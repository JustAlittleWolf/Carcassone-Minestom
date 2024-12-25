package me.wolfii.game.tile

import me.wolfii.game.geometry.Direction
import net.minestom.server.instance.block.Block

sealed interface Tile {
    companion object {
        val CCCC = TileImpl("CCCC")
        val CCFC = TileImpl("CCFC")
        val CRFR = TileImpl("CRFR")
        val CRRF = TileImpl("CRRF")
    }

    /**
     * @param x in 0..<15
     * @param z in 0..<15
     */
    fun displayBlockAt(x: Int, z: Int): Block = surfaceAt(x, z).displayBlock

    /**
     * @param x in 0..<15
     * @param z in 0..<15
     */
    fun surfaceAt(x: Int, z: Int): Surface

    fun connection(direction: Direction): Surface
}

