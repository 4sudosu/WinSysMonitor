package com.wsmonitor.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ConnectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.connect_title)

        val inputIp = findViewById<EditText>(R.id.inputIp)
        val inputPort = findViewById<EditText>(R.id.inputPort)
        val inputServerAdminPass = findViewById<EditText>(R.id.inputServerAdminPass)

        // prefill from saved connect config
        val cfg = ServerConfig.load(this)
        if (cfg.mode == "connect" && cfg.url.isNotBlank()) {
            val parts = cfg.url.removePrefix("http://").removePrefix("https://").split(":")
            if (parts.size >= 1) inputIp.setText(parts[0])
            if (parts.size >= 2) inputPort.setText(parts[1].trimEnd('/'))
        }
        // prefill server admin password from prefs
        inputServerAdminPass.setText(AppPrefs.serverAdminPassword(this))

        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            val ip = inputIp.text.toString().trim()
            val port = inputPort.text.toString().trim()
            val serverAdminPass = inputServerAdminPass.text.toString().trim()
            if (ip.isBlank()) {
                Toast.makeText(this, "Enter the server IP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = if (port.isBlank()) "http://$ip:3001" else if (port == "443") "https://$ip" else "http://$ip:$port"
            ServerConfig.saveConnect(this, url)
            // Save server admin password (for X-Admin-Password header)
            if (serverAdminPass.isNotBlank()) AppPrefs.saveServerAdminPassword(this, serverAdminPass)
            Toast.makeText(this, "Connected to $url", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}