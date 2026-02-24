package dev.kidepcode.chunkyfast.job

import dev.kidepcode.chunkyfast.ChunkyFastPlugin
import dev.kidepcode.chunkyfast.area.Area
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

object JobStorage {

    private fun file(plugin: ChunkyFastPlugin): File {
        return File(plugin.dataFolder, "job.yml")
    }

    fun save(plugin: ChunkyFastPlugin, state: JobState) {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        val f = file(plugin)
        val y = YamlConfiguration()

        y.set("worldId", state.worldId.toString())
        y.set("starterId", state.starterId)
        y.set("startedAtMs", state.startedAtMs)
        y.set("done", state.done)
        y.set("skipped", state.skipped)
        y.set("errors", state.errors)

        val aSec = y.createSection("area")
        state.area.serialize(aSec)

        val cSec = y.createSection("cursor")
        state.cursorState.serialize(cSec)

        y.save(f)
    }

    fun load(plugin: ChunkyFastPlugin): JobState? {
        val f = file(plugin)
        if (!f.exists()) return null
        val y = YamlConfiguration.loadConfiguration(f)

        val worldId = runCatching { UUID.fromString(y.getString("worldId") ?: return null) }.getOrNull() ?: return null
        val starterId = y.getString("starterId")

        val startedAtMs = y.getLong("startedAtMs", System.currentTimeMillis())
        val done = y.getLong("done", 0L)
        val skipped = y.getLong("skipped", 0L)
        val errors = y.getLong("errors", 0L)

        val areaSec = y.getConfigurationSection("area") ?: return null
        val area = Area.deserialize(areaSec) ?: return null

        val cursorSec = y.getConfigurationSection("cursor") ?: return null
        val cursor = CursorState.deserialize(cursorSec)

        return JobState(
            worldId = worldId,
            starterId = starterId,
            startedAtMs = startedAtMs,
            done = done,
            skipped = skipped,
            errors = errors,
            area = area,
            cursorState = cursor
        )
    }

    fun clear(plugin: ChunkyFastPlugin) {
        val f = file(plugin)
        if (f.exists()) f.delete()
    }
}

data class JobState(
    val worldId: UUID,
    val starterId: String?,
    val startedAtMs: Long,
    val done: Long,
    val skipped: Long,
    val errors: Long,
    val area: Area,
    val cursorState: CursorState
)