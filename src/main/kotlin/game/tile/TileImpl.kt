package me.wolfii.game.tile

import me.wolfii.game.geometry.Direction
import me.wolfii.game.geometry.Field
import java.io.File
import me.wolfii.game.geometry.toCharField
import me.wolfii.game.geometry.map
import java.io.FileNotFoundException

class TileImpl(name: String) : Tile {
    private val surface: Field<Surface> = (this::class.java.classLoader.getResource("tiles${File.separator}$name.tile") ?: throw FileNotFoundException())
        .readText()
        .trim()
        .split("\n")
        .toCharField()
        .map { ch ->
            when (ch.lowercaseChar()) {
                'f' -> Surface.FIELD
                'c' -> Surface.CITY
                'r' -> Surface.ROAD
                else -> throw error("Invalid character '$ch'")
            }
        }.also { require(it.width == 15 && it.height == 15) }

    private val northConnection = surfaceAt(7, 0)
    private val eastConnection = surfaceAt(14, 7)
    private val southConnection = surfaceAt(7, 14)
    private val westConnection = surfaceAt(0, 7)

    override fun surfaceAt(x: Int, z: Int): Surface {
        return surface.getValue(x, z)
    }

    override fun connection(direction: Direction) = when (direction) {
        Direction.NORTH -> northConnection
        Direction.EAST -> eastConnection
        Direction.SOUTH -> southConnection
        Direction.WEST -> westConnection
    }
}