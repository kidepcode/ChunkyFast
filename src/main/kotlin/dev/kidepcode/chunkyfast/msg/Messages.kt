package dev.kidepcode.chunkyfast.msg

import dev.kidepcode.chunkyfast.config.MessageBundle
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender

class Messages(
    private val bundle: MessageBundle
) {
    private val mm = MiniMessage.miniMessage()
    private val prefix = bundle.prefix

    fun send(sender: CommandSender, mini: String, vararg placeholders: Pair<String, String>) {
        sender.sendMessage(deserialize(prefix + mini, placeholders))
    }

    fun sendKey(sender: CommandSender, key: String, vararg placeholders: Pair<String, String>) {
        val v = bundle.values[key] ?: return
        when (v) {
            is String -> sender.sendMessage(deserialize(prefix + v, placeholders))
            is List<*> -> {
                for (line in v) {
                    if (line is String) sender.sendMessage(deserialize(prefix + line, placeholders))
                }
            }
        }
    }

    fun sendUsage(sender: CommandSender) {
        sendKey(sender, "usage")
    }

    fun deserialize(mini: String, placeholders: Array<out Pair<String, String>>): Component {
        if (placeholders.isEmpty()) return mm.deserialize(mini)
        val builder = TagResolver.builder()
        for (p in placeholders) builder.resolver(Placeholder.parsed(p.first, p.second))
        return mm.deserialize(mini, builder.build())
    }
}