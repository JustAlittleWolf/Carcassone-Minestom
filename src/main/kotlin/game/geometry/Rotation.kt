package me.wolfii.game.geometry

enum class Rotation {
    NONE,
    DEG90,
    DEG180,
    DEG270;

    fun rotatedRight() = when (this) {
        NONE -> DEG90
        DEG90 -> DEG180
        DEG180 -> DEG270
        DEG270 -> NONE
    }

    fun transform(vec2I: Vec2I): Vec2I = when (this) {
        NONE -> vec2I
        DEG90 -> Vec2I(-vec2I.z, vec2I.x)
        DEG180 -> Vec2I(-vec2I.x, -vec2I.z)
        DEG270 -> Vec2I(vec2I.z, -vec2I.x)
    }

    fun inverse() = when (this) {
        NONE -> NONE
        DEG90 -> DEG270
        DEG180 -> DEG180
        DEG270 -> DEG90
    }
}