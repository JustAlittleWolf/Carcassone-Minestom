package me.wolfii.game

import me.wolfii.game.geometry.Direction
import me.wolfii.game.geometry.MutableField
import me.wolfii.game.geometry.Vec2I
import me.wolfii.game.geometry.all
import me.wolfii.game.geometry.mutableFieldWithInitial
import me.wolfii.game.tile.RotateableTile
import me.wolfii.game.tile.Tile
import me.wolfii.game.tile.TileDisplay
import net.bladehunt.kotstom.InstanceManager
import net.bladehunt.kotstom.SchedulerManager
import net.bladehunt.kotstom.dsl.instance.generator
import net.bladehunt.kotstom.dsl.instance.modify
import net.bladehunt.kotstom.dsl.listen
import net.bladehunt.kotstom.dsl.scheduleTask
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.instance.InstanceUnregisterEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerHandAnimationEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.WorldBorder
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.ExecutionType
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.chunk.ChunkSupplier

@SuppressWarnings
class Game {
    companion object {
        const val SPECTATING_Y = 48.0
        const val TURN_Y = 34.0
        val SPAWN = Pos(0.5, SPECTATING_Y, 0.5)
        const val FIELD_SIZE = 100
        const val FIELD_Y = -1

        private const val INITIAL_START_COUNTDOWN = 19//20 * 60
        private const val SHORTENED_START_COUNTDOWN = 20 * 5
        private const val MIN_REQUIRED_PLAYERS = 1
        private const val MAX_PLAYERS = 8
        private val COUNTDOWN_NOTIFY_TIMES = setOf(1, 2, 3, 5, 10, 30).map { it * 20 }
        private val COUNTDOWN_SOUND = Sound.sound(Key.key("block.note_block.pling"), Sound.Source.RECORD, 1F, 1F)
        private val START_SOUND = Sound.sound(Key.key("block.note_block.pling"), Sound.Source.RECORD, 1F, 2F)
        private val WAITING_TEXT = Component.text("Waiting for players", NamedTextColor.YELLOW, TextDecoration.BOLD)

        private const val TURN_TIME = 20 * 30
        const val TURN_SCALE = 7.0
        private const val SPECTATING_SCALE = 1.0

        private const val INTERACT_TIMEOUT_TICKS = 3
        private val NO_PLACEMENT_POSSIBLE_SOUND = Sound.sound(Key.key("block.note_block.didgeridoo"), Sound.Source.RECORD, 0.7F, 0.707107F)
        private val PLACED_SOUND = Sound.sound(Key.key("block.note_block.chime"), Sound.Source.RECORD, 1F, 1F)
        private val ROTATE_SOUND = Sound.sound(Key.key("entity.player.attack.sweep"), Sound.Source.PLAYER, 0.4F, 0.9F)
        private val NEXT_PLAYER_SOUND = Sound.sound(Key.key("block.note_block.harp"), Sound.Source.RECORD, 1F, 1F)
        private val RUNNING_OUT_OF_TIME_SOUND = COUNTDOWN_SOUND
        private val RUNNING_OUT_OF_TIME_TICKS = listOf(1, 2, 3, 5).map { it * 20 }

        init {
            require(FIELD_SIZE % 2 == 0)
        }
    }

    val instance = InstanceManager.createInstanceContainer().also {
        it.timeRate = 0
        it.generator {
            modify {
                for (x in absoluteStart().blockX()..<absoluteEnd().blockX()) {
                    for (z in absoluteStart().blockZ()..<absoluteEnd().blockZ()) {
                        if (x % 16 == 0 || z % 16 == 0) setBlock(x, FIELD_Y, z, Block.SPRUCE_WOOD)
                        else setBlock(x, FIELD_Y, z, Block.STRIPPED_SPRUCE_WOOD)
                    }
                }
                fill(absoluteStart().withY(SPECTATING_Y - 1), absoluteEnd().withY(SPECTATING_Y), Block.BARRIER)
                fill(absoluteStart().withY(TURN_Y - 1), absoluteEnd().withY(TURN_Y), Block.BARRIER)
            }
        }
        it.worldBorder = WorldBorder(16.0 * (FIELD_SIZE / 2) + 1, 0.5, 0.5, 0, 0)
        it.chunkSupplier = ChunkSupplier { a, b, c -> LightingChunk(a, b, c) }
        it.eventNode().registerGameEvents()
    }

    private val turnDisplay = BossBar.bossBar(WAITING_TEXT, 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)
    private val players: MutableList<Player> = ArrayList()
    private var hasStarted = false
    private var startCountdown = INITIAL_START_COUNTDOWN
    private var currentPlayer: Player? = null
    private var turnTime = TURN_TIME
    private var lockInteractions = false
    private var tileDisplay = TileDisplay(Tile.CRRF, this)
    private var lastIteractions: MutableMap<Player, Long> = HashMap()
    private val field: MutableField<RotateableTile?> = mutableFieldWithInitial(Vec2I(FIELD_SIZE, FIELD_SIZE)) { null }
    private var turn: Int = 0
    private var turnState = TurnState.PLACE_TILE

    private fun EventNode<InstanceEvent>.registerGameEvents() {
        listen<PlayerSpawnEvent> { event ->
            event.player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 1.0
            event.player.getAttribute(Attribute.SNEAKING_SPEED).baseValue = 0.05
            event.player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).baseValue = Double.MAX_VALUE
            event.player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).baseValue = Double.MAX_VALUE
            event.player.inventory.clear()
            turnDisplay.addViewer(event.player)
            if (!hasStarted && players.size < MAX_PLAYERS) players.add(event.player)
        }
        listen<PlayerDisconnectEvent> { event ->
            call(PlayerLeaveGameEvent(event.player))
        }
        listen<InstanceUnregisterEvent> { event ->
            instance.players.forEach { player -> call(PlayerLeaveGameEvent(player)) }
        }
        listen<PlayerLeaveGameEvent> { event ->
            turnDisplay.removeViewer(event.player)
            if (players.remove(event.player) && hasStarted) nextPlayer()
        }
        listen<InstanceTickEvent> { tick() }
        listen<PlayerMoveEvent> { event ->
            if (lockInteractions) return@listen
            onMove(event.player)
        }
        listen<PlayerBlockInteractEvent> { event ->
            if (lockInteractions) return@listen
            val diff = instance.worldAge - lastIteractions.getOrPut(event.player) { instance.worldAge - INTERACT_TIMEOUT_TICKS }
            if (diff >= INTERACT_TIMEOUT_TICKS) {
                lastIteractions[event.player] = instance.worldAge
                onRightClick(event.player)
            }
        }
        listen<PlayerHandAnimationEvent> { event ->
            if (lockInteractions) return@listen
            onLeftClick(event.player)
        }
    }

    private fun tick() {
        if (!hasStarted) preStartTick().also { return }
        currentPlayer?.let { tickTurn(it) } ?: nextPlayer()
    }

    private fun preStartTick() {
        if (players.size < MIN_REQUIRED_PLAYERS) {
            startCountdown = INITIAL_START_COUNTDOWN
            turnDisplay.name(WAITING_TEXT)
        } else {
            startCountdown--
            if (players.size == MAX_PLAYERS && startCountdown > SHORTENED_START_COUNTDOWN) startCountdown = SHORTENED_START_COUNTDOWN
            turnDisplay.name(Component.text("Starting in ${startCountdown / 20} seconds", NamedTextColor.GRAY, TextDecoration.BOLD))
        }

        if (startCountdown < 0) {
            hasStarted = true
            broadcastSound(START_SOUND)
            nextPlayer()
            turn = 1
        } else {
            turnDisplay.progress(startCountdown.toFloat() / INITIAL_START_COUNTDOWN)
            if (COUNTDOWN_NOTIFY_TIMES.contains(startCountdown)) broadcastSound(COUNTDOWN_SOUND)
        }
    }

    private fun tickTurn(player: Player) {
        if (!lockInteractions) {
            if (--turnTime < 0) nextPlayer()
            else {
                if (turnTime in RUNNING_OUT_OF_TIME_TICKS) player.playSound(RUNNING_OUT_OF_TIME_SOUND, Sound.Emitter.self())
                val placementInfo = placementInfo(tileDisplay.position().fieldPos(), tileDisplay.tile())
                tileDisplay.setColor(placementInfo.infoColor)
            }
        }
        turnDisplay.progress(turnTime.toFloat() / TURN_TIME)
    }

    private fun nextPlayer() {
        if (players.isEmpty()) currentPlayer = null
        else {
            currentPlayer?.let {
                it.getAttribute(Attribute.SCALE).baseValue = SPECTATING_SCALE
                it.teleport(it.position.withY(SPECTATING_Y))
            }
            if (currentPlayer != null) broadcastSound(NEXT_PLAYER_SOUND)
            currentPlayer = players[(players.indexOf(currentPlayer) + 1) % players.size].also {
                it.getAttribute(Attribute.SCALE).baseValue = TURN_SCALE
                it.teleport(it.position.withY(TURN_Y))
                nextTile()
                tileDisplay.moveFromPlayerFacing(it)
                tileDisplay.show(instance.players)
            }
            turnTime = TURN_TIME
            turnState = TurnState.PLACE_TILE
            lockInteractions = false
            turnDisplay.name(Component.text("${currentPlayer?.username}'s turn", NamedTextColor.GREEN, TextDecoration.BOLD))
            turnDisplay.progress(1F)
        }
    }

    private fun nextTile() {
        tileDisplay.remove()
        // @TODO tile set
        tileDisplay = TileDisplay(listOf(Tile.CCCC, Tile.CCFC, Tile.CRFR, Tile.CRRF).random(), this)
    }

    private fun onRightClick(player: Player) {
        if (player == currentPlayer) {
            when (turnState) {
                TurnState.PLACE_TILE -> {
                    player.playSound(ROTATE_SOUND, Sound.Emitter.self())
                    tileDisplay.rotateRight()
                }

                TurnState.SELECT_MEEPLE -> {

                }
            }
        }
    }

    private fun onLeftClick(player: Player) {
        if (player.position.pitch < 20) return
        if (player == currentPlayer) {
            when (turnState) {
                TurnState.PLACE_TILE -> {
                    val placementInfo = placementInfo(tileDisplay.position().fieldPos(), tileDisplay.tile())
                    if (placementInfo.valid) {
                        val placementResult = tileDisplay.place(instance)
                        if(placementResult.wasPlaced) {
                            player.playSound(PLACED_SOUND, Sound.Emitter.self())
                            field[tileDisplay.position().fieldPos()] = tileDisplay.tile()
                            lockInteractions = true
                            SchedulerManager.scheduleTask(ExecutionType.TICK_END, TaskSchedule.future(placementResult.future)) {
                                if (currentPlayer == player) {
                                    turnState = TurnState.SELECT_MEEPLE
                                    lockInteractions = false
                                }
                                TaskSchedule.stop()
                            }
                        }
                    } else player.playSound(NO_PLACEMENT_POSSIBLE_SOUND, Sound.Emitter.self())
                }

                TurnState.SELECT_MEEPLE -> {
                    nextPlayer()
                }
            }
        }
    }

    private fun onMove(player: Player) {
        if (player == currentPlayer) {
            when (turnState) {
                TurnState.PLACE_TILE -> {
                    tileDisplay.moveFromPlayerFacing(player)
                }

                TurnState.SELECT_MEEPLE -> {

                }
            }
        }
    }

    private fun broadcastSound(sound: Sound) = instance.players.forEach { player -> player.playSound(sound, Sound.Emitter.self()) }
    private fun Point.fieldPos(): Vec2I = Vec2I(this.withX { it - 0.5 }.chunkX() + FIELD_SIZE / 2, this.withZ { it - 0.5 }.chunkZ() + FIELD_SIZE / 2)

    private fun placementInfo(fieldPos: Vec2I, tile: Tile): PlacementInfo {
        if (!field.isInside(fieldPos)) return PlacementInfo.POSITION_OCCUPIED
        if (turn == 1 && field.all { it == null }) return PlacementInfo.VALID
        if (field[fieldPos] != null) return PlacementInfo.POSITION_OCCUPIED
        if (Direction.entries.all { direction -> field[fieldPos + direction.vec] == null }) return PlacementInfo.NO_NEIGHBOURS
        if (Direction.entries.any { direction ->
                val neighbour = field[fieldPos + direction.vec] ?: return@any false
                return@any tile.connection(direction) != neighbour.connection(direction.inverse())
            }) return PlacementInfo.INCOMPATIBLE_CONNECTIONS
        return PlacementInfo.VALID
    }

    private enum class PlacementInfo(val valid: Boolean, val infoColor: TextColor) {
        VALID(true, NamedTextColor.GREEN),
        POSITION_OCCUPIED(false, NamedTextColor.RED),
        INCOMPATIBLE_CONNECTIONS(false, NamedTextColor.GOLD),
        NO_NEIGHBOURS(false, NamedTextColor.YELLOW)
    }

    private enum class TurnState {
        PLACE_TILE,
        SELECT_MEEPLE
    }

    fun hasStarted() = hasStarted
}