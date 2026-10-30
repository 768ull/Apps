package com.tgca.collection.games.frutris

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import kotlin.math.min

private val Bg = Color(0xFF0B0B12)
private val Panel = Color(0xFF15151F)
private val PanelBorder = Color(0xFF262638)
private val Ink = Color(0xFFEEEAF4)
private val Muted = Color(0xFF9A9AB2)
private val Accent = Color(0xFFB78AFF)
private val Yellow = Color(0xFFFFD60A)
private val BtnBg = Color(0xFF1B1B2A)
private val BtnBorder = Color(0xFF2E2E46)
private val Scrim = Color(0xCC0B0B12)

@Composable
fun FrutrisScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    var muted by remember { mutableStateOf(GameSave.readBool(context, GameSave.K_AUDIO_MUTED, false)) }
    var showRecord by remember { mutableStateOf(false) }
    Sfx.muted = muted

    val game = remember { FrutrisGame(onSound = { Sfx.play(it) }, onAutosave = {}, onEvent = {}) }

    fun persistNow() {
        GameSave.writeString(context, GameSave.K_FRUTRIS_STATE, game.toJson())
        GameSave.writeInt(context, GameSave.K_FRUTRIS_BEST, game.best)
    }

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0L) {
                    var delta = (now - last) / 1_000_000f
                    if (delta > 500f) delta = 0f
                    game.update(delta)
                }
                last = now
                tick++
            }
        }
    }

    LaunchedEffect(Unit) {
        game.onAutosave = { persistNow() }
        game.onEvent = { ev ->
            when (ev) {
                FrutrisGame.Event.RecordReached -> {
                    showRecord = true
                    game.status = FrutrisGame.Status.PAUSED
                }
                is FrutrisGame.Event.GameOver -> Unit
            }
        }
        GameSave.readString(context, GameSave.K_FRUTRIS_STATE)?.let { game.loadFromJson(it) }
        game.best = max(game.best, GameSave.readInt(context, GameSave.K_FRUTRIS_BEST, 0))
    }

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

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ----------------------------------------------------------------- HUD
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { persistNow(); onExit() }) {
                Icon(Icons.Filled.Home, contentDescription = "Voltar ao início", tint = Muted)
            }
            Column(Modifier.weight(1f)) {
                Text("PONTOS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("${game.score}", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("RECORDE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("${max(game.best, game.score)}/30000", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { game.togglePause() }) {
                Icon(
                    if (game.status == FrutrisGame.Status.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Pausar",
                    tint = Ink,
                )
            }
        }

        // ------------------------------------------------------------- TABULEIRO
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            val sideW = 66.dp
            val availW = maxWidth - sideW * 2 - 20.dp
            var boardH = availW * 2f
            if (boardH > maxHeight) boardH = maxHeight
            val boardW = boardH / 2f
            val cellPx = with(density) { (boardW / FrutrisGame.COLS).toPx() }
            val stepX = with(density) {
                cellPx.coerceIn(18.dp.toPx(), 32.dp.toPx())
            }
            val stepY = stepX * 1.2f

            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ------------------------------------------------ coluna esquerda
                Column(Modifier.width(sideW)) {
                    PanelBox("GUARDAR") {
                        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                            drawMiniPiece(game.pieceMatrix(game.holdType), dim = !game.canHold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    PanelBox("NÍVEL") {
                        Text("${game.level}", color = Accent, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text(
                            "${game.linesToNextLevel} LINHAS PARA SUBIR",
                            color = Muted,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // ------------------------------------------------------ tabuleiro
                Box(Modifier.size(boardW, boardH)) {
                    Canvas(
                        Modifier
                            .size(boardW, boardH)
                            .border(1.dp, PanelBorder, RoundedCornerShape(8.dp))
                            .pointerInput(stepX) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val startX = down.position.x
                                    val startY = down.position.y
                                    var anchorX = startX
                                    var anchorY = startY
                                    var maxX = 0f
                                    var maxY = 0f
                                    var axis = 0 // 0 nenhum, 1 horizontal, 2 baixo, 3 cima
                                    var softDropped = false
                                    val startTime = System.currentTimeMillis()
                                    val tapPx = 10.dp.toPx()
                                    val axisPx = 8.dp.toPx()
                                    val swipePx = max(54.dp.toPx(), stepX * 2f)

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        val x = change.position.x
                                        val y = change.position.y
                                        maxX = max(maxX, abs(x - startX))
                                        maxY = max(maxY, abs(y - startY))
                                        val elapsed = System.currentTimeMillis() - startTime
                                        if (axis == 0) {
                                            val dx = x - startX
                                            val dy = y - startY
                                            if (abs(dx) > axisPx || abs(dy) > axisPx) {
                                                axis = when {
                                                    abs(dx) >= abs(dy) -> 1
                                                    dy > 0 -> 2
                                                    else -> 3
                                                }
                                            }
                                        }
                                        when (axis) {
                                            1 -> {
                                                val dist = x - anchorX
                                                var steps = (abs(dist) / stepX).toInt().coerceAtMost(10)
                                                while (steps-- > 0) {
                                                    game.move(if (dist > 0) 1 else -1)
                                                    anchorX += if (dist > 0) stepX else -stepX
                                                }
                                            }
                                            2 -> if (elapsed > 250) {
                                                val dist = y - anchorY
                                                var steps = (dist / stepY).toInt().coerceAtMost(20)
                                                while (steps-- > 0) {
                                                    if (!game.softDrop()) break
                                                    anchorY += stepY
                                                    softDropped = true
                                                }
                                            }
                                            else -> Unit
                                        }
                                    }

                                    val elapsed = System.currentTimeMillis() - startTime
                                    when {
                                        axis == 0 && maxX <= tapPx && maxY <= tapPx && elapsed <= 300 ->
                                            game.rotate()
                                        axis == 3 && maxY >= swipePx && maxY >= maxX * 2f ->
                                            game.hold()
                                        axis == 2 && !softDropped && maxY >= swipePx &&
                                            maxY >= maxX * 1.8f && elapsed <= 250 ->
                                            game.hardDrop()
                                    }
                                }
                            }
                    ) {
                        drawBoard(game, cellPx)
                    }

                    // celebração (COMBO / NOVO NÍVEL / T-SPIN)
                    game.celebration?.let { msg ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(top = 8.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Text(
                                msg,
                                color = Yellow,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xAA1B1B2A))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }

                    when {
                        showRecord -> OverlayBox {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("VOCÊ É O MELHOR!", color = Yellow, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(4.dp))
                                Text("30000 pontos alcançados", color = Muted, fontSize = 13.sp)
                                Spacer(Modifier.height(14.dp))
                                OverlayButton("VOLTAR AO JOGO") {
                                    showRecord = false
                                    game.status = FrutrisGame.Status.PLAYING
                                }
                            }
                        }
                        game.status == FrutrisGame.Status.IDLE -> OverlayBox {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Frutris", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
                                Text("estilo Tetris", color = Muted, fontSize = 14.sp)
                                Spacer(Modifier.height(18.dp))
                                OverlayButton("JOGAR") { game.newGame() }
                            }
                        }
                        game.status == FrutrisGame.Status.PAUSED -> OverlayBox {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PAUSA", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(12.dp))
                                OverlayButton("CONTINUAR") { game.togglePause() }
                                Spacer(Modifier.height(8.dp))
                                OverlayButton("RECOMEÇAR") { game.newGame() }
                                Spacer(Modifier.height(8.dp))
                                OverlayButton("CASA") { persistNow(); onExit() }
                                Spacer(Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("SONS", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
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
                        game.status == FrutrisGame.Status.OVER -> OverlayBox {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("FIM DE JOGO", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                                if (game.newRecord) {
                                    Text("NOVO RECORDE!", color = Yellow, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("${game.score} PTS", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(18.dp))
                                OverlayButton("JOGAR NOVAMENTE") { game.newGame() }
                                Spacer(Modifier.height(8.dp))
                                OverlayButton("CASA") { persistNow(); onExit() }
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // --------------------------------------------------- coluna direita
                Column(Modifier.width(sideW)) {
                    PanelBox("PRÓXIMO") {
                        Canvas(Modifier.fillMaxWidth().height(118.dp)) {
                            val next3 = game.nextQueue.take(3)
                            val cellMini = min(size.width / 5f, size.height / 10.5f)
                            next3.forEachIndexed { i, type ->
                                val m = game.pieceMatrix(type)
                                if (m.isEmpty()) return@forEachIndexed
                                val w = m[0].size
                                val h = m.size
                                val ox = (size.width - w * cellMini) / 2f
                                val oy = i * cellMini * 3.5f + (cellMini * 3f - h * cellMini) / 2f
                                m.forEachIndexed { r, row ->
                                    row.forEachIndexed { c, v ->
                                        if (v != 0) {
                                            drawRoundRect(
                                                color = Color(FrutrisGame.COLORS[v]),
                                                topLeft = Offset(ox + c * cellMini + 1f, oy + r * cellMini + 1f),
                                                size = Size(cellMini - 2f, cellMini - 2f),
                                                cornerRadius = CornerRadius(cellMini * 0.18f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------- CONTROLES
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                CtrlButton(Icons.Filled.KeyboardArrowLeft, repeat = true) { game.move(-1) }
                Spacer(Modifier.width(10.dp))
                CtrlButton(Icons.Filled.Refresh, repeat = false) { game.rotate() }
                Spacer(Modifier.width(10.dp))
                CtrlButton(Icons.Filled.KeyboardArrowRight, repeat = true) { game.move(1) }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                CtrlButton(Icons.Filled.SwapHoriz, repeat = false) { game.hold() }
                Spacer(Modifier.width(10.dp))
                CtrlButton(Icons.Filled.KeyboardDoubleArrowDown, repeat = false) { game.hardDrop() }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Componentes auxiliares
// ---------------------------------------------------------------------------

@Composable
private fun PanelBox(label: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .border(1.dp, PanelBorder, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun OverlayBox(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun OverlayButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF12081F)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CtrlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    repeat: Boolean,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed && repeat) {
            delay(150)
            while (pressed) {
                onClick()
                delay(40)
            }
        }
    }
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BtnBg)
            .border(1.dp, BtnBorder, RoundedCornerShape(12.dp))
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
        Icon(icon, contentDescription = null, tint = Ink)
    }
}

// ---------------------------------------------------------------------------
// Desenho
// ---------------------------------------------------------------------------

private fun DrawScope.drawMiniPiece(matrix: Array<IntArray>, dim: Boolean) {
    if (matrix.isEmpty()) return
    val m = matrix
    val cellMini = min(size.width / 5f, size.height / 4f)
    val w = m[0].size
    val h = m.size
    val ox = (size.width - w * cellMini) / 2f
    val oy = (size.height - h * cellMini) / 2f
    m.forEachIndexed { r, row ->
        row.forEachIndexed { c, v ->
            if (v != 0) {
                drawRoundRect(
                    color = Color(FrutrisGame.COLORS[v]).copy(alpha = if (dim) 0.35f else 1f),
                    topLeft = Offset(ox + c * cellMini + 1f, oy + r * cellMini + 1f),
                    size = Size(cellMini - 2f, cellMini - 2f),
                    cornerRadius = CornerRadius(cellMini * 0.18f),
                )
            }
        }
    }
}

private fun DrawScope.drawBoard(game: FrutrisGame, cell: Float) {
    val boardW = cell * FrutrisGame.COLS
    val boardH = cell * FrutrisGame.ROWS
    drawRoundRect(Color(0xFF08080E), size = Size(boardW, boardH), cornerRadius = CornerRadius(6f))

    // grade sutil
    val gridColor = Color(0x141E1E30)
    for (c in 0..FrutrisGame.COLS) {
        drawLine(gridColor, Offset(c * cell, 0f), Offset(c * cell, boardH), 1f)
    }
    for (r in 0..FrutrisGame.ROWS) {
        drawLine(gridColor, Offset(0f, r * cell), Offset(boardW, r * cell), 1f)
    }

    // células fixas
    for (r in 0 until FrutrisGame.ROWS) {
        for (c in 0 until FrutrisGame.COLS) {
            val v = game.board[r][c]
            if (v != 0) drawCell(Color(FrutrisGame.COLORS[v]), c, r, cell)
        }
    }

    // fantasma e peça atual (apenas durante a partida)
    if (game.status == FrutrisGame.Status.PLAYING || game.status == FrutrisGame.Status.PAUSED) {
        val gy = game.ghostY()
        for (mr in game.matrix.indices) {
            for (mc in game.matrix[mr].indices) {
                if (game.matrix[mr][mc] == 0) continue
                val by = gy + mr
                val bx = game.pieceX + mc
                if (by in 0 until FrutrisGame.ROWS) {
                    drawCell(
                        Color(FrutrisGame.COLORS[game.currentType]).copy(alpha = 0.22f),
                        bx, by, cell,
                    )
                }
            }
        }

        // peça atual
        for (mr in game.matrix.indices) {
            for (mc in game.matrix[mr].indices) {
                if (game.matrix[mr][mc] == 0) continue
                val by = game.pieceY + mr
                val bx = game.pieceX + mc
                if (by in 0 until FrutrisGame.ROWS) {
                    drawCell(Color(FrutrisGame.COLORS[game.currentType]), bx, by, cell)
                }
            }
        }
    }

    // brilho das linhas em limpeza
    game.clearing.forEach { cl ->
        val frac = ((game.clock - cl.startedAt) / FrutrisGame.LINE_CLEAR_MS).coerceIn(0f, 1f)
        drawRoundRect(
            color = Color.White.copy(alpha = (1f - frac) * 0.6f),
            topLeft = Offset(0f, cl.y * cell),
            size = Size(boardW, cell),
            cornerRadius = CornerRadius(4f),
        )
    }
}

private fun DrawScope.drawCell(color: Color, x: Int, y: Int, cell: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x * cell + 1f, y * cell + 1f),
        size = Size(cell - 2f, cell - 2f),
        cornerRadius = CornerRadius(cell * 0.16f),
    )
}
