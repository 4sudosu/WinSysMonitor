package com.wsmonitor.app

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Device detail "tab" — live screenshot viewer for one machine.
 * Auto-refresh (3s/5s/10s), save / copy / share of the captured screenshot.
 * Opened from the Devices list by tapping a device card or its serial.
 */
class DeviceDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DEVICE = "device"
    }

    private lateinit var tvTitle: TextView
    private lateinit var tvOnline: TextView
    private lateinit var tvInfo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var imgScreenshot: ImageView
    private lateinit var tvPlaceholder: TextView
    private lateinit var btnSave: Button
    private lateinit var btnCopy: Button
    private lateinit var btnShare: Button

    private val intervals = intArrayOf(0, 3, 5, 10)
    private val chips = mutableListOf<TextView>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var refreshJob: Job? = null
    private var base: String? = null
    private var machine = ""
    private var hostname = ""
    private var currentBytes: ByteArray? = null
    private var capturing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_detail)

        tvTitle = findViewById(R.id.tvTitle)
        tvOnline = findViewById(R.id.tvOnline)
        tvInfo = findViewById(R.id.tvInfo)
        tvStatus = findViewById(R.id.tvStatus)
        chipRow = findViewById(R.id.chipRow)
        imgScreenshot = findViewById(R.id.imgScreenshot)
        imgScreenshot.maxHeight = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        tvPlaceholder = findViewById(R.id.tvPlaceholder)
        btnSave = findViewById(R.id.btnSave)
        btnCopy = findViewById(R.id.btnCopy)
        btnShare = findViewById(R.id.btnShare)

        val device = intent.getStringExtra(EXTRA_DEVICE)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        machine = device?.optString("machineName", "") ?: ""
        hostname = device?.optString("hostname", machine).orEmpty().ifBlank { machine }
        base = ServerConfig.dashboardUrl(this)?.trimEnd('/')

        if (machine.isBlank() || base == null) {
            Toast.makeText(this, "Missing device or server config", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvTitle.text = "📷 $hostname"
        tvOnline.text = if (device?.optBoolean("online", false) == true) "● online" else "○ offline"
        tvOnline.setTextColor(if (device?.optBoolean("online", false) == true)
            getColor(com.wsmonitor.app.R.color.online_green)
        else getColor(com.wsmonitor.app.R.color.text_muted))

        tvInfo.text = buildString {
            device?.optString("model", "")?.takeIf { it.isNotBlank() }?.let { appendLine("🖥 $it") }
            device?.optString("ip", "")?.takeIf { it.isNotBlank() }?.let { append("IP: $it   ") }
            device?.optString("serial", "")?.takeIf { it.isNotBlank() }?.let { append("S/N: $it") }
            if (isNotEmpty() && !endsWith("\n")) append("\n")
            device?.optString("os", "")?.takeIf { it.isNotBlank() }?.let { appendLine("OS: $it") }
            device?.optString("user", "")?.takeIf { it.isNotBlank() }?.let { appendLine("User: $it") }
            device?.optString("version", "")?.takeIf { it.isNotBlank() }?.let { append("Agent: v$it") }
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        applyAccent(AppPrefs.accentColor(this))
        setupChips()
        btnSave.setOnClickListener { saveImage() }
        btnCopy.setOnClickListener { copyImage() }
        btnShare.setOnClickListener { shareImage() }
        findViewById<Button>(R.id.btnCapture).setOnClickListener { captureNow() }
    }

    private fun applyAccent(accent: Int) {
        findViewById<View>(R.id.header).background = null
        findViewById<View>(R.id.header).setBackgroundColor(accent)
        btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        btnCopy.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        btnShare.backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        findViewById<Button>(R.id.btnCapture).backgroundTintList =
            android.content.res.ColorStateList.valueOf(accent)
    }

    // ── auto-refresh chips ────────────────────────────────────────────────
    private fun setupChips() {
        chipRow.removeAllViews()
        chips.clear()
        val labels = arrayOf("Off", "3s", "5s", "10s")
        val accent = AppPrefs.accentColor(this)
        for (i in intervals.indices) {
            val chip = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    marginEnd = dp(8)
                }
                gravity = android.view.Gravity.CENTER
                text = labels[i]
                textSize = 13f
                setOnClickListener { selectInterval(i) }
            }
            chipRow.addView(chip)
            chips.add(chip)
        }
        refreshChips(0)
    }

    private fun refreshChips(selected: Int) {
        val accent = AppPrefs.accentColor(this)
        for (i in chips.indices) {
            val chip = chips[i]
            val active = i == selected
            chip.setBackgroundColor(if (active) accent else getColor(com.wsmonitor.app.R.color.card_bg))
            chip.setTextColor(if (active) Color.WHITE else getColor(com.wsmonitor.app.R.color.text_secondary))
        }
    }

    private fun selectInterval(index: Int) {
        refreshChips(index)
        val seconds = intervals[index]
        refreshJob?.cancel()
        if (seconds <= 0) {
            setStatus("Auto-refresh off")
            return
        }
        setStatus("Auto-refresh every ${seconds}s")
        refreshJob = scope.launch {
            while (isActive) {
                delay(seconds * 1000L)
                captureNow()
            }
        }
    }

    // ── capture ───────────────────────────────────────────────────────────
    private fun captureNow() {
        if (capturing) return
        capturing = true
        setStatus("📸 Capturing…")
        scope.launch {
            val result = withContext(Dispatchers.IO) { postScreenshot() }
            capturing = false
            if (result == null) {
                setStatus("❌ Could not reach the server")
                return@launch
            }
            if (!result.optBoolean("success", false)) {
                setStatus("❌ ${result.optString("error", "Capture failed")}")
                return@launch
            }
            val b64 = result.optString("image", "")
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp == null) {
                setStatus("❌ Invalid image received")
                return@launch
            }
            currentBytes = bytes
            imgScreenshot.setImageBitmap(bmp)
            imgScreenshot.visibility = View.VISIBLE
            tvPlaceholder.visibility = View.GONE
            val at = result.optString("at", "")
            val time = if (at.length >= 19) at.substring(11, 19) else "now"
            setStatus("📸 Captured $time")
        }
    }

    private fun postScreenshot(): JSONObject? = try {
        val body = "{}".toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("${base}/api/monitor/${java.net.URLEncoder.encode(machine, "UTF-8")}/screenshot")
            .header("X-Admin-Password", AppPrefs.serverAdminPassword(this))
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JSONObject(resp.body?.string() ?: "{}")
        }
    } catch (e: Exception) { null }

    // ── actions ───────────────────────────────────────────────────────────
    private fun saveImage() {
        val bytes = currentBytes ?: run { setStatus("No screenshot yet — capture first"); return }
        try {
            val name = "wsm_${machine}_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WinSysMonitor")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(this, "Saved to Pictures/WinSysMonitor", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not save", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyImage() {
        val bytes = currentBytes ?: run { setStatus("No screenshot yet — capture first"); return }
        try {
            val uri = writeCacheImage(bytes)
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData(ClipDescription("Screenshot", arrayOf("image/png")),
                ClipData.Item(uri))
            cm.setPrimaryClip(clip)
            Toast.makeText(this, "Image copied — paste into any app", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage() {
        val bytes = currentBytes ?: run { setStatus("No screenshot yet — capture first"); return }
        try {
            val uri = writeCacheImage(bytes)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share screenshot"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeCacheImage(bytes: ByteArray): android.net.Uri {
        val dir = File(cacheDir, "screenshots").apply { mkdirs() }
        val file = File(dir, "wsm_${machine}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { it.write(bytes) }
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    private fun setStatus(text: String) {
        tvStatus.text = text
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        refreshJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}