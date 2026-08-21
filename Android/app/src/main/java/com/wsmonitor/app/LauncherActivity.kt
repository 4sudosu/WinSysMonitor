package com.wsmonitor.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {

    companion object {
        var unlocked = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

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

        if (!unlocked) showLoginGate()
    }

    private fun showLoginGate() {
        val input = EditText(this).apply {
            hint = "Enter owner password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("🔐 Unlock WinSysMonitor")
            .setMessage("Enter the owner password to continue.")
            .setView(input)
            .setPositiveButton("Unlock", null)
            .setCancelable(false)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = input.text.toString()
                if (entered.isNotEmpty() && entered == AppPrefs.adminPassword(this)) {
                    AppPrefs.saveAdminPassword(this, entered)
                    unlocked = true
                    dialog.dismiss()
                } else {
                    input.error = "Incorrect password"
                    input.text?.clear()
                }
            }
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val status = findViewById<TextView>(R.id.tvStatus)
        val cfg = ServerConfig.load(this)
        status.text = when {
            NodeService.isRunning -> "🟢 Server running on port ${cfg.port}"
            cfg.mode == "host" -> "Server configured on port ${cfg.port} (start it)"
            cfg.url.isNotBlank() -> "Connected to ${cfg.url}"
            else -> "No server configured"
        }
    }

    private fun promptStartServer() {
        val portInput = EditText(this).apply {
            hint = "Port"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(ServerConfig.load(this@LauncherActivity).port.toString())
        }
        AlertDialog.Builder(this)
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