package me.santio.minehututils.sticky

import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

object StickyListener : ListenerAdapter() {

    override fun onMessageDelete(event: MessageDeleteEvent) {
        StickyManager.onDelete(event.channel.id, event.messageId)
    }

    override fun onMessageBulkDelete(event: MessageBulkDeleteEvent) {
        event.messageIds.forEach { StickyManager.onDelete(event.channel.id, it) }
    }

}
