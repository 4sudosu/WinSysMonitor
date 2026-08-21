package com.wsmonitor.app

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Window
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

        findViewById<Button>(R.id.btnOpenDashboard).setOnClickListener {
            val url = ServerConfig.dashboardUrl(this)
            if (url.isNullOrBlank()) {
                Toast.makeText(this, "No server configured yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val open = if (NodeService.isRunning && ServerConfig.load(this).mode == "host")
                Intent(this, MainActivity::class.java)
            else
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(open)
        }

        if (!unlocked) showLoginGate()
    }

    private fun showLoginGate() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_unlock)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val input = dialog.findViewById<EditText>(R.id.etPassword)
        input.setOnEditorActionListener { _, _, _ -> unlockAttempt(dialog, input); true }

        dialog.findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            unlockAttempt(dialog, input)
        }
        dialog.findViewById<Button>(R.id.btnTelegram).setOnClickListener {
            openUrl("https://t.me/verifiedharyanvi")
        }
        dialog.findViewById<Button>(R.id.btnInstagram).setOnClickListener {
            openUrl("https://www.instagram.com/4sudo.su")
        }
        dialog.findViewById<Button>(R.id.btnGitHub).setOnClickListener {
            openUrl("https://github.com/4sudosu")
        }
        dialog.findViewById<Button>(R.id.btnGmail).setOnClickListener {
            openUrl("mailto:4sudo.su@gmail.com")
        }
        dialog.show()
    }

    private fun unlockAttempt(dialog: Dialog, input: EditText) {
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