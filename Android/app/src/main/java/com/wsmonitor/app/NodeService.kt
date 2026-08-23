package com.wsmonitor.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Foreground service that runs the embedded Node.js WinSysMonitor server
 * (nodejs-mobile). The server keeps running so agents can connect to this
 * phone while the app is backgrounded.
 */
class NodeService : Service() {

    companion object {
        private const val CH_ID = "node_server"
        private const val NID = 2001
        private const val PREFS = "NODEJS_MOBILE_PREFS"
        private const val KEY_LAST_UPDATE = "NODEJS_MOBILE_APK_LastUpdateTime"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        private var startedNodeAlready = false

        fun start(context: Context) {
            val i = Intent(context, NodeService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }

        /** Stop the embedded server. The node thread exits once its event loop drains. */
        fun stop(context: Context) {
            isRunning = false
            startedNodeAlready = false
            context.stopService(Intent(context, NodeService::class.java))
        }
    }

    private var thread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val cfg = ServerConfig.load(this)
        if (cfg.mode != "host") {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("WinSysMonitor Server")
            .setContentText("Starting on port ${cfg.port}…")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 26) startForeground(NID, notification)

        if (!startedNodeAlready && thread == null) {
            thread = Thread {
                try {
                    runServer(cfg.port)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            thread?.start()
        }
        return START_STICKY
    }

    private fun runServer(port: Int) {
        val ctx = applicationContext
        val nodeDir = File(ctx.filesDir, "nodejs-project")

        if (wasAPKUpdated()) {
            deleteRecursively(nodeDir)
            copyAssetFolder(ctx.assets, "nodejs-project", nodeDir.absolutePath)
            saveLastUpdateTime()
        }

        val cfg = ServerConfig.load(this)
        val bindHost = if (cfg.bind.isNotBlank()) cfg.bind else "0.0.0.0"
        val conf = JSONObject()
        conf.put("port", port)
        conf.put("host", bindHost)
        // main.cjs reads cfg.password -> process.env.ADMIN_PASSWORD
        conf.put("password", AppPrefs.serverAdminPassword(this))
        File(nodeDir, "server.config.json").writeText(conf.toString())

        startedNodeAlready = true
        isRunning = true
        updateNotification("Server running on port $port")
        startNodeWithArguments(arrayOf("node", File(nodeDir, "main.cjs").absolutePath))
        isRunning = false
        stopSelf()
    }

    private fun updateNotification(text: String) {
        if (Build.VERSION.SDK_INT >= 26) {
            val n = NotificationCompat.Builder(this, CH_ID)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("WinSysMonitor Server")
                .setContentText(text)
                .setOngoing(true)
                .build()
            startForeground(NID, n)
        }
    }

    // ── asset helpers ──────────────────────────────────────────────────────

    private fun wasAPKUpdated(): Boolean {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getLong(KEY_LAST_UPDATE, 0L)
        val lastUpdate = try {
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            previous
        }
        return lastUpdate != previous
    }

    private fun saveLastUpdateTime() {
        val lastUpdate = try {
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            System.currentTimeMillis()
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_UPDATE, lastUpdate).apply()
    }

    private fun deleteRecursively(file: File): Boolean {
        if (!file.exists()) return true
        val children = file.listFiles() ?: return file.delete()
        for (child in children) {
            if (child.isDirectory) deleteRecursively(child) else child.delete()
        }
        return file.delete()
    }

    private fun copyAssetFolder(assets: AssetManager, from: String, to: String): Boolean {
        try {
            val files = assets.list(from) ?: return false
            if (files.isEmpty()) {
                return copyAsset(assets, from, to)
            }
            File(to).mkdirs()
            for (f in files) copyAssetFolder(assets, "$from/$f", "$to/$f")
            return true
        } catch (e: IOException) {
            return false
        }
    }

    private fun copyAsset(assets: AssetManager, from: String, to: String): Boolean {
        try {
            assets.open(from).use { input ->
                FileOutputStream(to).use { output ->
                    copyStream(input, output)
                }
            }
            return true
        } catch (e: IOException) {
            return false
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CH_ID, "WinSysMonitor Server", NotificationManager.IMPORTANCE_LOW
            )
            ch.description = "Keeps the embedded monitor server running"
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    // ── native entrypoint (implemented in native-lib.cpp) ──────────────────
    external fun startNodeWithArguments(arguments: Array<String>): Int

    override fun onBind(intent: Intent?): IBinder? = null

    init {
        System.loadLibrary("node")
        System.loadLibrary("native-lib")
    }
}