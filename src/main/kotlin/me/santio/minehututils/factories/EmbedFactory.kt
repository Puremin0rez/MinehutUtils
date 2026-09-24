package me.santio.minehututils.factories

import me.santio.minehututils.factories.EmbedFactory.exception
import me.santio.minehututils.resolvers.EmojiResolver
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed

/**
 * The factory for creating embed messages that the bot sends.
 * @see EmbedBuilder
 */
object EmbedFactory {

    /**
     * The base embed that all embeds are based off of.
     * @param block The block that is used to modify the embed.
     * @return The embed that is created.
     */
    /**
     * Shortens text to Discord's embed description limit, long user content (tags, stickies, logs)
     * would otherwise make the embed fail to build.
     */
    private fun fit(text: String): String {
        if (text.length <= MessageEmbed.DESCRIPTION_MAX_LENGTH) return text
        return text.take(MessageEmbed.DESCRIPTION_MAX_LENGTH - 1) + "…"
    }

    private fun baseEmbed(block: (EmbedBuilder) -> Unit): EmbedBuilder {
        val embed = EmbedBuilder()
        embed.setTitle(" ")
        embed.setColor(0x488aff)
        block(embed)
        return embed
    }

    /**
     * A default embed that is used for general messages.
     * @param text The text that is displayed in the embed.
     * @param block The block that is used to modify the embed.
     * @return The embed that is created.
     */
    fun default(text: String, block: (EmbedBuilder) -> Unit = {}): EmbedBuilder {
        return baseEmbed {
            it.setDescription(fit(text))
            block(it)
        }
    }

    /**
     * An embed that is used for success messages.
     * @param text The text that is displayed in the embed.
     * @param block The block that is used to modify the embed.
     * @return The embed that is created.
     */
    fun success(text: String, guild: Guild?, block: (EmbedBuilder) -> Unit = {}): EmbedBuilder {
        return baseEmbed {
            it.setDescription(fit("${EmojiResolver.yes(guild)?.formatted} $text"))
            it.setColor(0x6efa61)
            block(it)
        }
    }

    /**
     * An embed that is used for warning messages.
     * @param text The text that is displayed in the embed.
     * @param block The block that is used to modify the embed.
     * @return The embed that is created.
     */
    fun warning(text: String, block: (EmbedBuilder) -> Unit = {}): EmbedBuilder {
        return baseEmbed {
            it.setDescription(fit("${EmojiResolver.warning().formatted} $text"))
            it.setColor(0xfaba61)
            block(it)
        }
    }

    /**
     * An embed that is used for error messages.
     * @param text The text that is displayed in the embed.
     * @param guild The guild to check for the emote, else null to use discord default
     * @param block The block that is used to modify the embed.
     * @return The embed that is created.
     */
    fun error(text: String, guild: Guild?, block: (EmbedBuilder) -> Unit = {}): EmbedBuilder {
        return baseEmbed {
            it.setDescription(fit("${EmojiResolver.no(guild)?.formatted} $text"))
            it.setColor(0xff6961)
            block(it)
        }
    }

    /**
     * An embed that is used for error messages.
     * @param text The text that is displayed in the embed.
     * @param guild The guild to check for the emote, else null to use discord default
     * @param exception The exception that was thrown.
     * @param block The block that is used to modify the embed.
     * @return The embed that is created.
     */
    fun exception(text: String, guild: Guild?, exception: Throwable, block: (EmbedBuilder) -> Unit = {}): EmbedBuilder {
        return baseEmbed {
            it.setDescription(fit(
                """
            | ${EmojiResolver.no(guild)?.formatted} $text
            | ```diff
            | - ${exception::class.java.simpleName}: ${exception.message}```
            """.trimMargin()
            ))
            it.setColor(0xff6961)
            block(it)
        }
    }

}
