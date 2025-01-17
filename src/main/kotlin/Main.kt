package me.wolfii

import net.bladehunt.kotstom.GlobalEventHandler
import net.bladehunt.kotstom.dsl.listen
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.adventure.audience.Audiences
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.server.ServerListPingEvent
import net.minestom.server.extras.MojangAuth
import org.slf4j.LoggerFactory

private val lobby: Lobby by lazy { Lobby() }
private val LOGGER = LoggerFactory.getLogger("CarcassoneMinestom")

fun main() {
    System.setProperty("minestom.entity-view-distance", "15")
    System.setProperty("minestom.chunk-view-distance", "16")
    val minecraftServer = MinecraftServer.init()
    MinecraftServer.setBrandName("Carcassone")
    GlobalEventHandler.listen<AsyncPlayerConfigurationEvent> { event ->
        event.spawningInstance = lobby.instance
        event.player.respawnPoint = Lobby.SPAWN
        event.player.gameMode = GameMode.ADVENTURE
        Audiences.players().sendMessage(Component.text("${event.player.username} joined the game", NamedTextColor.YELLOW))
        LOGGER.info("${event.player.username} [${event.player.playerConnection.remoteAddress}] joined the game")
    }
    GlobalEventHandler.listen<ServerListPingEvent> { event ->
        event.responseData.favicon = CustomResponseData.favicon
        event.responseData.description = CustomResponseData.description
    }
    GlobalEventHandler.listen<PlayerDisconnectEvent> { event ->
        Audiences.players().sendMessage(Component.text("${event.player.username} left the game", NamedTextColor.YELLOW))
        LOGGER.info("${event.player.username} [${event.player.playerConnection.remoteAddress}] left the game")
    }
    MojangAuth.init()

    minecraftServer.start("0.0.0.0", 25565)
}