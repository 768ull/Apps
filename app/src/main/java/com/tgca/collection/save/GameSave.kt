package com.tgca.collection.save

import android.content.Context

/**
 * Persistência do TGCA+ em SharedPreferences. Utilizada pelo salvamento
 * automático dos jogos, pelos recordes e pelas preferências.
 */
object GameSave {
    private const val PREFS = "tgca_save"

    const val K_COBRINHA_STATE = "cobrinha.state"
    const val K_COBRINHA_BEST = "cobrinha.best"
    const val K_FRUTRIS_STATE = "frutris.state"
    const val K_FRUTRIS_BEST = "frutris.best"
    const val K_AUDIO_MUTED = "audio.muted"
    const val K_COB_THEME = "cobrinha.theme"
    const val K_COB_DPAD = "cobrinha.dpad"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun readString(ctx: Context, key: String): String? = prefs(ctx).getString(key, null)

    fun writeString(ctx: Context, key: String, value: String) {
        prefs(ctx).edit().putString(key, value).apply()
    }

    fun readInt(ctx: Context, key: String, def: Int): Int =
        prefs(ctx).getInt(key, def)

    fun writeInt(ctx: Context, key: String, value: Int) {
        prefs(ctx).edit().putInt(key, value).apply()
    }

    fun readBool(ctx: Context, key: String, def: Boolean): Boolean =
        prefs(ctx).getBoolean(key, def)

    fun writeBool(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(key, value).apply()
    }
}
