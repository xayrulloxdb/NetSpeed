package uz.netspeed

import android.content.Context

object Prefs {
    private const val FILE = "settings"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(c: Context) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(c: Context, v: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, v).apply()
}
