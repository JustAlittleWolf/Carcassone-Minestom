package me.wolfii.game.geometry

import me.wolfii.game.geometry.Rotation

enum class Direction(val vec: Vec2I) {
    NORTH(Vec2I(0, -1)),
    EAST(Vec2I(1, 0)),
    SOUTH(Vec2I(0, 1)),
    WEST(Vec2I(-1, 0));

    fun turnedRight() = when (this) {
        NORTH -> EAST
        EAST -> SOUTH
        SOUTH -> WEST
        WEST -> NORTH
    }

    fun turnedLeft() = when (this) {
        NORTH -> WEST
        EAST -> NORTH
        SOUTH -> EAST
        WEST -> SOUTH
    }

    fun inverse() = when (this) {
        NORTH -> SOUTH
        SOUTH -> NORTH
        EAST -> WEST
        WEST -> EAST
    }

    fun transformed(rotation: Rotation) = when (rotation) {
        Rotation.NONE -> this
        Rotation.DEG90 -> this.turnedRight()
        Rotation.DEG180 -> this.inverse()
        Rotation.DEG270 -> this.turnedLeft()
    }
}