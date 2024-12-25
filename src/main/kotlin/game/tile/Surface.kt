package me.wolfii.game.tile

import net.minestom.server.instance.block.Block

enum class Surface(val displayBlock: Block, val displayChar: Char) {
    CITY(Block.BRICKS, 'C'),
    ROAD(Block.DIRT_PATH, 'R'),
    FIELD(Block.GRASS_BLOCK, '.'),
    WALL(Block.STONE_BRICKS, 'W')
}