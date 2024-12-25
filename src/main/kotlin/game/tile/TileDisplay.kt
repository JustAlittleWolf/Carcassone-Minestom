package me.wolfii.game.tile

import me.wolfii.game.Game
import net.bladehunt.kotstom.SchedulerManager
import net.bladehunt.kotstom.extension.editMeta
import net.bladehunt.kotstom.extension.plus
import net.bladehunt.kotstom.extension.times
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.max

class TileDisplay(tile: Tile, game: Game) {
    companion object {
        private const val PLACE_Y = Game.FIELD_Y + 1.0
        private const val DISPLAY_Y = 3.0
        private const val MIN_YAW = 25.0
    }

    private var rotateableTile = RotateableTile(tile)
    private var isConsumed = false
    private var position: Point = Vec.ZERO

    private val entities: List<MutableList<Entity>> = (0..<15).map { z ->
        (0..<15).mapTo(ArrayList()) { x ->
            Entity(EntityType.BLOCK_DISPLAY).also { entity ->
                entity.isGlowing = true
                entity.isAutoViewable = false
                entity.editMeta<BlockDisplayMeta> {
                    isHasGlowingEffect = true
                    glowColorOverride = NamedTextColor.GREEN.value()
                    setBlockState(tile.displayBlockAt(x, z))
                    isHasNoGravity = true
                    posRotInterpolationDuration = 3
                }
                entity.setInstance(game.instance)
            }
        }
    }

    fun moveFromPlayerFacing(player: Player) {
        if (isConsumed) return
        val direction = player.position.withPitch { max(it, MIN_YAW) }.direction()
        val source = player.position.withY { it + player.eyeHeight * Game.TURN_SCALE }
        val collision = source + direction * (abs((source.y - DISPLAY_Y) / direction.y))
        setPos(collision)
    }

    private fun setPos(pos: Point) {
        position = pos
        val startX = pos.withX { it - 0.5 }.chunkX() * 16 + 1.0
        val startZ = pos.withZ { it - 0.5 }.chunkZ() * 16 + 1.0
        for (z in 0..<15) {
            for (x in 0..<15) {
                entities[z][x].teleport(Pos(startX + x, DISPLAY_Y, startZ + z))
            }
        }
    }

    fun rotateRight() {
        if (isConsumed) return
        rotateableTile = rotateableTile.rotatedRight()
        val newEntities = MutableList(15) { MutableList<Entity?>(15) { null } }
        entities.forEachIndexed { z, list ->
            list.forEachIndexed { x, entity ->
                val relX = x - 7
                val relZ = z - 7
                newEntities[7 + relX][7 - relZ] = entity
            }
        }
        for (z in 0..<15) {
            for (x in 0..<15) {
                entities[z][x] = newEntities[z][x] ?: throw IllegalStateException()
            }
        }
        setPos(entities[0][0].position)
    }

    fun place(instance: Instance): PlacementResult {
        if (isConsumed) return PlacementResult(false)
        val future = CompletableFuture<Unit>()
        SchedulerManager.submitTask {
            entities.forEach { it.forEach { it.teleport(it.position.withY { it - 0.3 }) } }
            if (entities.any { it.any { it.position.y <= PLACE_Y } }) {
                entities.forEach {
                    it.forEach { entity ->
                        instance.setBlock(entity.position.withY(PLACE_Y), (entity.entityMeta as BlockDisplayMeta).blockStateId)
                    }
                }
                remove()
                future.complete(Unit)
                return@submitTask TaskSchedule.stop()
            } else TaskSchedule.nextTick()
        }
        isConsumed = true
        return PlacementResult(true, future)
    }

    fun setColor(color: TextColor) {
        val colorValue = color.value()
        entities.forEach {
            it.forEach { entity ->
                entity.editMeta<BlockDisplayMeta> {
                    if (colorValue != glowColorOverride) glowColorOverride = colorValue
                }
            }
        }
    }

    fun isPlaced() = isConsumed
    fun position() = position
    fun tile() = rotateableTile
    fun show(players: Set<Player>) = players.forEach { player -> entities.forEach { it.forEach { it.addViewer(player) } } }
    fun remove() = entities.forEach { it.forEach { it.remove() } }

    data class PlacementResult(val wasPlaced: Boolean, val future: CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit))
}