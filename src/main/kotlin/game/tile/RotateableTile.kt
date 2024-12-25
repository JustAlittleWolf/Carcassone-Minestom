package me.wolfii.game.tile

import me.wolfii.game.geometry.Direction
import me.wolfii.game.geometry.Rotation
import me.wolfii.game.geometry.Vec2I

class RotateableTile(private val tile: Tile, private val rotation: Rotation = Rotation.NONE) : Tile {
    val rotatedMeeplePositions: List<Vec2I> by lazy { tile.placeableMeeples().map { transform(it.x, it.z) } }

    fun rotatedRight() = RotateableTile(tile, rotation.rotatedRight())

    override fun surfaceAt(x: Int, z: Int): Surface {
        val (transformedX, transformedZ) = transform(x, z)
        return tile.surfaceAt(transformedX, transformedZ)
    }

    override fun connection(direction: Direction) = tile.connection(direction.transformed(rotation.inverse()))
    override fun placeableMeeples() = rotatedMeeplePositions

    private fun transform(x: Int, z: Int) = rotation.transform(Vec2I(x - 7, z - 7)).plus(Vec2I(7, 7))

    override fun toString(): String {
        return buildString {
            (0..<15).forEach { z ->
                (0..<15).forEach { x ->
                    append(surfaceAt(x, z).displayChar)
                }
                append('\n')
            }
        }
    }
}
