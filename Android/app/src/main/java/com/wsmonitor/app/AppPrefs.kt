package com.wsmonitor.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

object AppPrefs {

    private const val PREFS = "app_prefs"
    private const val KEY_THEME = "theme_index"
    private const val KEY_NOTIF_ENABLED = "notif_enabled"
    private const val KEY_TONE = "notif_tone"
    private const val KEY_TONE_URI = "notif_tone_uri"
    private const val KEY_ICON = "notif_icon"
    private const val KEY_APP_ICON = "app_icon"
    private const val KEY_ADMIN_PASSWORD = "admin_password"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── theme ─────────────────────────────────────────────────────────────
    fun themeIndex(ctx: Context): Int = sp(ctx).getInt(KEY_THEME, 0)

    fun saveTheme(ctx: Context, index: Int) =
        sp(ctx).edit().putInt(KEY_THEME, index).apply()

    fun accentColor(ctx: Context): Int {
        val colors = ctx.resources.getIntArray(com.wsmonitor.app.R.array.theme_colors)
        val i = themeIndex(ctx).coerceIn(0, colors.size - 1)
        return colors[i]
    }

    // ── notifications ─────────────────────────────────────────────────────
    fun notifEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_NOTIF_ENABLED, true)

    fun saveNotifEnabled(ctx: Context, enabled: Boolean) =
        sp(ctx).edit().putBoolean(KEY_NOTIF_ENABLED, enabled).apply()

    /** tone key: "system" | "chime" | "alert" | "ding" | "soft" | "custom" */
    fun tone(ctx: Context): String = sp(ctx).getString(KEY_TONE, "system") ?: "system"

    fun saveTone(ctx: Context, tone: String) =
        sp(ctx).edit().putString(KEY_TONE, tone).apply()

    fun customToneUri(ctx: Context): String? = sp(ctx).getString(KEY_TONE_URI, null)

    fun saveCustomToneUri(ctx: Context, uri: String?) =
        sp(ctx).edit().putString(KEY_TONE_URI, uri).apply()

    /** drawable resource name for the notification icon */
    fun notifIcon(ctx: Context): String = sp(ctx).getString(KEY_ICON, "ic_notify") ?: "ic_notify"

    fun saveNotifIcon(ctx: Context, resName: String) =
        sp(ctx).edit().putString(KEY_ICON, resName).apply()

    fun notifIconRes(ctx: Context): Int {
        val name = notifIcon(ctx)
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        return if (id != 0) id else com.wsmonitor.app.R.drawable.ic_notify
    }

    // ── launcher (app) icon ───────────────────────────────────────────────
    fun appIcon(ctx: Context): String = sp(ctx).getString(KEY_APP_ICON, "Default") ?: "Default"

    fun saveAppIcon(ctx: Context, key: String) =
        sp(ctx).edit().putString(KEY_APP_ICON, key).apply()

    // ── admin password (owner secret, never shown in UI) ──────────────────
    fun adminPassword(ctx: Context): String =
        sp(ctx).getString(KEY_ADMIN_PASSWORD, ServerConfig.DEFAULT_ADMIN_PASSWORD)
            ?: ServerConfig.DEFAULT_ADMIN_PASSWORD

    fun saveAdminPassword(ctx: Context, password: String) =
        sp(ctx).edit().putString(KEY_ADMIN_PASSWORD, password).apply()

    /** Resolve the Uri to play for the chosen tone. Null => system default. */
    fun toneUri(ctx: Context): Uri? {
        return when (tone(ctx)) {
            "chime" -> Uri.parse("android.resource://${ctx.packageName}/${com.wsmonitor.app.R.raw.tone_chime}")
            "alert" -> Uri.parse("android.resource://${ctx.packageName}/${com.wsmonitor.app.R.raw.tone_alert}")
            "ding" -> Uri.parse("android.resource://${ctx.packageName}/${com.wsmonitor.app.R.raw.tone_ding}")
            "soft" -> Uri.parse("android.resource://${ctx.packageName}/${com.wsmonitor.app.R.raw.tone_soft}")
            "custom" -> customToneUri(ctx)?.let { Uri.parse(it) }
            else -> null // system default
        }
    }
}