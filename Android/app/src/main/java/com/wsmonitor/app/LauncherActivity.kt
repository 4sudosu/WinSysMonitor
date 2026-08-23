package com.wsmonitor.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {

    private val GITHUB_REPO = "4sudosu/WinSysMonitor"
    private lateinit var updateChecker: UpdateChecker
    private var updateChecked = false
    private var lastUpdateCheckTime: Long = 0
    private val UPDATE_CHECK_CACHE_MS = 3600000L // 1 hour

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        updateChecker = UpdateChecker(this)
        setLauncherActionsEnabled(false)
        checkForUpdates()

        updateStatus()

        findViewById<Button>(R.id.btnStartServer).setOnClickListener {
            promptStartServer()
        }
        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            startActivity(Intent(this, ConnectActivity::class.java))
        }
        findViewById<Button>(R.id.btnRunAll).setOnClickListener {
            val cfg = ServerConfig.load(this)
            val port = if (cfg.port in 1..65535) cfg.port else 3001
            ServerConfig.saveHost(this, port, "0.0.0.0")
            NodeService.start(this)
            Toast.makeText(this, "Server running on 0.0.0.0:$port", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<TextView>(R.id.tvDeveloper).setOnClickListener {
            startActivity(Intent(this, DeveloperActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenDashboard).setOnClickListener {
            val url = ServerConfig.dashboardUrl(this)
            if (url.isNullOrBlank()) {
                Toast.makeText(this, "No server configured yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    private fun checkForUpdates() {
        if (updateChecked) return
        
        // Skip if checked recently (cache)
        val now = System.currentTimeMillis()
        if (lastUpdateCheckTime > 0 && (now - lastUpdateCheckTime) < UPDATE_CHECK_CACHE_MS) {
            updateChecked = true
            setLauncherActionsEnabled(true)
            val status = findViewById<TextView>(R.id.tvStatus)
            status.text = "✓ Up to date ($GITHUB_REPO) [cached]"
            return
        }
        
        val status = findViewById<TextView>(R.id.tvStatus)
        status.text = "Checking $GITHUB_REPO for updates..."
        updateChecker.checkForUpdates(GITHUB_REPO, object : UpdateChecker.UpdateCallback {
            override fun onUpdateAvailable(info: UpdateChecker.UpdateInfo) {
                updateChecked = true
                lastUpdateCheckTime = System.currentTimeMillis()
                showUpdateScreen(info)
            }

            override fun onNoUpdate() {
                updateChecked = true
                lastUpdateCheckTime = System.currentTimeMillis()
                setLauncherActionsEnabled(true)
                status.text = "✓ Up to date ($GITHUB_REPO)"
            }

            override fun onError(error: String) {
                updateChecked = false
                setLauncherActionsEnabled(false)
                status.text = "Update check failed"
                android.app.AlertDialog.Builder(this@LauncherActivity)
                    .setTitle("Update check required")
                    .setMessage("WinSysMonitor cannot be used until the latest version is verified. Check your internet connection and try again.")
                    .setPositiveButton("Retry") { _, _ -> 
                        lastUpdateCheckTime = 0 // Reset cache on retry
                        checkForUpdates() 
                    }
                    .setNegativeButton("Exit") { _, _ -> finishAffinity() }
                    .setOnCancelListener { checkForUpdates() }
                    .show()
            }
        })
    }

    private fun showUpdateScreen(info: UpdateChecker.UpdateInfo) {
        val intent = Intent(this, UpdateActivity::class.java).apply {
            putExtra("latest_version", info.latestVersion)
            putExtra("release_url", info.releaseUrl)
            putExtra("apk_url", info.apkUrl)
            putExtra("release_notes", info.releaseNotes)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        if (!updateChecked) {
            checkForUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateChecker.shutdown()
    }

    private fun updateStatus() {
        val status = findViewById<TextView>(R.id.tvStatus)
        if (!updateChecked) {
            status.text = "Checking $GITHUB_REPO for updates..."
            return
        }
        val cfg = ServerConfig.load(this)
        status.text = when {
            NodeService.isRunning -> "🟢 Server running on port ${cfg.port}"
            cfg.mode == "host" -> "Server configured on port ${cfg.port} (start it)"
            cfg.url.isNotBlank() -> "Connected to ${cfg.url}"
            else -> "No server configured"
        }
    }

    private fun setLauncherActionsEnabled(enabled: Boolean) {
        findViewById<Button>(R.id.btnStartServer).isEnabled = enabled
        findViewById<Button>(R.id.btnConnect).isEnabled = enabled
        findViewById<Button>(R.id.btnRunAll).isEnabled = enabled
        findViewById<Button>(R.id.btnOpenDashboard).isEnabled = enabled
        findViewById<TextView>(R.id.tvDeveloper).isEnabled = enabled
        if (!enabled) {
            findViewById<TextView>(R.id.tvStatus).text = "Checking $GITHUB_REPO for updates..."
        }
    }

    private fun promptStartServer() {
        val portInput = android.widget.EditText(this).apply {
            hint = "Port"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(ServerConfig.load(this@LauncherActivity).port.toString())
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("🚀 Start Server")
            .setMessage("Enter the port to host the dashboard on. Agents on your network can connect to this phone.")
            .setView(portInput)
            .setPositiveButton("Start") { _, _ ->
                val port = portInput.text.toString().trim().toIntOrNull()
                if (port == null || port !in 1..65535) {
                    Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                ServerConfig.saveHost(this, port, "0.0.0.0")
                NodeService.start(this)
                startActivity(Intent(this, MainActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
