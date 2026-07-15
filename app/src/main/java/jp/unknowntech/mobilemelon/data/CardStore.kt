package jp.unknowntech.mobilemelon.data

import android.content.Context

/** Persists the cardholder's own IDi (16 hex chars) across launches. */
class CardStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("melon", Context.MODE_PRIVATE)

    fun loadIdiHex(): String? = prefs.getString(KEY_IDI, null)

    fun saveIdiHex(idiHex: String) {
        prefs.edit().putString(KEY_IDI, idiHex).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_IDI).apply()
    }

    private companion object {
        const val KEY_IDI = "idi_hex"
    }
}
