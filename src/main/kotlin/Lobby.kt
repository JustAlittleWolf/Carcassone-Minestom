package me.wolfii

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.wolfii.game.Game
import net.bladehunt.kotstom.GlobalEventHandler
import net.bladehunt.kotstom.InstanceManager
import net.bladehunt.kotstom.dsl.instance.generator
import net.bladehunt.kotstom.dsl.instance.modify
import net.bladehunt.kotstom.dsl.item.item
import net.bladehunt.kotstom.dsl.item.itemName
import net.bladehunt.kotstom.dsl.listen
import net.bladehunt.kotstom.extension.adventure.asMini
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceUnregisterEvent
import net.minestom.server.event.inventory.InventoryClickEvent
import net.minestom.server.event.inventory.InventoryItemChangeEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerSwapItemEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.PlayerInventory
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.utils.chunk.ChunkSupplier
import java.util.concurrent.locks.ReentrantLock

class Lobby {
    companion object {
        val SPAWN = Pos(0.5, 1.0, 0.5)
        private val playItem = item(Material.GREEN_CONCRETE) { itemName = "<green>Play".asMini() }
        private val inventoryItems = mapOf(
            4 to playItem
        )
    }

    val instance = InstanceManager.createInstanceContainer().also {
        it.timeRate = 0
        it.generator {
            if (absoluteStart().chunkZ() !in -1..0 || absoluteStart().chunkX() !in -1..0) return@generator
            modify { fillHeight(-1, 0, Block.STONE) }
        }
        it.chunkSupplier = ChunkSupplier { a, b, c -> LightingChunk(a, b, c) }
        it.eventNode().registerLobbyEvents()
    }

    private var game = Game()
    private val gameLock = ReentrantLock()

    private fun EventNode<InstanceEvent>.registerLobbyEvents() {
        listen<PlayerSpawnEvent> { event ->
            event.player.inventory.clear()
            inventoryItems.forEach { (slot, item) -> event.player.inventory.setItemStack(slot, item) }
        }
        listen<ItemDropEvent> { event ->
            event.isCancelled = true
        }
        listen<PlayerSwapItemEvent> { event ->
            event.isCancelled = true
        }
        listen<InventoryClickEvent> { event ->
            event.player.inventory.cursorItem = ItemStack.AIR
        }
        listen<PlayerUseItemEvent> { event ->
            runBlocking {
                if (event.itemStack != playItem) return@runBlocking
                async {
                    gameLock.lock()
                    try {
                        if (game.hasStarted()) game = Game()
                        event.player.setInstance(game.instance, Game.SPAWN)
                    } finally {
                        gameLock.unlock()
                    }
                }
            }
        }
        EventNode.all("lobby").also { node ->
            node.listen<InventoryItemChangeEvent> { event ->
                if (event.inventory !is PlayerInventory) return@listen
                event.inventory.viewers.forEach { player ->
                    if (player.instance != instance) return@forEach
                    if (!inventoryItems.containsKey(event.slot) && event.newItem == ItemStack.AIR) return@listen
                    if (inventoryItems[event.slot] == event.newItem) return@listen
                    event.inventory.setItemStack(event.slot, event.previousItem)
                }
            }
            GlobalEventHandler.addChild(node)
            listen<InstanceUnregisterEvent> { GlobalEventHandler.removeChild(node) }
        }
        listen<PlayerMoveEvent> { event ->
            if (event.newPosition.y < 0) event.player.teleport(SPAWN)
        }
    }
}


