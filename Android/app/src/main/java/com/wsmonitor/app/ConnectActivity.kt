package com.wsmonitor.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private lateinit var inputIp: EditText
    private lateinit var inputPort: EditText
    private lateinit var inputServerAdminPass: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnUnlockCheck: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.connect_title)

        inputIp = findViewById(R.id.inputIp)
        inputPort = findViewById(R.id.inputPort)
        inputServerAdminPass = findViewById(R.id.inputServerAdminPass)
        btnConnect = findViewById(R.id.btnConnect)
        btnUnlockCheck = findViewById(R.id.btnUnlockCheck)

        // prefill from saved connect config (URL/IP only — password is never stored)
        val cfg = ServerConfig.load(this)
        if (cfg.mode == "connect" && cfg.url.isNotBlank()) {
            val parts = cfg.url.removePrefix("http://").removePrefix("https://").split(":")
            if (parts.size >= 1) inputIp.setText(parts[0])
            if (parts.size >= 2) inputPort.setText(parts[1].trimEnd('/'))
        }

        if (ServerConfig.isLocallyBlocked(this)) applyBlockedUi()

        btnUnlockCheck.setOnClickListener { probeUnlockStatus() }

        btnConnect.setOnClickListener {
            val ip = inputIp.text.toString().trim()
            val port = inputPort.text.toString().trim()
            val pass = inputServerAdminPass.text.toString().trim()
            if (ip.isBlank()) {
                Toast.makeText(this, "Enter the server IP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pass.isBlank()) {
                Toast.makeText(this, "Enter the server password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = when {
                port.isBlank() -> "http://$ip:3001"
                port == "443" -> "https://$ip"
                else -> "http://$ip:$port"
            }

            btnConnect.isEnabled = false
            Toast.makeText(this, "Checking connection…", Toast.LENGTH_SHORT).show()
            scope.launch {
                when (withContext(Dispatchers.IO) { testConnection(url, pass) }) {
                    "ok" -> {
                        ServerConfig.clearConnectFailures(this@ConnectActivity)
                        ServerConfig.saveConnect(this@ConnectActivity, url)
                        startActivity(Intent(this@ConnectActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        finish()
                    }
                    "auth" -> {
                        val attempts = ServerConfig.recordConnectFailure(this@ConnectActivity)
                        runOnUiThread {
                            btnConnect.isEnabled = true
                            if (attempts >= ServerConfig.MAX_CONNECT_ATTEMPTS) {
                                applyBlockedUi()
                                Toast.makeText(this@ConnectActivity,
                                    "🚫 Device blocked after $attempts failed attempts. Unlock via dashboard.",
                                    Toast.LENGTH_LONG).show()
                            } else {
                                inputServerAdminPass.error =
                                    "Wrong password ($attempts/${ServerConfig.MAX_CONNECT_ATTEMPTS})"
                                Toast.makeText(this@ConnectActivity,
                                    "❌ Wrong password ($attempts/${ServerConfig.MAX_CONNECT_ATTEMPTS})",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    "blocked" -> {
                        ServerConfig.markLocallyBlocked(this@ConnectActivity)
                        runOnUiThread { applyBlockedUi() }
                    }
                    else -> runOnUiThread {
                        btnConnect.isEnabled = true
                        Toast.makeText(this@ConnectActivity, "❌ Cannot reach $url", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /** Disables all inputs; only the unlock-status check remains available. */
    private fun applyBlockedUi() {
        inputServerAdminPass.isEnabled = false
        inputServerAdminPass.error = null
        btnConnect.isEnabled = false
        btnUnlockCheck.visibility = View.VISIBLE
        Toast.makeText(this,
            "🚫 Device blocked. Unlock from the WinSysMonitor dashboard, then tap Check status.",
            Toast.LENGTH_LONG).show()
    }

    private fun restoreUiAfterUnlock() {
        inputServerAdminPass.isEnabled = true
        btnConnect.isEnabled = true
        btnUnlockCheck.visibility = View.GONE
    }

    /** Asks the server (device ID only, no password) whether this device is still blocked. */
    private fun probeUnlockStatus() {
        val ip = inputIp.text.toString().trim()
        val port = inputPort.text.toString().trim()
        if (ip.isBlank()) {
            Toast.makeText(this, "Enter the server IP first", Toast.LENGTH_SHORT).show()
            return
        }
        val url = when {
            port.isBlank() -> "http://$ip:3001"
            port == "443" -> "https://$ip"
            else -> "http://$ip:$port"
        }
        btnUnlockCheck.isEnabled = false
        scope.launch {
            val stillBlocked = withContext(Dispatchers.IO) { checkBlockStatus(url) }
            runOnUiThread {
                btnUnlockCheck.isEnabled = true
                if (stillBlocked == null) {
                    Toast.makeText(this@ConnectActivity, "❌ Cannot reach $url", Toast.LENGTH_LONG).show()
                } else if (stillBlocked) {
                    Toast.makeText(this@ConnectActivity,
                        "🚫 Still blocked. Ask the admin to unlock this device.", Toast.LENGTH_LONG).show()
                } else {
                    ServerConfig.clearConnectFailures(this@ConnectActivity)
                    restoreUiAfterUnlock()
                    Toast.makeText(this@ConnectActivity,
                        "✅ Unlocked. Enter your password to connect.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Returns null on network error, otherwise current blocked state. */
    private fun checkBlockStatus(baseUrl: String): Boolean? = try {
        val req = Request.Builder()
            .url("$baseUrl/api/config")
            .header("X-Device-ID", ServerConfig.loadDeviceId(this))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else JSONObject(resp.body?.string() ?: "").optBoolean("deviceBlocked", false)
        }
    } catch (e: Exception) { null }

    /** Probe the server with the given password. Returns "ok" | "auth" | "network" | "blocked". */
    private fun testConnection(url: String, pass: String): String = try {
        val req = Request.Builder()
            .url("$url/api/config")
            .header("X-Admin-Password", pass)
            .header("X-Device-ID", ServerConfig.loadDeviceId(this))
            .build()
        client.newCall(req).execute().use { resp ->
            when {
                resp.code == 423 -> "blocked"
                resp.isSuccessful || resp.code == 401 || resp.code == 403 -> {
                    val json = runCatching { JSONObject(resp.body?.string() ?: "") }.getOrNull()
                    when {
                        json?.optBoolean("deviceBlocked", false) == true -> "blocked"
                        json?.optBoolean("authError", false) == true -> "auth"
                        resp.isSuccessful -> "ok"
                        else -> "auth"
                    }
                }
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
