package com.wsmonitor.app

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var devicesPanel: View
    private lateinit var settingsPanel: ScrollView
    private lateinit var themeRow: GridLayout
    private lateinit var iconRow: GridLayout
    private lateinit var appIconRow: GridLayout
    private lateinit var switchNotif: Switch
    private lateinit var tvTone: TextView
    private lateinit var tabDevices: TextView
    private lateinit var tabSettings: TextView

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val adapter = DeviceAdapter(onCapture = { machine -> promptPassword(machine) })

    private var currentBase: String? = null
    private var activeTab = 0
    private var lastStatus = ""

    private val toneLabels = mapOf(
        "system" to "System default",
        "chime" to "Chime",
        "alert" to "Alert",
        "ding" to "Ding",
        "soft" to "Soft",
        "custom" to "Custom sound"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        tvStatus = findViewById(R.id.tvStatus)
        tvCount = findViewById(R.id.tvCount)
        tvEmpty = findViewById(R.id.tvEmpty)
        recycler = findViewById(R.id.recycler)
        devicesPanel = findViewById(R.id.devicesPanel)
        settingsPanel = findViewById(R.id.settingsPanel)
        themeRow = findViewById(R.id.themeRow)
        iconRow = findViewById(R.id.iconRow)
        appIconRow = findViewById(R.id.appIconRow)
        switchNotif = findViewById(R.id.switchNotif)
        tvTone = findViewById(R.id.tvTone)
        tabDevices = findViewById(R.id.tabDevices)
        tabSettings = findViewById(R.id.tabSettings)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        tabDevices.setOnClickListener { setTab(0) }
        tabSettings.setOnClickListener { setTab(1) }

        setupDeveloperLinks()
        setupSettings()

        requestNotificationPermission()
        applyTheme(AppPrefs.accentColor(this))
        setTab(0)

        loadDashboard()
    }

    private fun loadDashboard() {
        val url = ServerConfig.dashboardUrl(this)
        if (url.isNullOrBlank()) {
            startActivity(Intent(this, ConnectActivity::class.java))
            return
        }
        val base = url.trimEnd('/')
        if (currentBase == base) return
        currentBase = base
        lastStatus = if (ServerConfig.load(this).mode == "host")
            "Server on port ${ServerConfig.load(this).port} · LAN: ${lanIp() ?: "unknown"}"
        else "Connected to $base"
        updateStatusLine()
        startPolling(base)
        AgentEventService.start(this, base)
    }

    // ── status ────────────────────────────────────────────────────────────
    private fun updateStatusLine() {
        tvStatus.text = if (NodeService.isRunning && ServerConfig.load(this).mode == "host")
            "$lastStatus · running" else lastStatus
    }

    private fun lanIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList().mapNotNull { ni ->
            ni.inetAddresses.toList().firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
        }.firstOrNull()?.hostAddress
    } catch (e: Exception) { null }

// ── tabs ──────────────────────────────────────────────────────────────
    private fun setTab(i: Int) {
        activeTab = i
        devicesPanel.visibility = if (i == 0) View.VISIBLE else View.GONE
        settingsPanel.visibility = if (i == 1) View.VISIBLE else View.GONE
        val accentState = android.content.res.ColorStateList.valueOf(AppPrefs.accentColor(this))
        val unselected = ContextCompat.getColorStateList(this, R.color.card_bg)
        tabDevices.backgroundTintList = if (i == 0) accentState else unselected
        tabSettings.backgroundTintList = if (i == 1) accentState else unselected
        if (i == 0) refreshOnce()
    }

    // ── devices polling ───────────────────────────────────────────────────
    private fun startPolling(base: String) {
        scope.launch {
            while (isActive) {
                val data = withContext(Dispatchers.IO) { fetchAgents(base) }
                if (data != null) {
                    adapter.submit(data)
                    tvCount.text = "${data.size} connected"
                    tvEmpty.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    adapter.submit(emptyList())
                    tvCount.text = "unreachable"
                    tvEmpty.text = "Cannot reach the server.\nCheck that it is running."
                    tvEmpty.visibility = View.VISIBLE
                }
                delay(3000)
            }
        }
    }

    private fun refreshOnce() {
        val base = currentBase ?: return
        scope.launch {
            val data = withContext(Dispatchers.IO) { fetchAgents(base) }
            if (data != null) adapter.submit(data)
        }
    }

    private fun fetchAgents(base: String): List<JSONObject>? = try {
        val req = Request.Builder().url("$base/api/agents").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val arr = JSONArray(resp.body?.string() ?: "[]")
            (0 until arr.length()).map { arr.getJSONObject(it) }
        }
    } catch (e: Exception) { null }

    // ── capture ───────────────────────────────────────────────────────────
    private fun promptPassword(machine: String) {
        val input = EditText(this).apply {
            hint = "Admin password"
            setText(".\\itdtpadmin")
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("📸 Capture $machine")
            .setMessage("Enter the server admin password")
            .setView(input)
            .setPositiveButton("Capture") { _, _ -> requestScreenshot(machine, input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestScreenshot(machine: String, password: String) {
        val base = currentBase ?: return
        Toast.makeText(this, "Capturing…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = withContext(Dispatchers.IO) { postScreenshot(base, machine, password) }
            if (result == null) {
                Toast.makeText(this@MainActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val ok = result.optBoolean("success", false)
            if (!ok) {
                Toast.makeText(this@MainActivity, "Failed: ${result.optString("error", "unknown")}",
                    Toast.LENGTH_LONG).show()
                return@launch
            }
            val b64 = result.optString("image", "")
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp == null) {
                Toast.makeText(this@MainActivity, "Invalid image", Toast.LENGTH_SHORT).show()
                return@launch
            }
            showScreenshotDialog(bmp, bytes, machine)
        }
    }

    private fun postScreenshot(base: String, machine: String, password: String): JSONObject? = try {
        val body = JSONObject().put("password", password).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$base/api/monitor/${java.net.URLEncoder.encode(machine, "UTF-8")}/screenshot")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JSONObject(resp.body?.string() ?: "{}")
        }
    } catch (e: Exception) { null }

    private fun showScreenshotDialog(bmp: Bitmap, bytes: ByteArray, machine: String) {
        val iv = ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
            maxHeight = (resources.displayMetrics.heightPixels * 0.7f).toInt()
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val pad = (18 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(iv)
        }
        AlertDialog.Builder(this)
            .setTitle("📸 $machine")
            .setView(container)
            .setPositiveButton("Save & Copy") { _, _ -> saveAndCopy(bytes, machine) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun saveAndCopy(bytes: ByteArray, machine: String) {
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
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData(ClipDescription("Screenshot", arrayOf("image/png")),
                    ClipData.Item(uri))
                cm.setPrimaryClip(clip)
                Toast.makeText(this, "Saved & copied", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not save", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── developer links ───────────────────────────────────────────────────
    private fun setupDeveloperLinks() {
        findViewById<View>(R.id.btnDevTelegram).setOnClickListener { open("https://t.me/verifiedharyanvi") }
        findViewById<View>(R.id.btnDevInstagram).setOnClickListener { open("https://www.instagram.com/4sudo.su") }
        findViewById<View>(R.id.btnDevGmail).setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:4sudo.su@gmail.com")))
            }.onFailure {
                Toast.makeText(this, "4sudo.su@gmail.com", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.btnDevGithub).setOnClickListener { open("https://github.com/4sudosu") }
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    // ── settings ──────────────────────────────────────────────────────────
    private fun setupSettings() {
        switchNotif.isChecked = AppPrefs.notifEnabled(this)
        switchNotif.setOnCheckedChangeListener { _, checked ->
            AppPrefs.saveNotifEnabled(this, checked)
            AgentEventService.applyPrefs(this)
        }
        tvTone.text = toneLabels[AppPrefs.tone(this)] ?: "System default"
        findViewById<Button>(R.id.btnTone).setOnClickListener { showTonePicker() }
        renderThemeSwatches()
        renderIconSwatches()
        renderAppIcons()
    }

    private fun renderThemeSwatches() {
        themeRow.removeAllViews()
        val colors = resources.getIntArray(R.array.theme_colors)
        val names = resources.getStringArray(R.array.theme_names)
        val sel = AppPrefs.themeIndex(this)
        for (i in colors.indices) {
            val cell = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    height = dp(52)
                    setMargins(0, 0, 0, dp(8))
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setGravity(android.view.Gravity.CENTER)
                }
                contentDescription = names[i]
                setOnClickListener {
                    AppPrefs.saveTheme(this@MainActivity, i)
                    applyTheme(colors[i])
                    renderThemeSwatches()
                }
            }
            val outer = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(46), dp(46)).apply {
                    gravity = android.view.Gravity.CENTER
                }
                background = if (i == sel) ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_ring)
                    else null
            }
            val inner = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(32), dp(32)).apply {
                    gravity = android.view.Gravity.CENTER
                }
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_swatch)
                background.setTint(colors[i])
            }
            outer.addView(inner)
            cell.addView(outer)
            themeRow.addView(cell)
        }
    }

    private fun renderAppIcons() {
        appIconRow.removeAllViews()
        val res = resources.getStringArray(R.array.app_icon_res)
        val names = resources.getStringArray(R.array.app_icon_names)
        val sel = AppPrefs.appIcon(this)
        for (i in res.indices) {
            val id = resources.getIdentifier(res[i], "mipmap", packageName)
            if (id == 0) continue
            val selected = names[i] == sel
            val cell = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    height = dp(84)
                    setMargins(0, 0, 0, dp(10))
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setGravity(android.view.Gravity.CENTER)
                }
                contentDescription = names[i]
                setOnClickListener {
                    AppPrefs.saveAppIcon(this@MainActivity, names[i])
                    setAppIcon(i)
                    renderAppIcons()
                    Toast.makeText(this@MainActivity,
                        "App icon set to ${names[i]}. Check your home screen.",
                        Toast.LENGTH_SHORT).show()
                }
            }
            val outer = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(64), dp(64)).apply {
                    gravity = android.view.Gravity.CENTER
                }
                background = if (selected) ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_ring)
                    else null
            }
            val img = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply {
                    gravity = android.view.Gravity.CENTER
                }
                setImageResource(id)
            }
            outer.addView(img)
            cell.addView(outer)
            appIconRow.addView(cell)
        }
    }

    private fun setAppIcon(iconIndex: Int) {
        val aliases = resources.getStringArray(R.array.app_icon_alias)
        val pm = packageManager
        val enabled = ComponentName(packageName, aliases[iconIndex])
        for (a in aliases) {
            val cn = ComponentName(packageName, a)
            pm.setComponentEnabledSetting(cn,
                if (cn == enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
        }
    }

    private fun renderIconSwatches() {
        iconRow.removeAllViews()
        val res = resources.getStringArray(R.array.notif_icon_res)
        val names = resources.getStringArray(R.array.notif_icon_names)
        val sel = AppPrefs.notifIcon(this)
        for (i in res.indices) {
            val id = resources.getIdentifier(res[i], "drawable", packageName)
            if (id == 0) continue
            val selected = res[i] == sel
            val cell = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    height = dp(66)
                    setMargins(0, 0, 0, dp(10))
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setGravity(android.view.Gravity.CENTER)
                }
                contentDescription = names[i]
                setOnClickListener {
                    AppPrefs.saveNotifIcon(this@MainActivity, res[i])
                    renderIconSwatches()
                    AgentEventService.applyPrefs(this@MainActivity)
                }
            }
            val outer = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(52), dp(52)).apply {
                    gravity = android.view.Gravity.CENTER
                }
                background = if (selected) ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_ring)
                    else null
            }
            val img = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(40), dp(40)).apply {
                    gravity = android.view.Gravity.CENTER
                }
                setImageResource(id)
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_swatch)
                background.setTint(ContextCompat.getColor(this@MainActivity,
                    if (selected) R.color.cyan else R.color.card_bg))
                setPadding(dp(9), dp(9), dp(9), dp(9))
            }
            outer.addView(img)
            cell.addView(outer)
            iconRow.addView(cell)
        }
    }

    private fun showTonePicker() {
        val options = arrayOf(
            "System default", "Chime", "Alert", "Ding", "Soft", "Choose custom sound…"
        )
        val values = arrayOf("system", "chime", "alert", "ding", "soft", "custom")
        val current = AppPrefs.tone(this)
        val checked = values.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Notification sound")
            .setSingleChoiceItems(options, checked) { d, which ->
                val v = values[which]
                if (v == "custom") {
                    d.dismiss()
                    openRingtonePicker()
                } else {
                    AppPrefs.saveTone(this, v)
                    tvTone.text = options[which]
                    d.dismiss()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Custom notification sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                AppPrefs.customToneUri(this@MainActivity)?.let(Uri::parse))
        }
        startActivityForResult(intent, 4001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4001 && resultCode == RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                AppPrefs.saveTone(this, "custom")
                AppPrefs.saveCustomToneUri(this, uri.toString())
            } else {
                AppPrefs.saveTone(this, "system")
                AppPrefs.saveCustomToneUri(this, null)
            }
            tvTone.text = toneLabels[AppPrefs.tone(this)] ?: "System default"
            AgentEventService.applyPrefs(this)
        }
    }

    // ── theme ─────────────────────────────────────────────────────────────
    private fun applyTheme(accent: Int) {
        val tint = android.content.res.ColorStateList.valueOf(accent)
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .backgroundTintList = tint
        tvCount.setTextColor(accent)
        findViewById<Button>(R.id.btnTone).backgroundTintList = tint
        findViewById<Button>(R.id.btnTone).setTextColor(Color.WHITE)
        if (Build.VERSION.SDK_INT >= 21) {
            window.statusBarColor = accent
        }
        setTab(activeTab)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ── lifecycle ─────────────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> { refreshOnce(); true }
            R.id.action_launcher -> {
                startActivity(Intent(this, LauncherActivity::class.java)); true
            }
            R.id.action_connect -> {
                startActivity(Intent(this, ConnectActivity::class.java)); true
            }
            R.id.action_web -> {
                currentBase?.let { open(it) }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
            )
        }
    }
}