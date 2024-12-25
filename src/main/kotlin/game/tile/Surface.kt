package me.wolfii.game.tile

import net.minestom.server.instance.block.Block

enum class Surface(val displayBlock: Block) {
    CITY(Block.BRICKS),
    ROAD(Block.DIRT_PATH),
    FIELD(Block.GRASS_BLOCK)
}