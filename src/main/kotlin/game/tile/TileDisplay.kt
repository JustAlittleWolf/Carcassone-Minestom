package me.wolfii.game.tile

import me.wolfii.game.Game
import net.bladehunt.kotstom.SchedulerManager
import net.bladehunt.kotstom.extension.editMeta
import net.bladehunt.kotstom.extension.plus
import net.bladehunt.kotstom.extension.times
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.max

class TileDisplay(tile: Tile, instance: Instance) {
    companion object {
        private const val PLACE_Y = Game.FIELD_Y + 1.0
        private const val DISPLAY_Y = 3.0
        private const val MIN_YAW = 25.0

        private const val INTERPOLATION_TICKS = 3
    }

    private var rotateableTile = RotateableTile(tile)
    private var isConsumed = false
    private var position: Point = Vec.ZERO
    private var isShown = false

    private val center = Entity(EntityType.BLOCK_DISPLAY).apply {
        editMeta<BlockDisplayMeta> {
            setBlockState(Block.AIR)
            isHasNoGravity = true
            posRotInterpolationDuration = INTERPOLATION_TICKS
        }
        setInstance(instance, Vec(8.5, DISPLAY_Y, 8.5))
    }
    private val entities = ArrayList<Entity>()

    fun moveFromPlayerFacing(player: Player) {
        if (isConsumed) return
        val direction = player.position.withPitch { max(it, MIN_YAW) }.direction()
        val source = player.position.withY { it + player.eyeHeight * Game.TURN_SCALE }
        val collision = source + direction * (abs((source.y - DISPLAY_Y) / direction.y))
        setPos(collision)
    }

    private fun setPos(pos: Point) {
        position = pos
        val centerX = pos.withX { it - 0.5 }.chunkX() * 16 + 8.5
        val centerZ = pos.withZ { it - 0.5 }.chunkZ() * 16 + 8.5
        center.teleport(center.position.withCoord(centerX, DISPLAY_Y, centerZ))
    }

    fun rotateRight() {
        if (isConsumed) return
        rotateableTile = rotateableTile.rotatedRight()
        entities.forEach { it.setView(it.position.yaw + 90, 0F) }
    }

    fun place(instance: Instance): PlacementResult {
        if (isConsumed) return PlacementResult(false)
        val future = CompletableFuture<Unit>()
        SchedulerManager.submitTask {
            center.teleport(center.position.withY { it - 0.3 })
            if (center.position.y <= PLACE_Y) {
                center.passengers.forEach { entity ->
                    val posX = center.position.chunkX() * 16 + 1
                    val posZ = center.position.chunkZ() * 16 + 1
                    (0..<15).forEach { z ->
                        (0..<15).forEach { x ->
                            instance.setBlock(posX + x, PLACE_Y.toInt(), posZ + z, rotateableTile.displayBlockAt(x, z))
                        }
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
        entities.forEach { entity ->
            entity.editMeta<BlockDisplayMeta> {
                if (colorValue != glowColorOverride) glowColorOverride = colorValue
            }
        }
    }

    fun position() = position
    fun tile() = rotateableTile
    fun show(instance: Instance) {
        if (isShown) return
        isShown = true

        var host = center
        (0..<15).forEach { z ->
            (0..<15).forEach { x ->
                Entity(EntityType.BLOCK_DISPLAY).apply {
                    editMeta<BlockDisplayMeta> {
                        isHasGlowingEffect = true
                        glowColorOverride = NamedTextColor.GREEN.value()
                        setBlockState(rotateableTile.displayBlockAt(x, z))
                        isHasNoGravity = true
                        posRotInterpolationDuration = INTERPOLATION_TICKS
                        translation = Vec(x - 7.5, 0.0, z - 7.5)
                    }
                    setInstance(instance)
                }.also {
                    entities.add(it)
                    host.addPassenger(it)
                    host = it
                }
            }
        }
    }

    fun remove() = entities.reversed().forEach { it.remove() }.also { center.remove() }

    data class PlacementResult(val wasPlaced: Boolean, val future: CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit))
}