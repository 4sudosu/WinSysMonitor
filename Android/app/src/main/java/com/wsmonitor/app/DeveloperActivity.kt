package com.wsmonitor.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class DeveloperActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_developer)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.dev_title)

        findViewById<android.view.View>(R.id.rowTelegram).setOnClickListener {
            open("https://t.me/verifiedharyanvi")
        }
        findViewById<android.view.View>(R.id.rowInstagram).setOnClickListener {
            open("https://www.instagram.com/4sudo.su")
        }
        findViewById<android.view.View>(R.id.rowGmail).setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:4sudo.su@gmail.com")))
            }.onFailure {
                Toast.makeText(this, "4sudo.su@gmail.com", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<android.view.View>(R.id.rowGithub).setOnClickListener {
            open("https://github.com/4sudosu")
        }
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}