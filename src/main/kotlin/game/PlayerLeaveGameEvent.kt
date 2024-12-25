package me.wolfii.game

import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerInstanceEvent

class PlayerLeaveGameEvent(private val player: Player) : PlayerInstanceEvent {
    override fun getPlayer() = player
}