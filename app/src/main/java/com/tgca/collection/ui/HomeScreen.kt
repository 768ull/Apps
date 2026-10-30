package com.tgca.collection.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgca.collection.Dest
import com.tgca.collection.GameCatalog
import com.tgca.collection.save.GameSave

private val Bg = Color(0xFF10101A)
private val BgTop = Color(0xFF191926)
private val CardBg = Color(0xFF1B1B2A)
private val CardBorder = Color(0xFF2E2E46)
private val Ink = Color(0xFFEEEAF4)
private val Muted = Color(0xFF9A9AB2)
private val Yellow = Color(0xFFFFD60A)
private val Red = Color(0xFFD62828)

@Composable
fun HomeScreen(onOpen: (Dest) -> Unit) {
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, Bg)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo TGCA+ (fundo vermelho, texto amarelo)
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFE23B3B), Red)))
                    .border(1.dp, Color(0x33FFD60A), RoundedCornerShape(20.dp))
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "TGCA",
                        color = Yellow,
                        fontWeight = FontWeight.Black,
                        fontSize = 30.sp,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        "➕",
                        color = Yellow,
                        fontSize = 24.sp,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Two-Game Collection Apps", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(40.dp))

            GameCatalog.games.forEach { entry ->
                val best = remember(entry.id) {
                    GameSave.readInt(context, entry.bestKey, 0)
                }
                GameCard(entry = entry, best = best, onClick = {
                    onOpen(if (entry.id == "cobrinha") Dest.COBRINHA else Dest.FRUTRIS)
                })
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(28.dp))
            Text("Salvamento automático ativo", color = Muted, fontSize = 12.sp)
            Text("Novos jogos serão adicionados no futuro", color = Color(0xFF6C6C84), fontSize = 12.sp)
        }
    }
}

@Composable
private fun GameCard(entry: com.tgca.collection.GameEntry, best: Int, onClick: () -> Unit) {
    val bitmap = remember(entry.id) { entry.icon()?.asImageBitmap() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = entry.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF252538))
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(entry.tagline, color = Muted, fontSize = 13.sp)
            Text(
                if (best > 0) "Recorde: $best" else "Recorde: —",
                color = Yellow,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Muted,
        )
    }
}
