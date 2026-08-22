package com.wsmonitor.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class UpdateActivity : AppCompatActivity() {

    private var latestVersion = "Unknown"
    private var downloadId: Long? = null
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
            val id = downloadId ?: return
            val uri = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager)
                .getUriForDownloadedFile(id) ?: return
            startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)

        latestVersion = intent.getStringExtra("latest_version") ?: "Unknown"
        val releaseUrl = intent.getStringExtra("release_url") ?: ""
        val apkUrl = intent.getStringExtra("apk_url") ?: releaseUrl
        val releaseNotes = intent.getStringExtra("release_notes")

        val currentVersion = packageManager
            .getPackageInfo(packageName, 0)
            .versionName ?: "Unknown"
        findViewById<TextView>(R.id.tvCurrentVersion).text = "Current: $currentVersion"
        findViewById<TextView>(R.id.tvLatestVersion).text = "Latest: $latestVersion"

        val notesView = findViewById<TextView>(R.id.tvReleaseNotes)
        if (releaseNotes.isNullOrBlank()) {
            notesView.visibility = android.view.View.GONE
        } else {
            notesView.text = releaseNotes
        }

        val btnUpdate = findViewById<Button>(R.id.btnUpdate)
        btnUpdate.setOnClickListener {
            downloadUpdate(apkUrl)
        }
    }

    private fun downloadUpdate(url: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("WinSysMonitor update")
                .setDescription("Downloading version $latestVersion")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "winsysmonitor-update.apk")
            downloadId = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            findViewById<Button>(R.id.btnUpdate).isEnabled = false
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(downloadReceiver)
        super.onStop()
    }

    override fun onBackPressed() = Unit
}
