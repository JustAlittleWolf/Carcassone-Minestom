package me.wolfii.game.tile

import me.wolfii.game.geometry.Direction
import me.wolfii.game.geometry.Field
import me.wolfii.game.geometry.Vec2I
import java.io.File
import me.wolfii.game.geometry.toCharField
import me.wolfii.game.geometry.map
import java.io.FileNotFoundException
import java.util.EnumMap

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
                    'm' -> Surface.MONASTERY
                    else -> throw error("Invalid character '$ch'")
                }
            }.also { require(it.width == Tile.SIZE && it.height == Tile.SIZE) }
        meeplePositions = charField.indices.filter { charField.getValue(it).isUpperCase() }
    }

    private val reachableFrom: Map<Vec2I, Set<Vec2I>> = HashMap<Vec2I, Set<Vec2I>>().also { reachable ->
        val candidates = ArrayDeque(surface.indices)
        while (candidates.isNotEmpty()) {
            val start = candidates.removeFirst()
            val match = surfaceAt(start.x, start.z)
            if (reachable.containsKey(start)) continue
            val reachables = HashSet<Vec2I>()
            val toProcess = ArrayDeque(listOf(start))
            while (toProcess.isNotEmpty()) {
                val current = toProcess.removeFirst()
                if (!surface.isInside(current)) continue
                if (reachables.contains(current)) continue
                if (surfaceAt(current.x, current.z) != match) continue
                reachables.add(current)
                for (direction in Direction.entries) toProcess.add(current + direction.vec)
            }
            reachables.forEach { reachable[it] = reachables }
        }
    }

    private val reachableNeighbourtilesFrom: Map<Vec2I, Map<Direction, Set<Vec2I>>> = HashMap<Vec2I, Map<Direction, Set<Vec2I>>>().also { from ->
        val candidates = ArrayDeque(surface.indices)
        while (candidates.isNotEmpty()) {
            val start = candidates.removeFirst()
            if (from.containsKey(start)) continue
            val reachableOutside = EnumMap<Direction, Set<Vec2I>>(Direction::class.java)
            val reachablesBorder = reachableFrom(start.x, start.z)
                .filter { it.x == 0 || it.x == Tile.MAX_INDEX || it.z == 0 || it.z == Tile.MAX_INDEX }
            for (direction in Direction.entries) {
                reachableOutside[direction] = reachablesBorder
                    .map { it + direction.vec }
                    .filter { !surface.isInside(it) }
                    .map { Vec2I(Math.floorMod(it.x, Tile.SIZE), Math.floorMod(it.z, Tile.SIZE)) }
                    .toSet()
            }
            reachableFrom(start.x, start.z).forEach { from[it] = reachableOutside }
        }
    }

    override fun surfaceAt(x: Int, z: Int) = surface.getValue(x, z)

    override fun connection(direction: Direction) = when (direction) {
        Direction.NORTH -> surfaceAt(Tile.MAX_INDEX / 2, 0)
        Direction.EAST -> surfaceAt(Tile.MAX_INDEX, Tile.MAX_INDEX / 2)
        Direction.SOUTH -> surfaceAt((Tile.MAX_INDEX) / 2, Tile.MAX_INDEX)
        Direction.WEST -> surfaceAt(0, Tile.MAX_INDEX / 2)
    }

    override fun placeableMeeples() = meeplePositions
    override fun reachableFrom(x: Int, z: Int) = reachableFrom.getValue(Vec2I(x, z))
    override fun reachableNeighbourtilesFrom(x: Int, z: Int) = reachableNeighbourtilesFrom.getValue(Vec2I(x, z))

    override fun toString(): String {
        return buildString {
            (0..Tile.MAX_INDEX).forEach { z ->
                (0..Tile.MAX_INDEX).forEach { x ->
                    append(surfaceAt(x, z).displayChar)
                }
                append('\n')
            }
        }
    }
}