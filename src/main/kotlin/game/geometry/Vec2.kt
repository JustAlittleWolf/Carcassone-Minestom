package me.wolfii.game.geometry

data class Vec2I(val x: Int, val z: Int) {
    operator fun plus(other: Vec2I) = Vec2I(x + other.x, z + other.z)
    operator fun minus(other: Vec2I) = Vec2I(x - other.x, z - other.z)
    operator fun times(lambda: Int) = Vec2I(x * lambda, z * lambda)
}
