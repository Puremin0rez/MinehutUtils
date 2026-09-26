package me.santio.minehututils.resolvers

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.ansi.ColorLevel


/**
 * Resolves motds from MiniMessage format (Adventure API) to Plain Text
 */
@Suppress("unused")
object MOTDResolver {

    private val miniMessage = MiniMessage.miniMessage()
    private val ansi = ANSIComponentSerializer.builder()
        .colorLevel(ColorLevel.INDEXED_8)
        .build()

    private val lineBreak = Regex("\n|<(?i:br|newline)>")

    /**
     * Parses a motd in MiniMessage or legacy format. Each line, split at new lines and at `<br>` or `<newline>`
     * tags, is parsed on its own like Minehut's in-game server list does, so tags and gradients don't carry over.
     * @param motd The motd to parse
     * @return The parsed motd
     */
    fun toComponent(motd: String): Component {
        val oldFormat = LegacyComponentSerializer.legacySection().deserialize(motd)
        val text = PlainTextComponentSerializer.plainText().serialize(oldFormat)
        return Component.join(JoinConfiguration.newlines(), text.split(lineBreak).map { miniMessage.deserialize(it) })
    }

    /**
     * Converts MiniMessage format to Plain text
     * @param motd The motd to convert
     * @return The plain text motd
     */
    fun clean(motd: String): String = clean(toComponent(motd))

    /**
     * Converts a parsed motd to plain text
     * @param motd The parsed motd
     * @return The plain text motd
     */
    fun clean(motd: Component): String {
        return PlainTextComponentSerializer.plainText().serialize(motd)
            .trimIndent()
            .split("\n")
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
    }

    /**
     * Converts MiniMessage format to plain text with ANSI colors
     * @param motd The motd to convert
     * @return The ANSI formatted motd
     */
    fun toAnsi(motd: String): String {
        val miniMessage = toComponent(motd)
        return ansi.serialize(miniMessage)
            .trimIndent()
            .split("\n")
            .dropLastWhile { it.isBlank() }
            .joinToString("\n\u001B[0m")
    }

}
