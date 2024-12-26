package me.wolfii.game.tile

import me.wolfii.game.geometry.Direction
import me.wolfii.game.geometry.Rotation
import me.wolfii.game.geometry.Vec2I
import java.util.EnumMap

class RotateableTile private constructor(private val tile: Tile, private val rotation: Rotation = Rotation.NONE) : Tile {
    companion object {
        private val instances: MutableMap<Pair<Tile, Rotation>, RotateableTile> = HashMap<Pair<Tile, Rotation>, RotateableTile>()

        fun from(tile: Tile, rotation: Rotation = Rotation.NONE): RotateableTile {
            return instances[Pair(tile, rotation)] ?: run {
                val newRotateableTile = RotateableTile(tile, rotation)
                instances[Pair(tile, rotation)] = newRotateableTile
                return@run newRotateableTile
            }
        }
    }

    private val rotatedMeeplePositions: List<Vec2I> by lazy { tile.placeableMeeples().map { transform(it.x, it.z, true) } }
    private val rotatedReachableFrom: Map<Vec2I, Set<Vec2I>> by lazy {
        HashMap<Vec2I, Set<Vec2I>>().also { reachable ->
            (0..Tile.MAX_INDEX).forEach { z ->
                (0..Tile.MAX_INDEX).forEach { x ->
                    val pos = Vec2I(x, z)
                    val transformed = transform(pos.x, pos.z)
                    if (reachable.containsKey(pos)) return@forEach
                    val reachables = tile.reachableFrom(transformed.x, transformed.z).mapTo(HashSet()) { transform(it.x, it.z, true) }
                    reachables.forEach { reachable[it] = reachables }
                }
            }
        }
    }
    private val rotatedReachableNeighbourtilesFrom: Map<Vec2I, Map<Direction, Set<Vec2I>>> by lazy {
        HashMap<Vec2I, Map<Direction, Set<Vec2I>>>().also { from ->
            (0..Tile.MAX_INDEX).forEach { z ->
                (0..Tile.MAX_INDEX).forEach { x ->
                    val pos = Vec2I(x, z)
                    val transformed = transform(pos.x, pos.z)
                    if (from.containsKey(pos)) return@forEach
                    val reachablesNeighbours = EnumMap<Direction, Set<Vec2I>>(Direction::class.java)
                    tile.reachableNeighbourtilesFrom(transformed.x, transformed.z).forEach { reachableNeighbourtiles ->
                        val localDirection = reachableNeighbourtiles.key.transformed(rotation)
                        reachablesNeighbours[localDirection] = reachableNeighbourtiles.value.mapTo(HashSet()) {
                            val inside = it - reachableNeighbourtiles.key.vec
                            val transformed = transform(inside.x, inside.z, true) + localDirection.vec
                            return@mapTo Vec2I(Math.floorMod(transformed.x, Tile.SIZE), Math.floorMod(transformed.z, Tile.SIZE))
                        }
                    }
                    reachableFrom(pos.x, pos.z).forEach { from[it] = reachablesNeighbours }
                }
            }
        }
    }

    fun rotatedRight() = from(tile, rotation.rotatedRight())

    override fun surfaceAt(x: Int, z: Int): Surface {
        val (transformedX, transformedZ) = transform(x, z)
        return tile.surfaceAt(transformedX, transformedZ)
    }

    override fun connection(direction: Direction) = tile.connection(direction.transformed(rotation.inverse()))
    override fun placeableMeeples() = rotatedMeeplePositions
    override fun reachableFrom(x: Int, z: Int) = rotatedReachableFrom.getValue(Vec2I(x, z))
    override fun reachableNeighbourtilesFrom(x: Int, z: Int) = rotatedReachableNeighbourtilesFrom.getValue(Vec2I(x, z))

    private fun transform(x: Int, z: Int, reversed: Boolean = false) = (if (reversed) rotation else rotation.inverse())
        .transform(Vec2I(x - Tile.MAX_INDEX / 2, z - Tile.MAX_INDEX / 2)).plus(Vec2I(Tile.MAX_INDEX / 2, Tile.MAX_INDEX / 2))

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
