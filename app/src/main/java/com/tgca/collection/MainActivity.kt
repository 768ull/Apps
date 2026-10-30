package com.tgca.collection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tgca.collection.games.cobrinha.CobrinhaScreen
import com.tgca.collection.games.frutris.FrutrisScreen
import com.tgca.collection.ui.HomeScreen

/**
 * Activity única do TGCA+. A tela cheia imersiva esconde a barra de status e a
 * barra de navegação (os botões do sistema não aparecem durante o jogo).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent { TGCAApp() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun TGCAApp() {
    var dest by remember { mutableStateOf(Dest.HOME) }
    Crossfade(targetState = dest, label = "tgca-dest") { d ->
        when (d) {
            Dest.HOME -> HomeScreen(onOpen = { dest = it })
            Dest.COBRINHA -> CobrinhaScreen(onExit = { dest = Dest.HOME })
            Dest.FRUTRIS -> FrutrisScreen(onExit = { dest = Dest.HOME })
        }
    }
}
