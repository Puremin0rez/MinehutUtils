package me.santio.minehututils.commands.impl

import com.google.auto.service.AutoService
import dev.minn.jda.ktx.interactions.commands.Command
import me.santio.minehututils.commands.SlashCommand
import me.santio.minehututils.coroutines.await
import me.santio.minehututils.ext.formatted
import me.santio.minehututils.ext.toTime
import me.santio.minehututils.factories.EmbedFactory
import me.santio.minehututils.minehut.Minehut
import me.santio.minehututils.minehut.Minehut.server
import me.santio.minehututils.resolvers.EmojiResolver
import me.santio.minehututils.resolvers.MOTDResolver
import me.santio.minehututils.utils.TextHelper.titlecase
import me.santio.sdk.minehut.models.ListedServer
import me.santio.sdk.minehut.models.Server
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.utils.FileUpload
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.round

@AutoService(SlashCommand::class)
class ServerCommand : SlashCommand {

    private fun planName(server: Server, listed: ListedServer?): String {
        val raw = server.serverPlan ?: return "Unknown"
        val active = server.activeServerPlan

        val name = when {
            raw.startsWith("CUSTOM") -> "Custom"
            active != null && active != raw -> active.removePrefix("YEARLY ")
            else -> return raw.replace("_", " ").titlecase()
        }

        val details = listOfNotNull(
            "monthly".takeIf { raw.startsWith("MONTHLY_") },
            "yearly".takeIf { raw.startsWith("YEARLY_") },
            listed?.staticInfo?.planRam?.let { "${it / 1024}GB" }
        )

        return if (details.isEmpty()) name else "$name (${details.joinToString(", ")})"
    }

    private fun favicon(server: Server): FileUpload? {
        val data = server.serverListFavicon?.substringAfter("base64,", "")?.takeIf { it.isNotEmpty() } ?: return null
        val bytes = runCatching { Base64.getDecoder().decode(data) }.getOrNull() ?: return null
        return FileUpload.fromData(runCatching { upscale(bytes) }.getOrDefault(bytes), "favicon.png")
    }

    private fun upscale(bytes: ByteArray): ByteArray {
        val image = ImageIO.read(bytes.inputStream()) ?: return bytes
        val scale = 160 / maxOf(image.width, image.height)
        if (scale <= 1) return bytes

        val scaled = BufferedImage(image.width * scale, image.height * scale, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until scaled.height) {
            for (x in 0 until scaled.width) {
                scaled.setRGB(x, y, image.getRGB(x / scale, y / scale))
            }
        }

        return ByteArrayOutputStream().also { ImageIO.write(scaled, "png", it) }.toByteArray()
    }

    suspend fun buildServerEmbed(guild: Guild, server: Server): Pair<EmbedBuilder, FileUpload?> {
        val motd = MOTDResolver.toAnsi(server.motd?.replace("`", "'") ?: "A Minehut production")
        val status = if (server.online == true) {
            "${EmojiResolver.find(guild, "yes", EmojiResolver.checkmark())!!.formatted} Online"
        } else {
            "${EmojiResolver.find(guild, "no", EmojiResolver.crossmark())!!.formatted} Offline"
        }

        val listed = Minehut.servers().firstOrNull {
            it.staticInfo?.id == server.id
        }

        val owner = listed?.author?.let { MarkdownSanitizer.escape(it) } ?: "Unknown"
        val rank = listed?.authorRank
            ?.takeIf { listed.author != null && it != "DEFAULT" }
            ?.let { Minehut.rankName(it) ?: it.replace("_", " ").titlecase() }

        val started = listed?.staticInfo?.serviceStartDate
        val perDay = server.creditsPerDay?.toDouble() ?: 0.0
        val price = when {
            perDay <= 0 || server.serverPlan == "EXTERNAL" -> null
            server.serverPlan?.startsWith("YEARLY_") == true -> "${round(perDay * 360).toInt().formatted()} credits/year"
            else -> "${round(perDay * 30).toInt().formatted()} credits/month"
        }

        val categoryNames = Minehut.categoryNames()
        val categories = server.categories.orEmpty()
            .map { categoryNames[it] ?: it.titlecase() }
            .joinToString(", ")
            .ifEmpty { "None" }

        val name = server.name ?: "Unknown"
        val icon = "${Minehut.ICON_URL}/${server.icon ?: "OAK_SIGN"}.png"
        val favicon = favicon(server)

        val embed = EmbedFactory.default(
            listOfNotNull(
                ":warning: **This server is currently suspended**".takeIf { server.suspended == true },
                "```ansi\n$motd```"
            ).joinToString("\n")
        )
            .setTitle(null)
            .setAuthor(
                name,
                "https://minehut.com/servers/${name.lowercase()}",
                icon
            )
            .setThumbnail(if (favicon != null) "attachment://favicon.png" else icon)
            .addField("📡 Status", status, true)
            .addField("📈 Players", server.playerCount?.formatted() ?: "0", true)
            .addField("👥 Total Joins", server.joins?.formatted() ?: "0", true)
            .addField(
                if (started != null) "🕒 Online Since" else "🕒 Last Online",
                (started ?: server.lastOnline)?.toTime() ?: "Unknown",
                true
            )
            .addField("📅 Created", server.creation?.let { "<t:${it / 1000}:D>" } ?: "Unknown", true)
            .addField("🏷️ Version", listed?.versionMin?.let { "$it+" } ?: "Unknown", true)
            .addField("👑 Owner", owner + (rank?.let { " ($it)" } ?: ""), true)
            .addField(
                "💎 Plan",
                planName(server, listed) + (price?.let { "\n$it" } ?: ""),
                true
            )
            .addField("⚙️ Server Type", server.serverVersionType?.titlecase() ?: "Unknown", true)
            .addField("📁 Categories", categories, false)
            .setFooter("Server ID: ${server.id ?: "Unknown"}")

        return embed to favicon
    }

    override fun getData(): CommandData {
        return Command("server", "Get information about a server") {
            setContexts(InteractionContextType.GUILD)
            addOption(OptionType.STRING, "server", "The server to get information about", true, true)
        }
    }

    override suspend fun autoComplete(event: CommandAutoCompleteInteractionEvent): List<Command.Choice> {
        val query = event.focusedOption.value
        return Minehut.cachedServers().asSequence()
            .mapNotNull { it.name }
            .filter { it.contains(query, ignoreCase = true) }
            .take(OptionData.MAX_CHOICES)
            .map { Command.Choice(it, it) }
            .toList()
    }

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        val serverId = event.getOption("server")?.asString ?: error("Server not provided")

        val guild = event.guild ?: return

        // The Minehut API can take longer than Discord's 3 second window to respond
        event.deferReply(true).await()

        val data = server(serverId) ?: run {
            event.hook.editOriginalEmbeds(EmbedFactory.error("Failed to find the server", guild).build()).queue()
            return
        }

        val (embed, favicon) = buildServerEmbed(guild, data)
        event.hook.editOriginalEmbeds(embed.build())
            .apply { favicon?.let { setFiles(it) } }
            .queue()
    }

}
