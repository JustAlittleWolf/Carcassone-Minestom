package me.wolfii

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import java.io.FileNotFoundException
import java.util.*

object CustomResponseData {
    private val faviconResource = CustomResponseData.javaClass.classLoader.getResource("icon.png") ?: throw FileNotFoundException()
    val favicon = "data:image/png;base64,${Base64.getEncoder().encode(faviconResource.readBytes()).toString(Charsets.UTF_8)}"
    val description = Component.text("Carcassone", NamedTextColor.GOLD, TextDecoration.BOLD)
}