plugins {
    kotlin("jvm") version "2.1.0"
}

group = "me.wolfii"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://mvn.bladehunt.net/releases")
}

dependencies {
    implementation("org.slf4j:slf4j-simple:2.0.16")

    implementation("net.minestom:minestom-snapshots:c976f345d1")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.bladehunt:kotstom:0.4.0-beta.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

kotlin {
    jvmToolchain(21)
}