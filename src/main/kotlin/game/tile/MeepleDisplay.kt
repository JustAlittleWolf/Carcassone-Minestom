package me.wolfii.game.tile

import me.wolfii.game.Game
import me.wolfii.game.PlayerColor
import me.wolfii.game.geometry.Vec2I
import net.bladehunt.kotstom.extension.editMeta
import net.bladehunt.kotstom.extension.plus
import net.bladehunt.kotstom.extension.times
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import kotlin.math.abs
import kotlin.math.max

class MeepleDisplay(pos: Point, playerColor: PlayerColor, instance: Instance, meeplePlacementPositions: List<Vec2I>) {
    companion object {
        private const val PLACE_Y = Game.FIELD_Y + 2.0
        private const val MIN_YAW = 45.0
    }

    private val meepleCandidates: List<Pair<Entity, Vec2I>>
    private val displayBlock = Block.fromNamespaceId("minecraft:${playerColor.namespaceIDColor}_stained_glass") ?: throw IllegalStateException()
    private var isConsumed = false
    private var selectedMeeple: Pair<Entity, Vec2I>? = null

    init {
        val cornerX = pos.withX { it - 0.5 }.chunkX() * 16 + 1.0
        val cornerZ = pos.withZ { it - 0.5 }.chunkZ() * 16 + 1.0
        meepleCandidates = meeplePlacementPositions.map { offset ->
            Entity(EntityType.BLOCK_DISPLAY).apply {
                editMeta<BlockDisplayMeta> {
                    setBlockState(Block.AIR)
                    isHasNoGravity = true
                    glowColorOverride = playerColor.textColor.value()
                    scale = Vec(1.5, 2.9, 1.5)
                    translation = Vec(-0.25, 0.0, -0.25)
                }
                setInstance(instance, Pos(cornerX + offset.x, PLACE_Y, cornerZ + offset.z))
            } to offset
        }
    }

    fun selectFromPlayerFacing(player: Player) {
        if (isConsumed) return
        val direction = player.position.withPitch { max(it, MIN_YAW) }.direction()
        val source = player.position.withY { it + player.eyeHeight * Game.TURN_SCALE }
        val collision = source + direction * (abs((source.y - PLACE_Y) / direction.y))
        meepleCandidates.forEach { if (it.first.isGlowing) it.first.isGlowing = false }
        meepleCandidates.minByOrNull { it.first.getDistanceSquared(collision) }?.let {
            it.first.isGlowing = true
            selectedMeeple = it
        }
    }

    fun hasValidPosition() = meepleCandidates.isNotEmpty()
    fun show() = meepleCandidates.forEach { it.first.editMeta<BlockDisplayMeta> { setBlockState(displayBlock) } }
    fun remove() = meepleCandidates.forEach { it.first.remove() }
    fun selectedMeeple() = selectedMeeple ?: throw IllegalStateException()
}