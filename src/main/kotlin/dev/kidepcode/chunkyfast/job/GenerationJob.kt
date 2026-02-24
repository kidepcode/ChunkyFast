package dev.kidepcode.chunkyfast.job

import dev.kidepcode.chunkyfast.ChunkyFastPlugin
import dev.kidepcode.chunkyfast.area.Area
import dev.kidepcode.chunkyfast.area.ChunkCursor
import dev.kidepcode.chunkyfast.config.Settings
import dev.kidepcode.chunkyfast.config.UnloadMode
import dev.kidepcode.chunkyfast.limiter.MsptLimiter
import dev.kidepcode.chunkyfast.msg.Messages
import dev.kidepcode.chunkyfast.skip.RegionHeaderCache
import dev.kidepcode.chunkyfast.skip.SkipMode
import dev.kidepcode.chunkyfast.util.ChunkKey
import dev.kidepcode.chunkyfast.util.Format
import dev.kidepcode.chunkyfast.util.LongQueue
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

class GenerationJob(
    private val plugin: ChunkyFastPlugin,
    @Volatile private var settings: Settings,
    @Volatile private var messages: Messages,
    private val world: World,
    private val area: Area,
    private val starterId: UUID?,
    private val onTerminated: (GenerationJob) -> Unit
) {
    private var inFlight: Int = 0
    private var manualPaused: Boolean = false
    private var stopped: Boolean = false

    private var cursor: ChunkCursor = area.createCursor(null)
    private val total: Long = area.totalChunks()
    private var done: Long = 0L
    private var skipped: Long = 0L
    private var errors: Long = 0L

    private var startedAtMs: Long = System.currentTimeMillis()
    private var lastRateMs: Long = startedAtMs
    private var lastRateDone: Long = 0L
    private var rate: Double = 0.0

    private var lastAutosaveMs: Long = startedAtMs
    private var lastLogMs: Long = startedAtMs

    private var lastFactor: Double = 1.0
    private var guardPaused: Boolean = false
    private var effectiveMaxConcurrent: Int = settings.baseLimits.maxConcurrent
    private var effectiveDispatchPerTick: Int = settings.baseLimits.dispatchPerTick

    private var regionCache: RegionHeaderCache? = buildRegionCache(settings)
    private val limiter = MsptLimiter(settings.limiter)

    private val completionQueue = LongQueue(max(64, settings.baseLimits.maxConcurrent shl 1))
    private val unloadQueue = LongQueue(max(64, settings.baseLimits.maxConcurrent shl 1))

    private val callbackPool = ArrayDeque<ChunkCallback>(max(32, settings.baseLimits.maxConcurrent shl 1))

    fun update(newSettings: Settings, newMessages: Messages) {
        settings = newSettings
        messages = newMessages
        regionCache = buildRegionCache(newSettings)
        limiter.update(newSettings.limiter)

        val target = max(32, newSettings.baseLimits.maxConcurrent shl 1)
        if (callbackPool.size < target) {
            repeat(target - callbackPool.size) { callbackPool.addLast(ChunkCallback(this)) }
        }
    }

    fun start(resumed: Boolean = false) {
        startedAtMs = System.currentTimeMillis()
        lastRateMs = startedAtMs
        lastRateDone = done
        lastAutosaveMs = startedAtMs
        lastLogMs = startedAtMs

        val sender = resolveStarter() ?: Bukkit.getConsoleSender()
        if (!resumed) {
            messages.sendKey(sender, "started",
                "world" to world.name,
                "shape" to area.type,
                "total" to total.toString()
            )
        } else {
            messages.sendKey(sender, "autoResumed",
                "world" to world.name,
                "shape" to area.type
            )
        }
        saveState()
    }

    fun restore(state: JobState) {
        done = max(0L, state.done)
        skipped = max(0L, state.skipped)
        errors = max(0L, state.errors)
        startedAtMs = state.startedAtMs
        cursor = area.createCursor(state.cursorState)
    }

    fun pause(sender: CommandSender) {
        manualPaused = true
        messages.sendKey(sender, "paused")
        saveState()
    }

    fun resume(sender: CommandSender) {
        manualPaused = false
        messages.sendKey(sender, "resumed")
        saveState()
    }

    fun stop(sender: CommandSender) {
        stopped = true
        manualPaused = true
        messages.sendKey(sender, "stopped")
        saveState()
        onTerminated(this)
    }

    fun status(sender: CommandSender) {
        val percent = if (total <= 0) 100.0 else (done.toDouble() * 100.0 / total.toDouble()).coerceIn(0.0, 100.0)
        val eta = if (rate <= 0.0) "—" else Format.formatEtaSeconds(((total - done).toDouble() / rate).toLong())
        val r = if (rate <= 0.0) "—" else String.format("%.1f", rate)
        val mspt = String.format("%.2f", Bukkit.getAverageTickTime())
        val q = completionQueue.size() + unloadQueue.size()

        messages.sendKey(
            sender,
            "status",
            "world" to world.name,
            "shape" to area.type,
            "done" to done.toString(),
            "total" to total.toString(),
            "percent" to String.format("%.2f", percent),
            "mspt" to mspt,
            "factor" to String.format("%.2f", lastFactor),
            "inflight" to inFlight.toString(),
            "q" to q.toString(),
            "rate" to r,
            "eta" to eta,
            "skipped" to skipped.toString(),
            "errors" to errors.toString()
        )
    }

    fun tick() {
        if (stopped) return

        val now = System.currentTimeMillis()

        updateLimiter()
        pumpCompletions()
        pumpUnload()

        if (!isPaused()) {
            dispatchMore()
        }

        updateRate(now)

        if (settings.autosaveSeconds > 0) {
            val period = settings.autosaveSeconds * 1000L
            if (now - lastAutosaveMs >= period) {
                lastAutosaveMs = now
                saveState()
            }
        }

        if (settings.logProgressSeconds > 0) {
            val period = settings.logProgressSeconds * 1000L
            if (now - lastLogMs >= period) {
                lastLogMs = now
                val mspt = String.format("%.2f", Bukkit.getAverageTickTime())
                Bukkit.getConsoleSender().sendMessage(
                    messages.deserialize(
                        "<gray>${world.name}</gray> <white>${area.type}</white> <gray>${done}/${total}</gray> <gray>inFlight=$inFlight</gray> <gray>q=${completionQueue.size() + unloadQueue.size()}</gray> <gray>mspt=$mspt</gray> <gray>factor=${String.format("%.2f", lastFactor)}</gray>",
                        emptyArray()
                    )
                )
            }
        }

        if (cursor.isFinished() && inFlight == 0 && completionQueue.isEmpty() && unloadQueue.isEmpty()) {
            finish()
        }
    }

    private fun updateLimiter() {
        val mspt = Bukkit.getAverageTickTime()
        val res = limiter.tick(mspt)
        guardPaused = res.paused
        lastFactor = res.factor

        effectiveMaxConcurrent = max(1, (settings.baseLimits.maxConcurrent * lastFactor).toInt())
        effectiveDispatchPerTick = max(1, (settings.baseLimits.dispatchPerTick * lastFactor).toInt())
    }

    private fun isPaused(): Boolean = manualPaused || guardPaused

    private fun updateRate(now: Long) {
        val dt = now - lastRateMs
        if (dt < 1000L) return
        val dd = done - lastRateDone
        rate = (dd.toDouble() * 1000.0) / dt.toDouble()
        lastRateMs = now
        lastRateDone = done
    }

    private fun dispatchMore() {
        val available = effectiveMaxConcurrent - inFlight
        if (available <= 0) return

        val toDispatch = min(available, effectiveDispatchPerTick)
        var dispatched = 0

        val urgent = settings.urgentChunkRequests
        val cache = regionCache
        val skipMode = settings.skipMode

        while (dispatched < toDispatch) {
            val key = cursor.nextKey()
            if (key == ChunkCursor.END) break

            val cx = ChunkKey.x(key)
            val cz = ChunkKey.z(key)

            if (skipMode != SkipMode.NONE && cache != null && cache.hasChunk(cx, cz)) {
                done++
                skipped++
                continue
            }

            inFlight++
            dispatched++

            val cb = acquireCallback()
            cb.init(key, cx, cz)
            world.getChunkAtAsync(cx, cz, true, urgent, cb)
        }
    }

    private fun pumpCompletions() {
        val cap = settings.baseLimits.completePerTick
        if (cap <= 0) return

        var n = 0
        val unloadMode = settings.unloadMode

        while (n < cap) {
            val key = completionQueue.pollOrNull() ?: break
            val cx = ChunkKey.x(key)
            val cz = ChunkKey.z(key)

            try {
                when (unloadMode) {
                    UnloadMode.NONE -> {}
                    UnloadMode.REQUEST -> unloadQueue.add(key)
                    UnloadMode.IMMEDIATE -> world.unloadChunk(cx, cz, true)
                }
            } catch (_: Throwable) {
                errors++
            } finally {
                done++
            }
            n++
        }
    }

    private fun pumpUnload() {
        if (settings.unloadMode != UnloadMode.REQUEST) return
        val cap = settings.baseLimits.unloadPerTick
        if (cap <= 0) return

        var n = 0
        while (n < cap) {
            val key = unloadQueue.pollOrNull() ?: break
            val cx = ChunkKey.x(key)
            val cz = ChunkKey.z(key)
            world.unloadChunkRequest(cx, cz)
            n++
        }
    }

    private fun finish() {
        stopped = true
        manualPaused = true
        saveState(clear = true)

        val time = Format.formatDurationMs(System.currentTimeMillis() - startedAtMs)
        val sender = resolveStarter() ?: Bukkit.getConsoleSender()
        messages.sendKey(
            sender,
            "finished",
            "done" to done.toString(),
            "total" to total.toString(),
            "skipped" to skipped.toString(),
            "errors" to errors.toString(),
            "time" to time
        )
        onTerminated(this)
    }

    private fun resolveStarter(): CommandSender? {
        val id = starterId ?: return null
        val p = Bukkit.getPlayer(id) ?: return null
        return if (p.isOnline) p else null
    }

    private fun saveState(clear: Boolean = false) {
        if (clear) {
            JobStorage.clear(plugin)
            return
        }
        JobStorage.save(
            plugin,
            JobState(
                worldId = world.uid,
                starterId = starterId?.toString(),
                startedAtMs = startedAtMs,
                done = done,
                skipped = skipped,
                errors = errors,
                area = area,
                cursorState = cursor.state()
            )
        )
    }

    private fun buildRegionCache(s: Settings): RegionHeaderCache? {
        if (s.skipMode == SkipMode.NONE) return null
        val size = s.regionCacheSize
        if (size <= 0) return null
        val regionDir = java.io.File(world.worldFolder, "region")
        return RegionHeaderCache(regionDir, size)
    }

    private fun acquireCallback(): ChunkCallback {
        return if (callbackPool.isEmpty()) ChunkCallback(this) else callbackPool.removeLast()
    }

    private fun releaseCallback(cb: ChunkCallback) {
        cb.reset()
        callbackPool.addLast(cb)
    }

    private class ChunkCallback(
        private val job: GenerationJob
    ) : Consumer<org.bukkit.Chunk> {

        var key: Long = 0L
        var cx: Int = 0
        var cz: Int = 0

        fun init(key: Long, cx: Int, cz: Int) {
            this.key = key
            this.cx = cx
            this.cz = cz
        }

        fun reset() {
            key = 0L
            cx = 0
            cz = 0
        }

        override fun accept(chunk: org.bukkit.Chunk) {
            try {
                job.completionQueue.add(key)
            } finally {
                job.inFlight--
                job.releaseCallback(this)
            }
        }
    }
}