package com.wsmonitor.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ConnectActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.connect_title)

        val inputIp = findViewById<EditText>(R.id.inputIp)
        val inputPort = findViewById<EditText>(R.id.inputPort)
        val inputServerAdminPass = findViewById<EditText>(R.id.inputServerAdminPass)

        // prefill from saved connect config (URL/IP only)
        val cfg = ServerConfig.load(this)
        if (cfg.mode == "connect" && cfg.url.isNotBlank()) {
            val parts = cfg.url.removePrefix("http://").removePrefix("https://").split(":")
            if (parts.size >= 1) inputIp.setText(parts[0])
            if (parts.size >= 2) inputPort.setText(parts[1].trimEnd('/'))
        }
        // Do NOT prefill password - ask every time

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            val ip = inputIp.text.toString().trim()
            val port = inputPort.text.toString().trim()
            val serverAdminPass = inputServerAdminPass.text.toString().trim()
            if (ip.isBlank()) {
                Toast.makeText(this, "Enter the server IP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = if (port.isBlank()) "http://$ip:3001" else if (port == "443") "https://$ip" else "http://$ip:$port"

            val btn = findViewById<Button>(R.id.btnConnect)
            btn.isEnabled = false
            Toast.makeText(this, "Checking connection…", Toast.LENGTH_SHORT).show()
            scope.launch {
                // Verify the password BEFORE saving — a wrong one must not "connect".
                when (withContext(Dispatchers.IO) { testConnection(url, serverAdminPass) }) {
                    "ok" -> {
                        ServerConfig.saveConnect(this@ConnectActivity, url)
                        // Do NOT save password - ask every time
                        Toast.makeText(this@ConnectActivity, "Connected to $url", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@ConnectActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        finish()
                    }
                    "auth" -> {
                        btn.isEnabled = true
                        inputServerAdminPass.error = "Wrong admin password"
                        Toast.makeText(this@ConnectActivity, "❌ Wrong admin password", Toast.LENGTH_LONG).show()
                    }
                    "blocked" -> {
                        btn.isEnabled = true
                        Toast.makeText(this@ConnectActivity, "🚫 Device permanently blocked. Unlock from Render dashboard to continue.", Toast.LENGTH_LONG).show()
                        inputServerAdminPass.isEnabled = false
                        btn.isEnabled = false
                        // Stay blocked forever - only manual unlock from dashboard
                    }
                    else -> {
                        btn.isEnabled = true
                        Toast.makeText(this@ConnectActivity, "❌ Cannot reach $url", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /** Probe the server with the given password. Returns "ok" | "auth" | "network" | "blocked". */
    private fun testConnection(url: String, pass: String): String = try {
        val deviceId = ServerConfig.loadDeviceId(this)
        val req = Request.Builder()
            .url("$url/api/config")
            .header("X-Admin-Password", pass)
            .header("X-Device-ID", deviceId)
            .build()
        client.newCall(req).execute().use { resp ->
            when {
                resp.isSuccessful -> {
                    val body = resp.body?.string() ?: ""
                    val json = org.json.JSONObject(body)
                    if (json.optBoolean("deviceBlocked", false)) "blocked"
                    else if (json.optBoolean("authError", false)) "auth"
                    else "ok"
                }
                resp.code == 401 || resp.code == 403 -> "auth"
                else -> "network"
            }
        }
    } catch (e: Exception) { "network" }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
