package me.wolfii.game.tile

import me.wolfii.game.geometry.Direction
import me.wolfii.game.geometry.Field
import me.wolfii.game.geometry.Vec2I
import java.io.File
import me.wolfii.game.geometry.toCharField
import me.wolfii.game.geometry.map
import java.io.FileNotFoundException

class TileImpl(name: String) : Tile {
    private val surface: Field<Surface>
    private val meeplePositions: List<Vec2I>

    init {
        val charField = (this::class.java.classLoader.getResource("tiles${File.separator}$name.tile") ?: throw FileNotFoundException())
            .readText()
            .trim()
            .split("\n")
            .toCharField()
        surface = charField
            .map { ch ->
                when (ch.lowercaseChar()) {
                    'f' -> Surface.FIELD
                    'c' -> Surface.CITY
                    'r' -> Surface.ROAD
                    'w' -> Surface.WALL
                    else -> throw error("Invalid character '$ch'")
                }
            }.also { require(it.width == 15 && it.height == 15) }
        meeplePositions = charField.indices.filter { charField.getValue(it).isUpperCase() }
    }

    private val northConnection = surfaceAt(7, 0)
    private val eastConnection = surfaceAt(14, 7)
    private val southConnection = surfaceAt(7, 14)
    private val westConnection = surfaceAt(0, 7)

    override fun surfaceAt(x: Int, z: Int) = surface.getValue(x, z)

    override fun connection(direction: Direction) = when (direction) {
        Direction.NORTH -> northConnection
        Direction.EAST -> eastConnection
        Direction.SOUTH -> southConnection
        Direction.WEST -> westConnection
    }

    override fun placeableMeeples() = meeplePositions

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