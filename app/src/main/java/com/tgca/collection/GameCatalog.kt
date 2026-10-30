package com.tgca.collection

import android.graphics.Bitmap

/**
 * Catálogo de jogos da coleção. Para adicionar um novo jogo no futuro:
 * 1. Acrescente uma entrada na lista [games].
 * 2. Crie a tela do jogo e um destino no enum [Dest].
 * 3. Ligue o destino em MainActivity.TGCAApp().
 */
enum class Dest { HOME, COBRINHA, FRUTRIS }

data class GameEntry(
    val id: String,
    val title: String,
    val tagline: String,
    val bestKey: String,
    val icon: () -> Bitmap?,
)

object GameCatalog {
    val games: List<GameEntry> = listOf(
        GameEntry(
            id = "cobrinha",
            title = "Cobrinha",
            tagline = "Jogo da Cobrinha",
            bestKey = "cobrinha.best",
            icon = { IconAssets.cobrinhaIcon() },
        ),
        GameEntry(
            id = "frutris",
            title = "Frutris",
            tagline = "Estilo Tetris",
            bestKey = "frutris.best",
            icon = { IconAssets.frutrisIcon() },
        ),
    )
}
