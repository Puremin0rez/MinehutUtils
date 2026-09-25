package me.santio.minehututils.commands.impl

import com.google.auto.service.AutoService
import dev.minn.jda.ktx.interactions.commands.Command
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import me.santio.minehututils.commands.SlashCommand
import me.santio.minehututils.coroutines.await
import me.santio.minehututils.ext.formatted
import me.santio.minehututils.factories.EmbedFactory
import me.santio.minehututils.minehut.Minehut
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import kotlin.math.ceil

@AutoService(SlashCommand::class)
class NetworkCommand : SlashCommand {

    override fun getData(): CommandData {
        return Command("network", "View statistics about Minehut") {
            setContexts(InteractionContextType.GUILD)
        }
    }

    /**
     * Replaces the public "thinking" message with an ephemeral error
     */
    private suspend fun fail(event: SlashCommandInteractionEvent, embed: EmbedBuilder) {
        runCatching { event.hook.deleteOriginal().await() }
        event.hook.sendMessageEmbeds(embed.build()).setEphemeral(true).queue()
    }

    /**
     * Runs an API request, removing the public "thinking" message if it throws so the
     * command manager can report the failure ephemerally
     */
    private suspend fun <T> request(event: SlashCommandInteractionEvent, block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            runCatching { event.hook.deleteOriginal().await() }
            throw e
        }
    }

    override suspend fun execute(event: SlashCommandInteractionEvent) {
        // The Minehut API can take longer than Discord's 3 second window to respond
        event.deferReply().await()

        supervisorScope {
            val network = async { Minehut.network() }

            val playerDist = request(event) { Minehut.players() } ?: run {
                network.cancel()
                fail(event, EmbedFactory.error("Failed to fetch player statistics", event.guild))
                return@supervisorScope
            }

            val status = request(event) { network.await() } ?: run {
                fail(event, EmbedFactory.error("Failed to fetch network statistics", event.guild))
                return@supervisorScope
            }

            event.hook.editOriginalEmbeds(
                EmbedFactory.default(
                    """
                | :bar_chart: **Network Stats**
                | 
                | **Servers**: ${status.serverCount}/${status.serverMax}
                | **Ram**: ${ceil((status.ramCount ?: 0) / 1000.0).toInt()}GB
                |
                | **Players**: ${status.playerCount?.formatted()}
                | → Java: ${playerDist.javaTotal?.formatted()} (Lobby: ${playerDist.javaLobby?.formatted()}, Servers: ${playerDist.javaPlayerServer?.formatted()})
                | → Bedrock: ${playerDist.bedrockTotal?.formatted()} (Lobby: ${playerDist.bedrockLobby?.formatted()}, Servers: ${playerDist.bedrockPlayerServer?.formatted()})
                |
                | *View player statistics at [Minehut Track](https://track.gamersafer.systems/)*
                """.trimMargin()
                ).build()
            ).queue()
        }
    }

}
