package com.tgca.collection.games.cobrinha

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tgca.collection.audio.Sfx
import com.tgca.collection.save.GameSave
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Paletas — os três temas visuais originais do jogo
// ---------------------------------------------------------------------------

data class SnakeTheme(
    val page: Color,
    val frame: Color,
    val boardA: Color,
    val boardB: Color,
    val body: Color,
    val head: Color,
    val tail: Color,
    val food: Color,
    val foodDark: Color,
    val leaf: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val panel: Color,
    val scrim: Color,
)

private val LCD = SnakeTheme(
    page = Color(0xFF121512),
    frame = Color(0xFF4B4438),
    boardA = Color(0xFF252E29),
    boardB = Color(0xFF2D3730),
    body = Color(0xFF8A795C),
    head = Color(0xFFB66D57),
    tail = Color(0xFF668052),
    food = Color(0xFFCF5949),
    foodDark = Color(0xFF9D4038),
    leaf = Color(0xFF718451),
    ink = Color(0xFFEEE8D9),
    muted = Color(0xFFBBB4A1),
    accent = Color(0xFFC9B38A),
    panel = Color(0xFF3C4539),
    scrim = Color(0xCC121512),
)

private val FOSFORO = SnakeTheme(
    page = Color(0xFF0A0F0C),
    frame = Color(0xFF12331E),
    boardA = Color(0xFF071710),
    boardB = Color(0xFF0A1D13),
    body = Color(0xFF4BD97B),
    head = Color(0xFF9CFFB2),
    tail = Color(0xFF2E9B57),
    food = Color(0xFFFF5A3C),
    foodDark = Color(0xFFB23A26),
    leaf = Color(0xFF7FD98F),
    ink = Color(0xFFD2FFDD),
    muted = Color(0xFF8FAF98),
    accent = Color(0xFF6BFF9E),
    panel = Color(0xFF123A24),
    scrim = Color(0xCC0A0F0C),
)

private val NEON = SnakeTheme(
    page = Color(0xFF0B0714),
    frame = Color(0xFF2A1B4D),
    boardA = Color(0xFF150D2A),
    boardB = Color(0xFF1A1033),
    body = Color(0xFF7C4DFF),
    head = Color(0xFFB388FF),
    tail = Color(0xFF5E35D0),
    food = Color(0xFFFF3D8A),
    foodDark = Color(0xFFC2185B),
    leaf = Color(0xFF6AE0FF),
    ink = Color(0xFFF3E5F5),
    muted = Color(0xFF9E8FB0),
    accent = Color(0xFF00E5FF),
    panel = Color(0xFF1F1338),
    scrim = Color(0xCC0B0714),
)

private val THEMES = listOf("LCD", "FÓSFORO", "NEON")
private val THEME_LIST = listOf(LCD, FOSFORO, NEON)

// ---------------------------------------------------------------------------
// Tela do jogo
// ---------------------------------------------------------------------------

@Composable
fun CobrinhaScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    var themeIdx by remember {
        mutableIntStateOf(GameSave.readInt(context, GameSave.K_COB_THEME, 0).coerceIn(0, THEMES.size - 1))
    }
    var dpadOn by remember { mutableStateOf(GameSave.readBool(context, GameSave.K_COB_DPAD, false)) }
    var muted by remember { mutableStateOf(GameSave.readBool(context, GameSave.K_AUDIO_MUTED, false)) }
    var showSettings by remember { mutableStateOf(false) }
    Sfx.muted = muted

    val t = THEME_LIST[themeIdx]

    val game = remember { CobrinhaGame(onSound = { Sfx.play(it) }, onAutosave = {}) }

    fun persistNow() {
        GameSave.writeString(context, GameSave.K_COBRINHA_STATE, game.toJson())
        GameSave.writeInt(context, GameSave.K_COBRINHA_BEST, game.best)
    }

    // Loop de animação: avança o motor a cada quadro.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0L) {
                    var delta = (now - last) / 1_000_000f
                    if (delta > 500f) delta = 0f // pausa em segundo plano: descarta o salto
                    game.update(delta)
                }
                last = now
                tick++
            }
        }
    }

    // Restaura o salvamento automático e liga os retornos do motor.
    LaunchedEffect(Unit) {
        game.onAutosave = { persistNow() }
        GameSave.readString(context, GameSave.K_COBRINHA_STATE)?.let { game.loadFromJson(it) }
        game.best = max(game.best, GameSave.readInt(context, GameSave.K_COBRINHA_BEST, 0))
    }

    // Mantém a tela acesa durante a partida e salva ao sair do primeiro plano.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        view.keepScreenOn = true
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) persistNow()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            view.keepScreenOn = false
        }
    }

    val swipePx = with(density) { 20.dp.toPx() }
    val dragTotals = remember { floatArrayOf(0f, 0f) }

    Column(
        Modifier
            .fillMaxSize()
            .background(t.page)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ------------------------------------------------------------------ HUD
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { persistNow(); onExit() }) {
                Icon(Icons.Filled.Home, contentDescription = "Voltar ao início", tint = t.muted)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "PONTOS",
                    color = t.muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    "${game.score}",
                    color = if (game.pulse > 0f) t.accent else t.ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("RECORDE", color = t.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(
                    "${max(game.best, game.score)}",
                    color = t.accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = { game.togglePause() }) {
                Icon(
                    if (game.status == CobrinhaGame.Status.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Pausar",
                    tint = t.ink,
                )
            }
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(Icons.Filled.Settings, contentDescription = "Opções", tint = t.ink)
            }
        }

        if (showSettings) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(t.panel)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TEMA", color = t.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    THEMES.forEachIndexed { i, name ->
                        FilterChip(
                            selected = themeIdx == i,
                            onClick = {
                                themeIdx = i
                                GameSave.writeInt(context, GameSave.K_COB_THEME, i)
                            },
                            label = { Text(name, fontSize = 12.sp) },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("DIRECIONAL NA TELA", color = t.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = dpadOn,
                        onCheckedChange = {
                            dpadOn = it
                            GameSave.writeBool(context, GameSave.K_COB_DPAD, it)
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SONS", color = t.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = !muted,
                        onCheckedChange = {
                            muted = !it
                            Sfx.muted = muted
                            GameSave.writeBool(context, GameSave.K_AUDIO_MUTED, muted)
                        },
                    )
                }
            }
        }

        // ------------------------------------------------------------- TABULEIRO
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            val boardDp = maxWidth.coerceAtMost(maxHeight)
            Box(Modifier.size(boardDp), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier
                        .size(boardDp)
                        .pointerInput(game.status) {
                            detectTapGestures {
                                if (game.status == CobrinhaGame.Status.IDLE ||
                                    game.status == CobrinhaGame.Status.OVER
                                ) {
                                    game.newGame()
                                }
                            }
                        }
                        .pointerInput(swipePx) {
                            detectDragGestures(
                                onDrag = { change, amount ->
                                    dragTotals[0] += amount.x
                                    dragTotals[1] += amount.y
                                    change.consume()
                                },
                                onDragEnd = {
                                    val ax = abs(dragTotals[0])
                                    val ay = abs(dragTotals[1])
                                    if (max(ax, ay) >= swipePx) {
                                        if (ax >= ay * 1.2f) {
                                            game.turn(if (dragTotals[0] > 0f) CobrinhaGame.Dir.RIGHT else CobrinhaGame.Dir.LEFT)
                                        } else if (ay >= ax * 1.2f) {
                                            game.turn(if (dragTotals[1] > 0f) CobrinhaGame.Dir.DOWN else CobrinhaGame.Dir.UP)
                                        }
                                    }
                                    dragTotals[0] = 0f
                                    dragTotals[1] = 0f
                                },
                                onDragCancel = {
                                    dragTotals[0] = 0f
                                    dragTotals[1] = 0f
                                },
                            )
                        }
                ) {
                    drawBoard(game, t)
                }
            }

            // ----------------------------------------------------------- Sobreposições
            game.countdownText?.let { txt ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        txt,
                        color = t.ink,
                        fontSize = if (txt.length > 3) 44.sp else 64.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            when (game.status) {
                CobrinhaGame.Status.IDLE -> OverlayBox(t) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Jogo da Cobrinha", color = t.ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(6.dp))
                        Text("toque para começar", color = t.muted, fontSize = 14.sp)
                        Spacer(Modifier.height(22.dp))
                        OverlayButton(t, "JOGAR") { game.newGame() }
                    }
                }
                CobrinhaGame.Status.PAUSED -> OverlayBox(t) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PAUSA", color = t.ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(4.dp))
                        Text("a partida está esperando", color = t.muted, fontSize = 14.sp)
                        Spacer(Modifier.height(22.dp))
                        OverlayButton(t, "CONTINUAR") { game.togglePause() }
                        Spacer(Modifier.height(10.dp))
                        OverlayButton(t, "RECOMEÇAR") { game.newGame() }
                    }
                }
                CobrinhaGame.Status.OVER -> OverlayBox(t) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (game.won) "VOCÊ VENCEU" else "FIM DE JOGO",
                            color = t.ink,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${game.score} PTS · RECORDE ${max(game.best, game.score)}",
                            color = t.accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("toque para tentar de novo", color = t.muted, fontSize = 14.sp)
                        Spacer(Modifier.height(22.dp))
                        OverlayButton(t, "JOGAR DE NOVO") { game.newGame() }
                        Spacer(Modifier.height(10.dp))
                        OverlayButton(t, "CASA") { persistNow(); onExit() }
                    }
                }
                else -> Unit
            }
        }

        // ------------------------------------------------------------- DIRECIONAL
        if (dpadOn) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DirButton(Icons.Filled.KeyboardArrowUp, t) { game.turn(CobrinhaGame.Dir.UP) }
                Row {
                    DirButton(Icons.Filled.KeyboardArrowLeft, t) { game.turn(CobrinhaGame.Dir.LEFT) }
                    Spacer(Modifier.width(10.dp))
                    DirButton(Icons.Filled.KeyboardArrowDown, t) { game.turn(CobrinhaGame.Dir.DOWN) }
                    Spacer(Modifier.width(10.dp))
                    DirButton(Icons.Filled.KeyboardArrowRight, t) { game.turn(CobrinhaGame.Dir.RIGHT) }
                }
            }
        }
    }
}

@Composable
private fun OverlayBox(t: SnakeTheme, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(t.scrim),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun OverlayButton(t: SnakeTheme, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = t.accent, contentColor = t.page),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DirButton(icon: androidx.compose.ui.graphics.vector.ImageVector, t: SnakeTheme, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(320)
            while (pressed) {
                onClick()
                delay(110)
            }
        }
    }
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(t.panel)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onClick()
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = t.ink)
    }
}

// ---------------------------------------------------------------------------
// Desenho do tabuleiro
// ---------------------------------------------------------------------------

private fun DrawScope.drawBoard(game: CobrinhaGame, t: SnakeTheme) {
    val cell = size.width / CobrinhaGame.COLS
    val boardPx = cell * CobrinhaGame.COLS

    val shakeAmp = (game.shakeRemaining / CobrinhaGame.SHAKE_MS) * cell * 0.35f
    val shakeX = if (shakeAmp > 0f) sin(game.shakeRemaining * 0.12f) * shakeAmp else 0f

    translate(shakeX, 0f) {
        // moldura
        val framePad = cell * 0.35f
        drawRoundRect(
            color = t.frame,
            topLeft = Offset(-framePad, -framePad),
            size = Size(boardPx + framePad * 2f, boardPx + framePad * 2f),
            cornerRadius = CornerRadius(framePad * 1.4f),
        )
        // células em xadrez
        for (y in 0 until CobrinhaGame.ROWS) {
            for (x in 0 until CobrinhaGame.COLS) {
                val c = if ((x + y) % 2 == 0) t.boardA else t.boardB
                drawRect(
                    color = c,
                    topLeft = Offset(x * cell, y * cell),
                    size = Size(cell, cell),
                )
            }
        }
        // comida (fruta com folha)
        game.food?.let { f ->
            val cx = (f.x + 0.5f) * cell
            val cy = (f.y + 0.5f) * cell
            val r = cell * 0.33f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(t.food, t.foodDark),
                    center = Offset(cx - r * 0.25f, cy - r * 0.25f),
                    radius = r * 1.5f,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
            rotate(-30f, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    color = t.leaf,
                    topLeft = Offset(cx - cell * 0.045f, cy - r - cell * 0.14f),
                    size = Size(cell * 0.09f, cell * 0.18f),
                    cornerRadius = CornerRadius(cell * 0.04f),
                )
            }
        }
        // cobra
        game.snake.forEachIndexed { i, p ->
            val color = when {
                i == 0 -> t.head
                i == game.snake.size - 1 && game.snake.size > 1 -> t.tail
                else -> t.body
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(p.x * cell + cell * 0.06f, p.y * cell + cell * 0.06f),
                size = Size(cell * 0.88f, cell * 0.88f),
                cornerRadius = CornerRadius(cell * 0.30f),
            )
        }
        // olhos da cabeça
        if (game.snake.isNotEmpty() && game.status != CobrinhaGame.Status.IDLE) {
            val head = game.snake.first()
            val hcx = (head.x + 0.5f) * cell
            val hcy = (head.y + 0.5f) * cell
            val off = cell * 0.16f
            val r = cell * 0.07f
            val eye = t.page
            when (game.dir) {
                CobrinhaGame.Dir.UP -> {
                    drawCircle(eye, r, Offset(hcx - off, hcy - off))
                    drawCircle(eye, r, Offset(hcx + off, hcy - off))
                }
                CobrinhaGame.Dir.DOWN -> {
                    drawCircle(eye, r, Offset(hcx - off, hcy + off))
                    drawCircle(eye, r, Offset(hcx + off, hcy + off))
                }
                CobrinhaGame.Dir.LEFT -> {
                    drawCircle(eye, r, Offset(hcx - off, hcy - off))
                    drawCircle(eye, r, Offset(hcx - off, hcy + off))
                }
                CobrinhaGame.Dir.RIGHT -> {
                    drawCircle(eye, r, Offset(hcx + off, hcy - off))
                    drawCircle(eye, r, Offset(hcx + off, hcy + off))
                }
            }
        }
        // anel do efeito de comer
        val fx = game.eatFx
        if (fx != null && game.pulse > 0f) {
            val pr = 1f - game.pulse
            drawCircle(
                color = t.leaf.copy(alpha = game.pulse * 0.7f),
                radius = cell * (0.3f + pr * 0.5f),
                center = Offset((fx.x + 0.5f) * cell, (fx.y + 0.5f) * cell),
                style = Stroke(width = cell * 0.06f),
            )
        }
    }
}

