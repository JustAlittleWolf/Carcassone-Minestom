package me.wolfii.game.geometry

interface Field<T> {
    val width: Int
    val height: Int
    val indices: Collection<Vec2I>

    operator fun get(pos: Vec2I): T? = get(pos.x, pos.z)
    operator fun get(x: Int, y: Int): T? = rows().getOrNull(y)?.getOrNull(x)

    fun getValue(pos: Vec2I): T = getValue(pos.x, pos.z)
    fun getValue(x: Int, y: Int) = rows()[y][x]

    fun isInside(pos: Vec2I): Boolean = isInside(pos.x, pos.z)
    fun isInside(x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < width && y < height

    fun rows(): List<List<T>>
}

interface MutableField<T> : Field<T> {
    operator fun set(pos: Vec2I, value: T) = set(pos.x, pos.z, value)
    operator fun set(x: Int, y: Int, value: T)
}

data class FieldImpl<T>(private val rows: List<List<T>>) : Field<T> {
    override val height = rows.size
    override val width = if (height == 0) 0 else rows[0].size

    init {
        require(rows.all { it.size == width }) { "field has to be a rectangle" }
    }

    override val indices = (0..<width).flatMap { x -> (0..<height).map { y -> Vec2I(x, y) } }

    override fun rows() = rows

    override fun toString() = toString(this)
}

data class MutableFieldImpl<T>(private val rows: List<MutableList<T>>) : MutableField<T> {
    override val height = rows.size
    override val width = if (height == 0) 0 else rows[0].size

    override val indices = (0..<width).flatMap { x -> (0..<height).map { y -> Vec2I(x, y) } }

    override fun rows() = rows

    override fun set(x: Int, y: Int, value: T) {
        rows[y][x] = value
    }

    override fun toString() = toString(this)
}

private fun toString(field: Field<*>): String {
    val stringBuilder = StringBuilder()
    for (row in field.rows()) {
        for (elem in row) {
            stringBuilder.append(elem)
        }
        stringBuilder.append('\n')
    }
    stringBuilder.removeSuffix("\n")
    return stringBuilder.toString()
}

infix fun Vec2I.inside(field: Field<*>) = field.isInside(this)

fun List<String>.toCharField(): Field<Char> = FieldImpl(this.map { it.toCharArray().filterNot(Char::isWhitespace) })
fun List<String>.toMutableCharField(): MutableField<Char> = MutableFieldImpl(this.map { it.toCharArray().filterNot(Char::isWhitespace).toMutableList() })

fun <T> fieldWithInitial(dimensions: Vec2I, initial: (index: Vec2I) -> T): Field<T> = FieldImpl(List(dimensions.z) { y -> List(dimensions.x) { x -> initial(Vec2I(x, y)) } })
fun <T> mutableFieldWithInitial(dimensions: Vec2I, initial: (index: Vec2I) -> T): MutableField<T> =
    MutableFieldImpl(MutableList(dimensions.z) { y -> MutableList(dimensions.x) { x -> initial(Vec2I(x, y)) } })

fun <T> List<List<T>>.toField(): Field<T> = FieldImpl(this)

fun <T> List<List<T>>.toMutableField(): MutableField<T> {
    val height = this.size
    if (height == 0) return MutableFieldImpl(listOf())
    val width = this[0].size
    if (this.any { it.size != width }) throw IllegalArgumentException("Field was not a rectangle.")
    return MutableFieldImpl<T>(this.map { row -> row.mapTo(ArrayList<T>()) { it } })
}

fun <T> Field<T>.toMutableField(): MutableField<T> {
    return MutableFieldImpl<T>(this.rows().map { row -> row.mapTo(ArrayList<T>()) { it } })
}

inline fun <T, R> Field<T>.map(transform: (T) -> R): Field<R> {
    return FieldImpl(rows().map { row -> row.map(transform) })
}

inline fun <T, R> Field<T>.mapIndexed(transform: (Vec2I, T) -> R): Field<R> {
    return FieldImpl(rows().mapIndexed { y, row -> row.mapIndexed { x, value -> transform(Vec2I(x, y), value) } })
}

inline fun <T> Field<T>.forEach(action: (T) -> Unit) {
    this.rows().forEach { row -> row.forEach(action) }
}

inline fun <T> Field<T>.forEachIndexed(action: (Vec2I, T) -> Unit) {
    this.rows().forEachIndexed { y, row -> row.forEachIndexed { x, value -> action(Vec2I(x, y), value) } }
}

inline fun <T> Field<T>.count(predicate: (T) -> Boolean): Int = this.rows().sumOf { row -> row.count(predicate) }

inline fun <T> Field<T>.countIndexed(predicate: (Vec2I, T) -> Boolean): Int {
    var sum = 0
    this.forEachIndexed { pos, ch -> if (predicate(pos, ch)) sum++ }
    return sum
}

inline fun <T> Field<T>.filter(predicate: (T) -> Boolean): List<T> = ArrayList<T>().also { list -> this.forEach { ch -> if (predicate(ch)) list.add(ch) } }

inline fun <T> Field<T>.filterIndexed(predicate: (Vec2I, T) -> Boolean): List<T> = ArrayList<T>().also { list -> this.forEachIndexed { pos, ch -> if (predicate(pos, ch)) list.add(ch) } }

inline fun <T> Field<T>.sumOf(operation: (T) -> Long): Long {
    var sum = 0L
    this.forEach { sum += operation(it) }
    return sum
}

inline fun <T> Field<T>.sumOfIndexed(operation: (Vec2I, T) -> Long): Long {
    var sum = 0L
    this.forEachIndexed { index, elem -> sum += operation(index, elem) }
    return sum
}

inline fun <T> Field<T>.any(predicate: (T) -> Boolean): Boolean = this.rows().any { row -> row.any(predicate) }

inline fun <T> Field<T>.anyIndexed(predicate: (Vec2I, T) -> Boolean): Boolean {
    this.forEachIndexed { index, elem -> if (predicate(index, elem)) return true }
    return false
}

inline fun <T> Field<T>.all(predicate: (T) -> Boolean): Boolean = this.rows().all { row -> row.all(predicate) }

inline fun <T> Field<T>.none(predicate: (T) -> Boolean): Boolean = this.rows().all { row -> row.none(predicate) }