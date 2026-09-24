package me.santio.minehututils.sticky

import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.launch
import me.santio.minehututils.bot
import me.santio.minehututils.commands.CommandLoader.logger
import me.santio.minehututils.coroutines.exceptionHandler
import me.santio.minehututils.factories.EmbedFactory
import me.santio.minehututils.scope
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * The stickied manager for handling stickied messages. By design, there can only be one stickied
 * message per channel.
 * @author ddbrother
 */
object StickyManager {

    data class StickyMessage(
        val channelId: String,
        var message: String,
        var lastMessageId: String? = null,
        var active: Boolean = false
    )

    private val stickyMessages = ConcurrentHashMap<String, StickyMessage>()

    /**
     * Start stickying a message
     * @param channelId The id of the channel to sticky
     * @param message The message to sticky
     */
    fun start(channelId: String, message: String) {
        val sticky = stickyMessages[channelId]
        if (sticky == null) {
            stickyMessages[channelId] = StickyMessage(
                channelId = channelId,
                message = message,
                active = true
            )
        } else {
            sticky.message = message
            sticky.active = true
        }
    }

    /**
     * Stop stickying a message
     * @param channelId The id of the channel to sticky
     */
    fun stop(channelId: String) {
        stickyMessages[channelId]?.active = false
    }

    /**
     * Set the sticky message for a channel
     * @param channelId The id of the channel to sticky
     * @param message The message to sticky
     */
    fun set(channelId: String, message: String) {
        val sticky = stickyMessages[channelId]
        if (sticky == null) {
            stickyMessages[channelId] = StickyMessage(
                channelId = channelId,
                message = message,
            )
        } else {
            sticky.message = message
        }
    }

    /**
     * Get the sticky message for a channel
     * @param channelId The id of the channel to sticky
     * @return The message to sticky
     */
    fun getMessage(channelId: String): String? {
        return stickyMessages[channelId]?.message
    }

    /**
     * Get whether a message is being stuck or not
     * @param channelId The id of the channel to sticky
     * @return Whether the message is being stuck or not
     */
    fun isActive(channelId: String): Boolean {
        return stickyMessages[channelId]?.active == true
    }

    /**
     * Get the embed for a stuck message in a channel.
     * @param channelId The id of the channel to sticky
     * @return The embed being stuck for the channel
     */
    fun getEmbed(channelId: String): MessageEmbed {
        if (getMessage(channelId)?.length!! > 256) {
            val embed = EmbedFactory.default(getMessage(channelId)!!) {
                it.setFooter("This is an automated sticky message.")
            }.build()
            return embed
        }
        val embed = EmbedFactory.default("") {
            it.setTitle(getMessage(channelId))
            it.setFooter("This is an automated sticky message.")
        }.build()
        return embed
    }

    /**
     * Refresh the stickied messages
     * @param force Whether to force refresh the stickied messages
     */
    fun refreshSticky(force: Boolean = false) {
        scope.launch(exceptionHandler) {
            stickyMessages.values.forEach { sticky ->

                if (!sticky.active) return@forEach
                val channel = bot.getGuildChannelById(sticky.channelId) as? MessageChannel ?: return@forEach

                if (!force) {
                    // A channel we can no longer read shouldn't stop the other stickies from refreshing
                    val lastMessage = runCatching { channel.history.retrievePast(1).await().firstOrNull() }
                        .getOrElse { return@forEach }
                    if (lastMessage?.id == sticky.lastMessageId) return@forEach
                }

                runCatching {
                    sticky.lastMessageId?.let { id ->
                        channel.deleteMessageById(id).await()
                    }
                }.onFailure { err ->
                    logger.error("Failed to delete the last sticky message", err)
                }

                val embed = runCatching {
                    channel.sendMessageEmbeds(getEmbed(channel.id)).await()
                }.getOrElse { err ->
                    logger.error("Failed to post sticky message", err)
                    null
                } ?: return@forEach

                sticky.lastMessageId = embed.id
            }
        }
    }
}
