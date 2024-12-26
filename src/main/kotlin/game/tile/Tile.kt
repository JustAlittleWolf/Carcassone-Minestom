package me.wolfii.game.tile

import me.wolfii.game.geometry.Direction
import me.wolfii.game.geometry.Vec2I
import net.minestom.server.instance.block.Block


sealed interface Tile {
    companion object {
        val CCCC = TileImpl("CCCC")
        val CCFC = TileImpl("CCFC")
        val CRFR = TileImpl("CRFR")
        val CRRF = TileImpl("CRRF")
        val FFFF_M = TileImpl("FFFF_M")
        val FFRF_M = TileImpl("FFRF_M")

        const val SIZE = 15
        const val MAX_INDEX = SIZE - 1

        init {
            require(SIZE % 2 == 1)
        }
    }

    /**
     * @param x in 0..<SIZE
     * @param z in 0..<SIZE
     */
    fun displayBlockAt(x: Int, z: Int): Block = surfaceAt(x, z).displayBlock

    /**
     * @param x in 0..<SIZE
     * @param z in 0..<SIZE
     */
    fun surfaceAt(x: Int, z: Int): Surface

    fun connection(direction: Direction): Surface

    fun placeableMeeples(): List<Vec2I>

    fun reachableFrom(x: Int, z: Int): Set<Vec2I>

    fun reachableNeighbourtilesFrom(x: Int, z: Int): Map<Direction, Set<Vec2I>>
}

