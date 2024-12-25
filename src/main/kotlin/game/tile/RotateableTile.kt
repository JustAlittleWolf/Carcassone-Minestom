package me.wolfii.game.tile

import me.wolfii.game.geometry.Direction
import me.wolfii.game.geometry.Rotation
import me.wolfii.game.geometry.Vec2I

class RotateableTile(private val tile: Tile, private val rotation: Rotation = Rotation.NONE) : Tile {
    fun rotatedRight() = RotateableTile(tile, rotation.rotatedRight())

    override fun surfaceAt(x: Int, z: Int): Surface {
        val index = rotation.transform(Vec2I(7 - x, 7 - z)).plus(Vec2I(7, 7))
        return tile.surfaceAt(index.x, index.z)
    }

    override fun connection(direction: Direction): Surface {
        return tile.connection(direction.transformed(rotation.inverse()))
    }
}