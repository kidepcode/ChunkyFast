package dev.kidepcode.chunkyfast.command

import dev.kidepcode.chunkyfast.ChunkyFastPlugin
import dev.kidepcode.chunkyfast.area.Area
import dev.kidepcode.chunkyfast.msg.Messages
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.math.max

class ChunkyFastCommand(
    private val plugin: ChunkyFastPlugin
) : BasicCommand {

    override fun permission(): String = "chunkyfast.admin"

    override fun execute(commandSourceStack: CommandSourceStack, args: Array<String>) {
        val sender = commandSourceStack.sender
        val m = plugin.messages

        val a = normalizeArgs(args)
        if (a.isEmpty()) {
            m.sendUsage(sender)
            return
        }

        when (a[0].lowercase()) {
            "start" -> handleStart(sender, m, a)
            "pause" -> {
                if (!plugin.jobManager.pause(sender)) m.sendKey(sender, "jobNotRunning")
            }
            "resume" -> {
                if (!plugin.jobManager.resume(sender)) m.sendKey(sender, "jobNotRunning")
            }
            "stop" -> {
                if (!plugin.jobManager.stop(sender)) m.sendKey(sender, "jobNotRunning")
            }
            "status" -> {
                if (!plugin.jobManager.status(sender)) m.sendKey(sender, "jobNotRunning")
            }
            "reload" -> {
                plugin.reloadAll()
                m.sendKey(sender, "reloaded")
            }
            else -> m.sendUsage(sender)
        }
    }

    override fun suggest(commandSourceStack: CommandSourceStack, args: Array<String>): Collection<String> {
        val sender = commandSourceStack.sender
        if (sender is CommandSender && !sender.hasPermission(permission())) return emptyList()

        val raw = args.toList()
        val a = normalizeArgs(args)

        val last = if (raw.isEmpty()) "" else raw.last()
        val atNewToken = last.isEmpty()

        fun filterPrefix(options: Collection<String>, prefix: String): List<String> {
            if (prefix.isEmpty()) return options.toList()
            val p = prefix.lowercase()
            val out = ArrayList<String>(options.size)
            for (o in options) {
                if (o.lowercase().startsWith(p)) out.add(o)
            }
            return out
        }

        if (a.isEmpty()) {
            return listOf("start", "pause", "resume", "stop", "status", "reload")
        }

        val cmd = a[0].lowercase()
        if (a.size == 1 && !atNewToken) {
            return filterPrefix(listOf("start", "pause", "resume", "stop", "status", "reload"), a[0])
        }

        if (cmd != "start") return emptyList()

        return when (a.size) {
            1 -> listOf("start")
            2 -> {
                val worlds = Bukkit.getWorlds().map { it.name }
                val prefix = if (atNewToken) "" else a[1]
                filterPrefix(worlds, prefix)
            }
            3 -> {
                val prefix = if (atNewToken) "" else a[2]
                filterPrefix(listOf("circle", "square", "rect", "worldborder"), prefix)
            }
            else -> emptyList()
        }
    }

    private fun handleStart(sender: CommandSender, m: Messages, a: List<String>) {
        if (plugin.jobManager.isRunning()) {
            m.sendKey(sender, "jobAlreadyRunning")
            return
        }
        if (a.size < 3) {
            m.sendUsage(sender)
            return
        }

        val world = Bukkit.getWorld(a[1])
        if (world == null) {
            m.sendKey(sender, "worldNotFound", "world" to a[1])
            return
        }

        val shape = a[2].lowercase()
        val area = when (shape) {
            "circle" -> {
                if (a.size < 4) return m.sendUsage(sender)
                val radius = parseInt(m, sender, a[3]) ?: return
                val (cx, cz) = parseCenter(sender, world, a, 4)
                Area.circle(cx, cz, max(0, radius))
            }
            "square" -> {
                if (a.size < 4) return m.sendUsage(sender)
                val radius = parseInt(m, sender, a[3]) ?: return
                val (cx, cz) = parseCenter(sender, world, a, 4)
                Area.square(cx, cz, max(0, radius))
            }
            "rect" -> {
                if (a.size < 7) return m.sendUsage(sender)
                val minX = parseInt(m, sender, a[3]) ?: return
                val minZ = parseInt(m, sender, a[4]) ?: return
                val maxX = parseInt(m, sender, a[5]) ?: return
                val maxZ = parseInt(m, sender, a[6]) ?: return
                Area.rect(minX, minZ, maxX, maxZ)
            }
            "worldborder" -> Area.worldBorder(world)
            else -> {
                m.sendKey(sender, "invalidMode", "value" to a[2])
                return
            }
        }

        plugin.jobManager.start(sender, world, area)
    }

    private fun parseCenter(sender: CommandSender, world: org.bukkit.World, a: List<String>, idx: Int): Pair<Int, Int> {
        if (a.size >= idx + 2) {
            val cx = a[idx].toIntOrNull()
            val cz = a[idx + 1].toIntOrNull()
            if (cx != null && cz != null) return cx to cz
        }
        if (sender is Player && sender.world.uid == world.uid) {
            val c = sender.location.chunk
            return c.x to c.z
        }
        val s = world.spawnLocation
        return (s.blockX shr 4) to (s.blockZ shr 4)
    }

    private fun parseInt(m: Messages, sender: CommandSender, value: String): Int? {
        val v = value.toIntOrNull()
        if (v == null) m.sendKey(sender, "invalidNumber", "value" to value)
        return v
    }

    private fun normalizeArgs(args: Array<String>): List<String> {
        if (args.isEmpty()) return emptyList()
        val out = ArrayList<String>(args.size)
        for (s in args) {
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }
}