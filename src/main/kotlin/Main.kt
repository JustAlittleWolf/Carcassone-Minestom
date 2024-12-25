package me.wolfii

import net.bladehunt.kotstom.GlobalEventHandler
import net.bladehunt.kotstom.dsl.listen
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.server.ServerListPingEvent
import net.minestom.server.extras.MojangAuth

private val lobby: Lobby by lazy { Lobby() }

fun main() {
    System.setProperty("minestom.entity-view-distance", "15")
    System.setProperty("minestom.chunk-view-distance", "16")
    val minecraftServer = MinecraftServer.init()
    MinecraftServer.setBrandName("Carcassone")
    GlobalEventHandler.listen<AsyncPlayerConfigurationEvent> { event ->
        event.spawningInstance = lobby.instance
        event.player.respawnPoint = Lobby.SPAWN
        event.player.gameMode = GameMode.ADVENTURE
    }
    GlobalEventHandler.listen<ServerListPingEvent> { event ->
        event.responseData.favicon = CustomResponseData.favicon
        event.responseData.description = CustomResponseData.description
    }
    MojangAuth.init()

    minecraftServer.start("0.0.0.0", 25565)
}