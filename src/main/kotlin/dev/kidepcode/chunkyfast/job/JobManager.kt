package dev.kidepcode.chunkyfast.job

import dev.kidepcode.chunkyfast.ChunkyFastPlugin
import dev.kidepcode.chunkyfast.area.Area
import dev.kidepcode.chunkyfast.config.Settings
import dev.kidepcode.chunkyfast.msg.Messages
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

class JobManager(
    private val plugin: ChunkyFastPlugin,
    @Volatile private var settings: Settings,
    @Volatile private var messages: Messages
) {
    @Volatile
    private var job: GenerationJob? = null

    @Volatile
    private var ticker: BukkitTask? = null

    fun update(newSettings: Settings, newMessages: Messages) {
        settings = newSettings
        messages = newMessages
        job?.update(newSettings, newMessages)
    }

    fun startTicker() {
        if (ticker != null) return
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            job?.tick()
        }, 1L, 1L)
    }

    fun isRunning(): Boolean = job != null

    fun start(starter: CommandSender, world: World, area: Area) {
        val starterId = (starter as? Player)?.uniqueId
        val j = GenerationJob(
            plugin = plugin,
            settings = settings,
            messages = messages,
            world = world,
            area = area,
            starterId = starterId,
            onTerminated = { terminated ->
                if (job === terminated) job = null
            }
        )
        job = j
        j.start()
    }

    fun pause(sender: CommandSender): Boolean {
        val j = job ?: return false
        j.pause(sender)
        return true
    }

    fun resume(sender: CommandSender): Boolean {
        val j = job ?: return false
        j.resume(sender)
        return true
    }

    fun stop(sender: CommandSender): Boolean {
        val j = job ?: return false
        j.stop(sender)
        job = null
        return true
    }

    fun status(sender: CommandSender): Boolean {
        val j = job ?: return false
        j.status(sender)
        return true
    }

    fun shutdown() {
        job?.stop(Bukkit.getConsoleSender())
        job = null
        ticker?.cancel()
        ticker = null
    }

    fun tryAutoResume() {
        val state = JobStorage.load(plugin) ?: return
        val world = Bukkit.getWorld(state.worldId) ?: return
        val area = state.area ?: return
        val starterId = state.starterId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val j = GenerationJob(
            plugin = plugin,
            settings = settings,
            messages = messages,
            world = world,
            area = area,
            starterId = starterId,
            onTerminated = { terminated ->
                if (job === terminated) job = null
            }
        )
        j.restore(state)
        job = j
        j.start(resumed = true)
    }
}