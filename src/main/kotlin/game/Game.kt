package me.wolfii.game

import me.wolfii.game.geometry.*
import me.wolfii.game.tile.MeepleDisplay
import me.wolfii.game.tile.Tile
import me.wolfii.game.tile.TileDisplay
import net.bladehunt.kotstom.InstanceManager
import net.bladehunt.kotstom.SchedulerManager
import net.bladehunt.kotstom.dsl.instance.generator
import net.bladehunt.kotstom.dsl.instance.modify
import net.bladehunt.kotstom.dsl.item.item
import net.bladehunt.kotstom.dsl.item.itemName
import net.bladehunt.kotstom.dsl.listen
import net.bladehunt.kotstom.dsl.scheduleTask
import net.bladehunt.kotstom.extension.editMeta
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.event.EventNode
import net.minestom.server.event.instance.InstanceTickEvent
import net.minestom.server.event.instance.InstanceUnregisterEvent
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.player.*
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.instance.LightingChunk
import net.minestom.server.instance.WorldBorder
import net.minestom.server.instance.block.Block
import net.minestom.server.inventory.PlayerInventory
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.timer.ExecutionType
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.chunk.ChunkSupplier

@SuppressWarnings
class Game {
    companion object {
        const val SPECTATING_Y = 40.0
        const val TURN_Y = 26.0
        val SPAWN = Pos(0.5, SPECTATING_Y, 0.5)
        const val FIELD_SIZE = 30
        const val FIELD_Y = -1

        private const val INITIAL_START_COUNTDOWN = 19//20 * 60
        private const val SHORTENED_START_COUNTDOWN = 20 * 5
        private const val MIN_REQUIRED_PLAYERS = 2
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
        private val MEEPLE_PLACED_SOUND = Sound.sound(Key.key("item.goat_horn.sound.1"), Sound.Source.RECORD, 0.6F, 1.2F)
        private val RUNNING_OUT_OF_TIME_SOUND = COUNTDOWN_SOUND
        private val RUNNING_OUT_OF_TIME_TICKS = listOf(1, 2, 3, 5).map { it * 20 }

        private val TILE_SET = mapOf<Tile, Int>(
            Tile.CCCC to 1,
            Tile.CRFR to 4,
            Tile.CRRF to 3,
            Tile.CCFC to 3,
            Tile.FFFF_M to 4,
            Tile.FFRF_M to 2
        )

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
        it.worldBorder = WorldBorder(16.0 * FIELD_SIZE + 1, 0.5, 0.5, 0, 0)
        it.chunkSupplier = ChunkSupplier { a, b, c -> LightingChunk(a, b, c) }
    }

    private val players: MutableList<Player> = ArrayList()
    private val turnDisplay = BossBar.bossBar(WAITING_TEXT, 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)
    private var hasStarted = false
    private var startCountdown = INITIAL_START_COUNTDOWN
    private var currentPlayer: Player? = null
    private var turnTime = TURN_TIME
    private var lockInteractions = false
    private var tileDisplay = TileDisplay(Tile.CRRF, instance)
    private var lastIteractions: MutableMap<Player, Long> = HashMap()
    private val field: MutableField<Tile?> = mutableFieldWithInitial(Vec2I(FIELD_SIZE, FIELD_SIZE)) { null }
    private var meepleDisplay = MeepleDisplay(Vec.ONE, PlayerColor.LIGHT_BLUE, instance, listOf())
    private val meeples: MutableField<MeepleEntry?> = mutableFieldWithInitial(Vec2I(FIELD_SIZE, FIELD_SIZE)) { null }
    private var turn: Int = 0
    private var turnState = TurnState.PLACE_TILE
    private var availableColors = ArrayDeque(PlayerColor.entries)
    private val playerColors: MutableMap<Player, PlayerColor> = HashMap()
    private val remainingTiles = ArrayDeque<Tile>().also { queue ->
        TILE_SET.entries.forEach { entry ->
            repeat(entry.value) {
                queue.add(entry.key)
            }
        }
        queue.shuffle()
    }

    init {
        instance.eventNode().registerGameEvents()
    }

    private fun EventNode<InstanceEvent>.registerGameEvents() {
        listen<PlayerSpawnEvent> { event ->
            event.player.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = 1.0
            event.player.getAttribute(Attribute.SNEAKING_SPEED).baseValue = 0.05
            event.player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).baseValue = Double.MAX_VALUE
            event.player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE).baseValue = Double.MAX_VALUE
            event.player.fieldViewModifier = 0.7f
            event.player.inventory.clear()
            turnDisplay.addViewer(event.player)
            if (!hasStarted && players.size < MAX_PLAYERS) players.add(event.player)
        }
        listen<ItemDropEvent> { event -> event.isCancelled = true }
        listen<PlayerDisconnectEvent> { event ->
            call(PlayerLeaveGameEvent(event.player))
        }
        listen<InstanceUnregisterEvent> { event ->
            instance.players.forEach { player -> call(PlayerLeaveGameEvent(player)) }
        }
        listen<PlayerLeaveGameEvent> { event ->
            turnDisplay.removeViewer(event.player)
            event.player.fieldViewModifier = 0.1f
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
            players.forEach { player ->
                val color = availableColors.removeFirst()
                playerColors[player] = color
                val meepleItem = item(Material.fromNamespaceId("minecraft:${color.namespaceIDColor}_concrete_powder") ?: throw IllegalStateException()) {
                    itemName = Component.text("Meeple", color.textColor)
                }
                (2..<9).forEach { slot -> player.inventory.setItemStack(slot, meepleItem) }
            }
            nextPlayer()
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
        meepleDisplay.remove()
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
                tileDisplay.show(instance)
            }
            turnTime = TURN_TIME
            turnState = TurnState.PLACE_TILE
            lockInteractions = false
            turnDisplay.name(Component.text("${currentPlayer?.username}'s turn", NamedTextColor.GREEN, TextDecoration.BOLD))
            turnDisplay.progress(1F)
        }
        turn++
    }

    private fun nextTile() {
        if (turn == 0) return
        tileDisplay.remove()
        val tile = remainingTiles.removeFirstOrNull()
        if(tile == null) {
            //@TODO spielende
            return
        }
        tileDisplay = TileDisplay(tile, instance)
    }

    private fun onRightClick(player: Player) {
        if (player != currentPlayer) return
        when (turnState) {
            TurnState.PLACE_TILE -> {
                player.playSound(ROTATE_SOUND, Sound.Emitter.self())
                tileDisplay.rotateRight()
            }

            TurnState.SELECT_MEEPLE -> {}
        }
    }

    private fun onLeftClick(player: Player) {
        if (player != currentPlayer) return
        when (turnState) {
            TurnState.PLACE_TILE -> {
                if (player.position.pitch < 20) return
                val placementInfo = placementInfo(tileDisplay.position().fieldPos(), tileDisplay.tile())
                if (placementInfo.valid) {
                    val placementResult = tileDisplay.place(instance)
                    if (!placementResult.wasPlaced) return
                    player.playSound(PLACED_SOUND, Sound.Emitter.self())
                    field[tileDisplay.position().fieldPos()] = tileDisplay.tile()
                    lockInteractions = true
                    SchedulerManager.scheduleTask(ExecutionType.TICK_END, TaskSchedule.future(placementResult.future)) {
                        if (currentPlayer == player) {
                            turnState = TurnState.SELECT_MEEPLE
                            lockInteractions = false
                            meepleDisplay = MeepleDisplay(
                                tileDisplay.position(),
                                playerColors.getValue(player),
                                instance,
                                getPlaceableMeeples(tileDisplay.tile(), tileDisplay.position().fieldPos())
                            )
                            if (!meepleDisplay.hasValidPosition() || (0..<PlayerInventory.INVENTORY_SIZE).none { slot ->
                                    player.inventory.getItemStack(slot).material().namespace().value().endsWith("concrete_powder")
                                }) {
                                nextPlayer()
                            } else {
                                meepleDisplay.selectFromPlayerFacing(player)
                                meepleDisplay.show()
                            }
                        }
                        TaskSchedule.stop()
                    }
                } else player.playSound(NO_PLACEMENT_POSSIBLE_SOUND, Sound.Emitter.self())
            }

            TurnState.SELECT_MEEPLE -> {
                if (player.position.pitch < 25) return
                if (!player.itemInMainHand.material().namespace().value().endsWith("concrete_powder")) {
                    nextPlayer()
                    return
                }
                player.itemInMainHand = ItemStack.AIR
                val selectedMeeple = this@Game.meepleDisplay.selectedMeeple()
                broadcastSound(MEEPLE_PLACED_SOUND)
                val meepleDisplayEntity = Entity(EntityType.BLOCK_DISPLAY).apply {
                    editMeta<BlockDisplayMeta> {
                        setBlockState(Block.fromNamespaceId("minecraft:${playerColors.getValue(player).namespaceIDColor}_concrete") ?: throw IllegalStateException())
                        isHasNoGravity = true
                        scale = Vec(1.5, 2.9, 1.5)
                        translation = Vec(-0.25, 0.0, -0.25)
                        posRotInterpolationDuration = 20
                    }
                    setInstance(this@Game.instance, selectedMeeple.first.position.add(0.0, 5.0, 0.0))
                }
                meeples[selectedMeeple.first.position.fieldPos()] = MeepleEntry(meepleDisplayEntity, selectedMeeple.second, playerColors.getValue(player))
                lockInteractions = true
                meepleDisplay.remove()
                SchedulerManager.scheduleNextTick { meepleDisplayEntity.teleport(selectedMeeple.first.position) }
                SchedulerManager.scheduleTask(ExecutionType.TICK_END, TaskSchedule.tick(30)) {
                    lockInteractions = false
                    nextPlayer()
                    TaskSchedule.stop()
                }
            }
        }
    }

    private fun onMove(player: Player) {
        if (player != currentPlayer) return
        when (turnState) {
            TurnState.PLACE_TILE -> tileDisplay.moveFromPlayerFacing(player)
            TurnState.SELECT_MEEPLE -> meepleDisplay.selectFromPlayerFacing(player)
        }
    }

    private fun broadcastSound(sound: Sound) = instance.players.forEach { player -> player.playSound(sound, Sound.Emitter.self()) }
    private fun Point.fieldPos(): Vec2I = Vec2I(this.withX { it - 0.5 }.chunkX() + FIELD_SIZE / 2, this.withZ { it - 0.5 }.chunkZ() + FIELD_SIZE / 2)

    private fun placementInfo(fieldPos: Vec2I, tile: Tile): PlacementInfo {
        if (!field.isInside(fieldPos)) return PlacementInfo.POSITION_OCCUPIED
        if (field.all { it == null }) return PlacementInfo.VALID
        if (field[fieldPos] != null) return PlacementInfo.POSITION_OCCUPIED
        if (Direction.entries.all { direction -> field[fieldPos + direction.vec] == null }) return PlacementInfo.NO_NEIGHBOURS
        if (Direction.entries.any { direction ->
                val neighbour = field[fieldPos + direction.vec] ?: return@any false
                return@any tile.connection(direction) != neighbour.connection(direction.inverse())
            }) return PlacementInfo.INCOMPATIBLE_CONNECTIONS
        return PlacementInfo.VALID
    }

    private fun getPlaceableMeeples(tile: Tile, tilePos: Vec2I): List<Vec2I> {
        return tile.placeableMeeples().filter { it ->
            val validSurface = tile.surfaceAt(it.x, it.z)
            val visited = HashSet<Pair<Vec2I, Map<Direction, Set<Vec2I>>>>()
            val toProcess = ArrayDeque<Pair<Vec2I, Map<Direction, Set<Vec2I>>>>(listOf(Pair(tilePos, tile.reachableNeighbourtilesFrom(it.x, it.z))))
            while (toProcess.isNotEmpty()) {
                val current = toProcess.removeFirst()
                if (visited.contains(current)) continue
                visited.add(current)
                for (entry in current.second.entries) {
                    val neighbourFieldPos = current.first + entry.key.vec
                    val neighbouringTile = field[neighbourFieldPos]
                    if (neighbouringTile == null) continue
                    val meepleEntry = meeples[neighbourFieldPos]
                    for (reachableTile in entry.value) {
                        if (neighbouringTile.surfaceAt(reachableTile.x, reachableTile.z) != validSurface) continue
                        if (meepleEntry != null && neighbouringTile.surfaceAt(meepleEntry.fieldPos.x, meepleEntry.fieldPos.z) == validSurface) {
                            if (neighbouringTile.reachableFrom(reachableTile.x, reachableTile.z).contains(meepleEntry.fieldPos)) return@filter false
                        }
                        toProcess.add(Pair(neighbourFieldPos, neighbouringTile.reachableNeighbourtilesFrom(reachableTile.x, reachableTile.z)))
                    }
                }
            }
            return@filter true
        }
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

    data class MeepleEntry(val displayEntity: Entity, val fieldPos: Vec2I, val playerColor: PlayerColor)

    fun hasStarted() = hasStarted
}