package me.wolfii.game

import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor

enum class PlayerColor(val textColor: TextColor, val namespaceIDColor: String) {
    LIGHT_BLUE("#3aafd9".toTextColor(), "light_blue"),
    LIME("#70b919".toTextColor(), "lime"),
    YELLOW("#f8c527".toTextColor(), "yellow"),
    RED("#a02722".toTextColor(), "red"),
    MAGENTA("#bd44b3".toTextColor(), "magenta"),
    ORANGE("#f07613".toTextColor(), "orange"),
    BLUE("#35399d".toTextColor(), "blue"),
    GREEN("#546d1b".toTextColor(), "green"),
    CYAN("#158991".toTextColor(), "cyan"),
    PINK("#ed8dac".toTextColor(), "pink"),
    BROWN("#724728".toTextColor(), "brown"),
    WHITE("#e9ecec".toTextColor(), "white"),
    GRAY("#3e4447".toTextColor(), "gray"),
    BLACK("#141519".toTextColor(), "black"),
    LIGHT_GRAY("#8e8e86".toTextColor(), "light_gray")
}

private fun String.toTextColor(): TextColor = TextColor.fromHexString(this) ?: throw IllegalStateException()