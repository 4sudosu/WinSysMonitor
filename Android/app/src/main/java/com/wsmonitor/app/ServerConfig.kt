package com.wsmonitor.app

import android.content.Context

data class ServerConfigData(
    val mode: String,       // "host" | "connect"
    val port: Int,
    val bind: String,
    val url: String         // connect-mode url, e.g. http://192.168.1.50:3001
)

object ServerConfig {
    private const val PREFS = "wsm_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_PORT = "port"
    private const val KEY_BIND = "bind"
    private const val KEY_URL = "connect_url"
    private const val KEY_DEVICE_ID = "device_id"

    /** Default admin password for the embedded / host server (matches server.js). */
    const val DEFAULT_ADMIN_PASSWORD = "Alok@1234"

    fun load(context: Context): ServerConfigData {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ServerConfigData(
            mode = p.getString(KEY_MODE, "connect") ?: "connect",
            port = p.getInt(KEY_PORT, 3001),
            bind = p.getString(KEY_BIND, "0.0.0.0") ?: "0.0.0.0",
            url = p.getString(KEY_URL, "") ?: ""
        )
    }

    fun loadDeviceId(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getString(KEY_DEVICE_ID, "").ifBlank { generateAndSaveDeviceId(context) }
    }

    private fun generateAndSaveDeviceId(context: Context): String {
        val deviceId = java.util.UUID.randomUUID().toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()
        return deviceId
    }

    fun saveHost(context: Context, port: Int, bind: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, "host")
            .putInt(KEY_PORT, port)
            .putString(KEY_BIND, bind)
            .apply()
    }

    fun saveConnect(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, "connect")
            .putString(KEY_URL, url)
            .apply()
    }

    /** The URL the WebView should load for the current mode. */
    fun dashboardUrl(context: Context): String? {
        val cfg = load(context)
        return if (cfg.mode == "host") "http://127.0.0.1:${cfg.port}"
        else cfg.url.ifBlank { null }
    }
}