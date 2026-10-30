package com.tgca.collection.games.cobrinha

import com.tgca.collection.audio.Snd
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Motor do jogo Cobrinha — porta nativa fiel da lógica original do jogo
 * "Jogo da Cobrinha" de algorithomus.com.br:
 * - grade 14 x 14, início com 4 segmentos no centro, andar para a direita;
 * - passo base de 145 ms, −9 ms por nível (mínimo 72 ms);
 * - um nível a cada 5 frutas (máximo 10), +10 pontos por fruta;
 * - contagem regressiva "3", "2", "1", "COBRA!" (620 ms cada);
 * - tremor de 520 ms na morte; fila de 2 entradas com proteção anti-reverso;
 * - ao preencher a grade inteira, o jogador vence.
 */
class CobrinhaGame(
    var onSound: (Snd) -> Unit = {},
    var onAutosave: () -> Unit = {},
) {
    enum class Status { IDLE, COUNTDOWN, PLAYING, PAUSED, OVER }

    enum class Dir(val dx: Int, val dy: Int) {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        val opposite: Dir
            get() = when (this) {
                UP -> DOWN
                DOWN -> UP
                LEFT -> RIGHT
                RIGHT -> LEFT
            }
    }

    data class Pt(val x: Int, val y: Int)

    companion object {
        const val COLS = 14
        const val ROWS = 14
        const val START_LENGTH = 4
        const val BASE_STEP_MS = 145
        const val STEP_DECREMENT = 9
        const val MIN_STEP_MS = 72
        const val FRUITS_PER_LEVEL = 5
        const val MAX_LEVEL = 10
        const val COUNTDOWN_MS = 620f
        const val SHAKE_MS = 520f
        const val PULSE_DECAY_MS = 260f
        val COUNTDOWN = listOf("3", "2", "1", "COBRA!")
    }

    var status = Status.IDLE
    var snake: MutableList<Pt> = mutableListOf()
    var dir = Dir.RIGHT
    private val inputQueue = mutableListOf<Dir>()
    var food: Pt? = null
    var score = 0
    var best = 0
    var level = 1
    var eaten = 0
    var won = false
    var countdownIndex = 0
    private var countdownElapsed = 0f
    var stepAcc = 0f
    var moveProgress = 0f
    var pulse = 0f
    var shakeRemaining = 0f
    var eatFx: Pt? = null
    var deathAt: Pt? = null

    val countdownText: String?
        get() = if (status == Status.COUNTDOWN) COUNTDOWN.getOrNull(countdownIndex) else null

    fun stepMs(): Int = max(MIN_STEP_MS, BASE_STEP_MS - STEP_DECREMENT * (level - 1))

    fun newGame() {
        snake = mutableListOf()
        val startY = ROWS / 2
        for (i in 0 until START_LENGTH) {
            snake.add(Pt(START_LENGTH - 1 - i, startY))
        }
        dir = Dir.RIGHT
        inputQueue.clear()
        score = 0
        level = 1
        eaten = 0
        won = false
        eatFx = null
        deathAt = null
        stepAcc = 0f
        moveProgress = 0f
        pulse = 0f
        shakeRemaining = 0f
        countdownIndex = 0
        countdownElapsed = 0f
        placeFood()
        status = Status.COUNTDOWN
        onSound(Snd.START)
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

    /** Retorna true quando a direção foi aceita (ou quando inicia o jogo). */
    fun turn(d: Dir): Boolean {
        if (status == Status.IDLE || status == Status.OVER) {
            newGame()
            return true
        }
        if (status != Status.PLAYING && status != Status.COUNTDOWN) return false
        val ref = inputQueue.lastOrNull() ?: dir
        if (d == ref || d == ref.opposite) return false
        if (inputQueue.size >= 2) return false
        inputQueue.add(d)
        return true
    }

    fun update(deltaMs: Float) {
        when (status) {
            Status.COUNTDOWN -> {
                countdownElapsed += deltaMs
                if (countdownElapsed >= COUNTDOWN_MS) {
                    countdownElapsed = 0f
                    if (countdownIndex >= COUNTDOWN.size - 1) {
                        status = Status.PLAYING
                    } else {
                        countdownIndex++
                    }
                }
            }
            Status.PLAYING -> {
                stepAcc += deltaMs
                var steps = 0
                while (stepAcc >= stepMs() && steps < 4) {
                    stepAcc -= stepMs()
                    steps++
                    if (!step()) {
                        stepAcc = 0f
                        break
                    }
                }
                if (steps >= 4) stepAcc = 0f
                moveProgress = if (status == Status.PLAYING) min(1f, stepAcc / stepMs()) else 0f
            }
            else -> Unit
        }
        if (pulse > 0f) pulse = max(0f, pulse - deltaMs / PULSE_DECAY_MS)
        if (shakeRemaining > 0f) shakeRemaining = max(0f, shakeRemaining - deltaMs)
    }

    private fun step(): Boolean {
        if (inputQueue.isNotEmpty()) dir = inputQueue.removeAt(0)
        moveProgress = 0f
        val head = snake.first()
        val nx = head.x + dir.dx
        val ny = head.y + dir.dy
        if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS) {
            die(Pt(nx, ny))
            return false
        }
        val ate = food?.let { it.x == nx && it.y == ny } ?: false
        val bodyToCheck = if (ate) snake.size else snake.size - 1
        for (i in 0 until bodyToCheck) {
            if (snake[i].x == nx && snake[i].y == ny) {
                die(Pt(nx, ny))
                return false
            }
        }
        snake.add(0, Pt(nx, ny))
        if (!ate) {
            snake.removeAt(snake.size - 1)
        } else {
            eaten++
            score += 10
            pulse = 1f
            eatFx = Pt(nx, ny)
            onSound(Snd.EAT)
            if (eaten % FRUITS_PER_LEVEL == 0 && level < MAX_LEVEL) level++
            if (!placeFood()) {
                won = true
                status = Status.OVER
                best = max(best, score)
                onAutosave()
                return false
            }
            onAutosave()
        }
        return true
    }

    private fun die(at: Pt) {
        deathAt = at
        status = Status.OVER
        shakeRemaining = SHAKE_MS
        best = max(best, score)
        onSound(Snd.DIE)
        onAutosave()
    }

    private fun placeFood(): Boolean {
        val free = mutableListOf<Pt>()
        for (y in 0 until ROWS) {
            for (x in 0 until COLS) {
                if (snake.none { it.x == x && it.y == y }) free.add(Pt(x, y))
            }
        }
        if (free.isEmpty()) {
            food = null
            return false
        }
        food = free[Random.nextInt(free.size)]
        return true
    }

    // ---------------------------------------------------------------------
    // Salvamento automático (serialização em JSON)
    // ---------------------------------------------------------------------

    fun toJson(): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("status", status.name)
        o.put("dir", dir.name)
        o.put("score", score)
        o.put("best", best)
        o.put("level", level)
        o.put("eaten", eaten)
        o.put("won", won)
        o.put("cd", countdownIndex)
        val arr = JSONArray()
        snake.forEach { arr.put(JSONArray().put(it.x).put(it.y)) }
        o.put("snake", arr)
        food?.let { o.put("food", JSONArray().put(it.x).put(it.y)) }
        return o.toString()
    }

    fun loadFromJson(json: String): Boolean {
        return try {
            val o = JSONObject(json)
            val arr = o.optJSONArray("snake") ?: return false
            val loadedSnake = mutableListOf<Pt>()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONArray(i)
                loadedSnake.add(Pt(p.getInt(0), p.getInt(1)))
            }
            if (loadedSnake.isEmpty()) return false
            snake = loadedSnake
            status = runCatching { Status.valueOf(o.optString("status")) }
                .getOrDefault(Status.IDLE)
            dir = runCatching { Dir.valueOf(o.optString("dir")) }.getOrDefault(Dir.RIGHT)
            score = o.optInt("score")
            best = o.optInt("best")
            level = o.optInt("level", 1).coerceIn(1, MAX_LEVEL)
            eaten = o.optInt("eaten")
            won = o.optBoolean("won")
            countdownIndex = o.optInt("cd")
            food = o.optJSONArray("food")?.let { p -> Pt(p.getInt(0), p.getInt(1)) }
            if (food == null && status != Status.OVER && status != Status.IDLE) placeFood()
            stepAcc = 0f
            moveProgress = 0f
            pulse = 0f
            shakeRemaining = 0f
            eatFx = null
            deathAt = null
            inputQueue.clear()
            true
        } catch (t: Throwable) {
            false
        }
    }
}
