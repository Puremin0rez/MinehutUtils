package me.santio.minehututils.tags

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.santio.minehututils.coroutines.exceptionHandler
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message.MessageFlag
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.schedule

object TagListener: ListenerAdapter() {

    private val logger = LoggerFactory.getLogger(TagListener::class.java)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cooldownReaction = Emoji.fromUnicode("⌛")
    private val timer = Timer()
    private val recentlySent = ConcurrentHashMap.newKeySet<String>()

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (!event.isFromGuild) return
        val message = event.message
        val tag = TagManager.find(event.guild.id, message.contentRaw) ?: return

        // Without these the reply would fail, so there's nothing to do in this channel
        val channel = event.guildChannel
        val self = event.guild.selfMember
        if (!channel.canTalk() || !self.hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)) {
            logger.debug("Skipping tag {} in {}, missing permission to reply", tag.id, channel.id)
            return
        }

        val id = "${event.channel.id}-${tag.id}"
        if (!recentlySent.add(id)) {
            if (self.hasPermission(channel, Permission.MESSAGE_ADD_REACTION, Permission.MESSAGE_HISTORY)) {
                message.addReaction(cooldownReaction).queue()
            }
            return
        }

        runCatching {
            val silent = message.flags.contains(MessageFlag.NOTIFICATIONS_SUPPRESSED)
            tag.send(message, silent)
        }.onFailure { result ->
            logger.error("Failed to send tag message", result)
        }

        coroutineScope.launch(exceptionHandler) {
            TagManager.addUse(tag)
        }

        timer.schedule(10000) {
            recentlySent.remove(id)
        }
    }

}
