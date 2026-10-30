package com.tgca.collection.games.frutris

import com.tgca.collection.audio.Snd
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Motor do jogo Frutris — porta nativa fiel da lógica original do jogo
 * "Frutris" de algorithomus.com.br:
 * - grade 10 x 20 visível + 4 linhas ocultas acima;
 * - peças I, J, L, O, S, T, Z com as cores exatas do original;
 * - queda 1000 ms × 0,8^(nível−1) (mínimo 100 ms);
 * - lock delay de 1000 ms com até 15 reposições;
 * - testes de deslocamento (kicks) na rotação;
 * - pontuação por linhas [0, 100, 300, 500, 800], T-spin 400 (dobrado se limpar);
 * - fila de 5 peças, guarda (hold) com canHold, combo;
 * - lixo (garbage) por marco de nível a partir do 6 e temporizado a partir do 15;
 * - meta de recorde: 30000 pontos;
 * - animação de fim de jogo linha a linha (35 ms por linha, cor 8).
 */
class FrutrisGame(
    var onSound: (Snd) -> Unit = {},
    var onAutosave: () -> Unit = {},
    var onEvent: (Event) -> Unit = {},
) {
    enum class Status { IDLE, PLAYING, PAUSED, OVER_ANIM, OVER }
    enum class Action { NONE, MOVE, ROTATE, DROP }

    sealed class Event {
        object RecordReached : Event()
        data class GameOver(val newRecord: Boolean) : Event()
    }

    data class Clearing(val y: Int, val cells: IntArray, val startedAt: Float)

    companion object {
        const val COLS = 10
        const val ROWS = 20
        const val HIDDEN = 4
        const val QUEUE = 5
        const val MAX_GARBAGE = 6
        const val GOAL = 30000
        const val LOCK_DELAY = 1000f
        const val LOCK_RESETS = 15
        const val LINE_CLEAR_MS = 420f
        const val CELEBRATION_MS = 1500f
        const val OVER_ANIM_MS = 35f

        val LINE_SCORES = intArrayOf(0, 100, 300, 500, 800)

        val KICKS = arrayOf(
            intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1),
            intArrayOf(-1, -1), intArrayOf(1, -1), intArrayOf(0, -2), intArrayOf(-2, 0),
            intArrayOf(2, 0),
        )

        /** Cores exatas do original: índice = tipo de célula (8 = morta, 9 = lixo). */
        val COLORS = intArrayOf(
            0,
            0xFF00BCC4.toInt(), // 1 I
            0xFF3F51B5.toInt(), // 2 J
            0xFFFF9800.toInt(), // 3 L
            0xFFFFEB3B.toInt(), // 4 O
            0xFF4CAF50.toInt(), // 5 S
            0xFF9C27B0.toInt(), // 6 T
            0xFFF44336.toInt(), // 7 Z
            0xFF1A1A1A.toInt(), // 8 morta
            0xFF78838E.toInt(), // 9 lixo
        )

        private val PIECES: Array<Array<IntArray>> = arrayOf(
            arrayOf(),
            arrayOf( // I
                intArrayOf(0, 0, 0, 0),
                intArrayOf(1, 1, 1, 1),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
            arrayOf( // J
                intArrayOf(2, 0, 0),
                intArrayOf(2, 2, 2),
                intArrayOf(0, 0, 0),
            ),
            arrayOf( // L
                intArrayOf(0, 0, 3),
                intArrayOf(3, 3, 3),
                intArrayOf(0, 0, 0),
            ),
            arrayOf( // O
                intArrayOf(4, 4),
                intArrayOf(4, 4),
            ),
            arrayOf( // S
                intArrayOf(0, 5, 5),
                intArrayOf(5, 5, 0),
                intArrayOf(0, 0, 0),
            ),
            arrayOf( // T
                intArrayOf(0, 6, 0),
                intArrayOf(6, 6, 6),
                intArrayOf(0, 0, 0),
            ),
            arrayOf( // Z
                intArrayOf(7, 7, 0),
                intArrayOf(0, 7, 7),
                intArrayOf(0, 0, 0),
            ),
        )
    }

    var status = Status.IDLE
    var board = Array(ROWS) { IntArray(COLS) }
    var hidden = Array(HIDDEN) { IntArray(COLS) }
    var currentType = 0
    var matrix: Array<IntArray> = arrayOf()
    var pieceX = 0
    var pieceY = 0
    var holdType = 0
    var canHold = true
    val nextQueue = ArrayDeque<Int>()
    var score = 0
    var best = 0
    var level = 1
    var totalLines = 0
    var combo = 0
    var dropInterval = 1000f
    var dropCounter = 0f
    var lockElapsed = 0f
    var lockResets = 0
    var grounded = false
    var lastAction = Action.NONE
    val pendingGarbage = mutableListOf<Int>()
    var garbageMilestone = 5
    var garbageHole = Random.nextInt(COLS)
    var garbageElapsed = 0f
    var overRow = 0
    var overTimer = 0f
    var newRecord = false
    var goalReached = false
    val clearing = mutableListOf<Clearing>()
    var celebration: String? = null
    var celebrationTimer = 0f
    var clock = 0f

    val linesToNextLevel: Int get() = 10 - (totalLines % 10)

    fun newGame() {
        board = Array(ROWS) { IntArray(COLS) }
        hidden = Array(HIDDEN) { IntArray(COLS) }
        nextQueue.clear()
        repeat(QUEUE) { nextQueue.addLast(Random.nextInt(1, 8)) }
        score = 0
        level = 1
        totalLines = 0
        combo = 0
        dropInterval = 1000f
        dropCounter = 0f
        lockElapsed = 0f
        lockResets = 0
        pendingGarbage.clear()
        garbageMilestone = 5
        garbageElapsed = 0f
        garbageHole = Random.nextInt(COLS)
        holdType = 0
        canHold = true
        overRow = 0
        overTimer = 0f
        newRecord = false
        goalReached = false
        clearing.clear()
        celebration = null
        celebrationTimer = 0f
        status = Status.PLAYING
        onSound(Snd.START)
        spawn()
        onAutosave()
    }

    fun togglePause(): Boolean {
        return when (status) {
            Status.PLAYING -> {
                status = Status.PAUSED
                onSound(Snd.UI)
                true
            }
            Status.PAUSED -> {
                status = Status.PLAYING
                onSound(Snd.UI)
                true
            }
            else -> false
        }
    }

    // ------------------------------------------------------------------ peça

    private fun baseMatrix(type: Int): Array<IntArray> =
        PIECES.getOrNull(type)?.map { it.copyOf() }.orEmpty().toTypedArray()

    /** Matriz da peça para desenho de pré-visualização (GUARDAR/PRÓXIMO). */
    fun pieceMatrix(type: Int): Array<IntArray> = baseMatrix(type)

    private fun spawnY(m: Array<IntArray>): Int {
        var last = 0
        m.forEachIndexed { r, row -> if (row.any { it != 0 }) last = r }
        return -(last + 1)
    }

    private fun collides(x: Int, y: Int, m: Array<IntArray>): Boolean {
        for (r in m.indices) {
            for (c in m[r].indices) {
                if (m[r][c] == 0) continue
                val bx = x + c
                val by = y + r
                if (bx < 0 || bx >= COLS || by >= ROWS) return true
                if (by >= 0) {
                    if (board[by][bx] != 0) return true
                } else {
                    val hy = HIDDEN + by
                    if (hy in 0 until HIDDEN && hidden[hy][bx] != 0) return true
                }
            }
        }
        return false
    }

    private fun spawn() {
        currentType = if (nextQueue.isEmpty()) Random.nextInt(1, 8) else nextQueue.removeFirst()
        while (nextQueue.size < QUEUE) nextQueue.addLast(Random.nextInt(1, 8))
        matrix = baseMatrix(currentType)
        pieceX = COLS / 2 - (matrix.firstOrNull()?.size ?: 0) / 2
        pieceY = spawnY(matrix)
        lockElapsed = 0f
        lockResets = 0
        dropCounter = 0f
        grounded = false
        lastAction = Action.NONE
        if (collides(pieceX, pieceY, matrix)) {
            startGameOver()
            return
        }
        onAutosave()
    }

    private fun resetLock() {
        if (lockResets < LOCK_RESETS) {
            lockElapsed = 0f
            lockResets++
        }
    }

    // ------------------------------------------------------------------ ações

    fun move(dx: Int): Boolean {
        if (status != Status.PLAYING) return false
        return if (!collides(pieceX + dx, pieceY, matrix)) {
            pieceX += dx
            lastAction = Action.MOVE
            onSound(Snd.MOVE)
            if (grounded) resetLock()
            onAutosave()
            true
        } else {
            onSound(Snd.WRONG)
            false
        }
    }

    fun rotate(): Boolean {
        if (status != Status.PLAYING) return false
        val rows = matrix.size
        val cols = matrix.firstOrNull()?.size ?: 0
        val rotated = Array(cols) { r -> IntArray(rows) { c -> matrix[rows - 1 - c][r] } }
        for (kick in KICKS) {
            val kx = pieceX + kick[0]
            val ky = pieceY + kick[1]
            if (!collides(kx, ky, rotated)) {
                matrix = rotated
                pieceX = kx
                pieceY = ky
                lastAction = Action.ROTATE
                onSound(Snd.ROTATE)
                if (grounded) resetLock()
                onAutosave()
                return true
            }
        }
        onSound(Snd.WRONG)
        return false
    }

    fun softDrop(): Boolean {
        if (status != Status.PLAYING) return false
        return if (!collides(pieceX, pieceY + 1, matrix)) {
            pieceY++
            lastAction = Action.MOVE
            dropCounter = 0f
            onSound(Snd.SOFT_DROP)
            if (grounded) resetLock()
            onAutosave()
            true
        } else {
            false
        }
    }

    fun hardDrop() {
        if (status != Status.PLAYING) return
        while (!collides(pieceX, pieceY + 1, matrix)) pieceY++
        lastAction = Action.DROP
        onSound(Snd.HARD_DROP)
        mergePiece()
    }

    fun hold() {
        if (status != Status.PLAYING) return
        if (!canHold) {
            onSound(Snd.WRONG)
            return
        }
        canHold = false
        val t = currentType
        if (holdType == 0) {
            currentType = nextQueue.removeFirst()
            while (nextQueue.size < QUEUE) nextQueue.addLast(Random.nextInt(1, 8))
        } else {
            currentType = holdType
        }
        holdType = t
        matrix = baseMatrix(currentType)
        pieceX = COLS / 2 - (matrix.firstOrNull()?.size ?: 0) / 2
        pieceY = spawnY(matrix)
        lockElapsed = 0f
        lockResets = 0
        dropCounter = 0f
        grounded = false
        lastAction = Action.NONE
        onSound(Snd.HOLD)
        if (collides(pieceX, pieceY, matrix)) {
            startGameOver()
        } else {
            onAutosave()
        }
    }

    fun ghostY(): Int {
        if (matrix.isEmpty()) return pieceY
        var y = pieceY
        while (!collides(pieceX, y + 1, matrix)) y++
        return y
    }

    // ------------------------------------------------------------------ relógio

    fun update(deltaMs: Float) {
        clock += deltaMs
        if (clearing.isNotEmpty()) {
            clearing.removeAll { clock - it.startedAt >= LINE_CLEAR_MS }
        }
        if (celebrationTimer > 0f) {
            celebrationTimer -= deltaMs
            if (celebrationTimer <= 0f) {
                celebrationTimer = 0f
                celebration = null
            }
        }
        when (status) {
            Status.OVER_ANIM -> {
                overTimer += deltaMs
                while (overTimer >= OVER_ANIM_MS) {
                    overTimer -= OVER_ANIM_MS
                    if (overRow < ROWS) {
                        for (c in 0 until COLS) {
                            if (board[overRow][c] != 0) board[overRow][c] = 8
                        }
                        overRow++
                    } else {
                        status = Status.OVER
                        if (score > best) newRecord = true
                        best = max(best, score)
                        onEvent(Event.GameOver(newRecord))
                        onAutosave()
                    }
                }
            }
            Status.PLAYING -> {
                advanceGarbage(deltaMs)
                if (collides(pieceX, pieceY + 1, matrix)) {
                    grounded = true
                    lockElapsed += deltaMs
                    if (lockElapsed >= LOCK_DELAY) {
                        lockElapsed = 0f
                        mergePiece()
                    }
                } else {
                    grounded = false
                    lockElapsed = 0f
                    dropCounter += deltaMs
                    if (dropCounter >= dropInterval) {
                        dropCounter = 0f
                        if (!collides(pieceX, pieceY + 1, matrix)) {
                            pieceY++
                            onSound(Snd.FALL)
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    private fun advanceGarbage(elapsed: Float) {
        if (level < 15) return
        garbageElapsed += elapsed
        val interval = max(8000f, 20000f - (level - 15) * 1000f)
        while (garbageElapsed >= interval) {
            garbageElapsed -= interval
            if (pendingGarbage.size < MAX_GARBAGE) pendingGarbage.add(Random.nextInt(COLS))
        }
    }

    private fun celebrate(msg: String) {
        celebration = msg
        celebrationTimer = CELEBRATION_MS
    }

    private fun cornersFilled(): Int {
        val cs = listOf(
            pieceX to pieceY,
            pieceX + 2 to pieceY,
            pieceX to pieceY + 2,
            pieceX + 2 to pieceY + 2,
        )
        var n = 0
        for ((x, y) in cs) {
            if (x < 0 || x >= COLS || y >= ROWS) {
                n++
                continue
            }
            if (y < 0) {
                val hy = HIDDEN + y
                if (hy in 0 until HIDDEN && hidden[hy][x] != 0) n++
            } else {
                if (board[y][x] != 0) n++
            }
        }
        return n
    }

    // ------------------------------------------------------------------ travamento

    private fun mergePiece() {
        val tspin = currentType == 6 && lastAction == Action.ROTATE && cornersFilled() >= 3

        // grade completa = linhas ocultas + visíveis
        val rows = Array(HIDDEN + ROWS) { r ->
            if (r < HIDDEN) hidden[r].copyOf() else board[r - HIDDEN].copyOf()
        }
        for (mr in matrix.indices) {
            for (mc in matrix[mr].indices) {
                val v = matrix[mr][mc]
                if (v == 0) continue
                val bx = pieceX + mc
                val by = pieceY + mr
                if (by in 0 until HIDDEN + ROWS) rows[by][bx] = v
            }
        }

        val full = (0 until HIDDEN + ROWS).filter { r -> rows[r].all { it != 0 } }
        val lines = full.size

        // lixo: linhas limpas "consomem" o lixo pendente; marcos geram extras
        val garbage = mutableListOf<Int>()
        if (lines > 0) {
            pendingGarbage.drop(lines).forEach { garbage.add(it) }
            val nextLevel = 1 + (totalLines + lines) / 10
            val milestone = min(nextLevel, 14)
            var extra = max(0, milestone - max(garbageMilestone, 5))
            while (extra-- > 0 && garbage.size < MAX_GARBAGE) garbage.add(garbageHole)
            garbageMilestone = max(garbageMilestone, milestone)
        }

        // remove linhas completas (linhas vazias entram por cima)
        val kept = mutableListOf<IntArray>()
        for (r in 0 until HIDDEN + ROWS) {
            if (r !in full) kept.add(rows[r])
        }
        val finalRows = mutableListOf<IntArray>()
        repeat((HIDDEN + ROWS) - kept.size) { finalRows.add(IntArray(COLS)) }
        kept.forEach { finalRows.add(it) }

        // aplica o lixo no fundo (empurra a pilha para cima)
        var overflow = false
        if (garbage.isNotEmpty()) {
            repeat(garbage.size) {
                val top = finalRows.removeAt(0)
                if (top.any { it != 0 }) overflow = true
            }
            garbage.forEach { hole ->
                finalRows.add(IntArray(COLS) { c -> if (c == hole) 0 else 9 })
            }
        }

        hidden = Array(HIDDEN) { finalRows[it].copyOf() }
        board = Array(ROWS) { finalRows[HIDDEN + it].copyOf() }
        pendingGarbage.clear()

        if (lines > 0) {
            full.forEach { y ->
                if (y >= HIDDEN) clearing.add(Clearing(y - HIDDEN, rows[y].copyOf(), clock))
            }
            combo++
            var base = LINE_SCORES[lines.coerceAtMost(4)]
            when (lines) {
                1 -> onSound(Snd.CLEAR1)
                2 -> onSound(Snd.CLEAR2)
                3 -> onSound(Snd.CLEAR3)
                else -> onSound(Snd.CLEAR4)
            }
            if (tspin) {
                base *= 2
                onSound(Snd.TSPIN)
                celebrate("T-SPIN!")
            }
            score += base
            totalLines += lines
            if (combo >= 2) {
                celebrate("COMBO ×$combo")
                onSound(Snd.COMBO)
            }
            val oldLevel = level
            level = 1 + totalLines / 10
            dropInterval = max(100f, (1000f * Math.pow(0.8, (level - 1).toDouble())).toFloat())
            if (level > oldLevel) {
                celebrate("NOVO NÍVEL $level!")
                onSound(Snd.LEVEL_UP)
            }
            if (score >= GOAL && !goalReached) {
                goalReached = true
                onSound(Snd.RECORD)
                onEvent(Event.RecordReached)
            }
        } else {
            combo = 0
        }

        onAutosave()

        val safe = !overflow && (0 until HIDDEN).all { h -> finalRows[h].all { c -> c == 0 } }
        if (!safe) {
            startGameOver()
        } else {
            onSound(Snd.LOCK)
            spawn()
        }
    }

    private fun startGameOver() {
        status = Status.OVER_ANIM
        overRow = 0
        overTimer = 0f
        onSound(Snd.GAME_OVER)
    }

    // ------------------------------------------------------------------ salvamento

    fun toJson(): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("status", status.name)
        o.put("board", gridToJson(board))
        o.put("hidden", gridToJson(hidden))
        o.put("cur", currentType)
        o.put("mat", gridToJson(matrix))
        o.put("px", pieceX)
        o.put("py", pieceY)
        o.put("hold", holdType)
        o.put("canHold", canHold)
        o.put("queue", JSONArray().apply { nextQueue.forEach { put(it) } })
        o.put("score", score)
        o.put("best", best)
        o.put("level", level)
        o.put("lines", totalLines)
        o.put("combo", combo)
        o.put("garbage", JSONArray().apply { pendingGarbage.forEach { put(it) } })
        o.put("mile", garbageMilestone)
        o.put("hole", garbageHole)
        o.put("gElapsed", garbageElapsed.toInt())
        o.put("newRecord", newRecord)
        o.put("goal", goalReached)
        return o.toString()
    }

    private fun gridToJson(g: Array<IntArray>): JSONArray {
        val arr = JSONArray()
        g.forEach { row -> arr.put(JSONArray().apply { row.forEach { put(it) } }) }
        return arr
    }

    private fun gridFromJson(arr: JSONArray?): Array<IntArray>? {
        if (arr == null) return null
        val out = mutableListOf<IntArray>()
        for (r in 0 until arr.length()) {
            val row = arr.getJSONArray(r)
            out.add(IntArray(row.length()) { c -> row.getInt(c) })
        }
        return out.toTypedArray()
    }

    fun loadFromJson(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            val b = gridFromJson(o.optJSONArray("board"))
            val h = gridFromJson(o.optJSONArray("hidden"))
            if (b == null || b.size != ROWS || h == null || h.size != HIDDEN) return false
            board = b
            hidden = h
            currentType = o.optInt("cur")
            matrix = gridFromJson(o.optJSONArray("mat")) ?: arrayOf()
            if (matrix.isEmpty() && currentType in 1..7) matrix = baseMatrix(currentType)
            pieceX = o.optInt("px")
            pieceY = o.optInt("py")
            holdType = o.optInt("hold")
            canHold = o.optBoolean("canHold", true)
            nextQueue.clear()
            o.optJSONArray("queue")?.let { q ->
                for (i in 0 until q.length()) nextQueue.addLast(q.getInt(i))
            }
            score = o.optInt("score")
            best = o.optInt("best")
            level = max(1, o.optInt("level", 1))
            totalLines = o.optInt("lines")
            combo = o.optInt("combo")
            pendingGarbage.clear()
            o.optJSONArray("garbage")?.let { g ->
                for (i in 0 until g.length()) pendingGarbage.add(g.getInt(i))
            }
            garbageMilestone = o.optInt("mile", 5)
            garbageHole = o.optInt("hole")
            garbageElapsed = o.optInt("gElapsed").toFloat()
            newRecord = o.optBoolean("newRecord")
            goalReached = o.optBoolean("goal")
            dropInterval = max(100f, (1000f * Math.pow(0.8, (level - 1).toDouble())).toFloat())
            clearing.clear()
            celebration = null
            celebrationTimer = 0f
            dropCounter = 0f
            lockElapsed = 0f
            lockResets = 0
            overRow = 0
            overTimer = 0f
            status = runCatching { Status.valueOf(o.optString("status")) }
                .getOrDefault(Status.IDLE)
            if (status == Status.OVER_ANIM) status = Status.OVER
            true
        } catch (t: Throwable) {
            false
        }
    }
}

