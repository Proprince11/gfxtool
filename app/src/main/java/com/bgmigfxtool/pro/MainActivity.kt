package com.bgmigfxtool.pro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    // ── Settings state ─────────────────────────────────────────────────────
    private var gamePkg    = GfxConfig.BGMI
    private var fps        = 60
    private var quality    = 1      // 1=Smooth 2=Balanced 3=HD 4=HDR
    private var style      = 3      // 1=Classic 2=Realistic 3=Colorful 4=Soft (default Colorful)
    private var resolution = "0.75"
    private var shadow     = 0
    private var msaa       = 1
    private var ipadView   = false
    private var vulkan     = false
    private var hdr        = false

    private lateinit var prefs: SharedPreferences
    private val REQ_CODE = 1

    // ── Shizuku listeners ──────────────────────────────────────────────────
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            toast("✅ Shizuku permission granted")
            Shell.bind()
            updateStatus()
        } else {
            toast("❌ Permission denied — open Shizuku app and try again")
        }
    }

    private val binderListener = Shizuku.OnBinderReceivedListener {
        AppLog.log("SHIZUKU", "Binder received")
        if (shizukuOk()) {
            Shell.bind()
            runOnUiThread { updateStatus() }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        AppLog.log("SHIZUKU", "Binder dead")
        runOnUiThread { updateStatus() }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("gfx_v3", MODE_PRIVATE)
        loadSavedSettings()

        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        if (shizukuOk()) Shell.bind()
        updateStatus()
        startConnectionPoller()
        setupUI()
        syncUiToState() // restore UI controls to saved values
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    // ── UI wiring ──────────────────────────────────────────────────────────
    private fun setupUI() {

        // Connect
        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            if (!isShizukuInstalled()) {
                toast("Install Shizuku from Play Store first")
                return@setOnClickListener
            }
            if (shizukuOk()) {
                Shell.bind()
                toast("Connecting service…")
                updateStatus()
            } else {
                try { Shizuku.requestPermission(REQ_CODE) }
                catch (_: Exception) { toast("Open Shizuku app and start it first") }
            }
        }

        // Game
        findViewById<RadioGroup>(R.id.rgGame).setOnCheckedChangeListener { _, id ->
            gamePkg = if (id == R.id.rbBgmi) GfxConfig.BGMI else GfxConfig.PUBG
            save("pkg", gamePkg)
        }

        // FPS
        findViewById<RadioGroup>(R.id.rgFps).setOnCheckedChangeListener { _, id ->
            fps = when (id) {
                R.id.rbFps30 -> 30; R.id.rbFps40 -> 40
                R.id.rbFps90 -> 90; R.id.rbFps120 -> 120
                else -> 60
            }
            save("fps", fps)
        }

        // Graphics quality
        findViewById<RadioGroup>(R.id.rgGraphics).setOnCheckedChangeListener { _, id ->
            quality = when (id) {
                R.id.rbBalanced -> 2; R.id.rbHD -> 3; R.id.rbHDR -> 4
                else -> 1
            }
            save("quality", quality)
            // Auto-enable HDR switch when HDR quality is selected
            if (quality == 4 && !hdr) {
                hdr = true
                save("hdr", 1)
                findViewById<Switch>(R.id.swHdr).isChecked = true
            }
        }

        // Style
        findViewById<RadioGroup>(R.id.rgStyle).setOnCheckedChangeListener { _, id ->
            style = when (id) {
                R.id.rbClassic -> 1; R.id.rbRealistic -> 2; R.id.rbSoft -> 4
                else -> 3 // Colorful default
            }
            save("style", style)
        }

        // Resolution
        findViewById<RadioGroup>(R.id.rgRes).setOnCheckedChangeListener { _, id ->
            resolution = when (id) {
                R.id.rbRes540 -> "0.5625"; R.id.rbRes1080 -> "1.0"
                else -> "0.75"
            }
            save("res", resolution)
        }

        // Shadow
        findViewById<RadioGroup>(R.id.rgShadow).setOnCheckedChangeListener { _, id ->
            shadow = when (id) {
                R.id.rbShadowLow -> 1; R.id.rbShadowMed -> 2; R.id.rbShadowHigh -> 3
                else -> 0
            }
            save("shadow", shadow)
        }

        // Switches
        findViewById<Switch>(R.id.swHdr).setOnCheckedChangeListener { _, c ->
            hdr = c
            save("hdr", if (c) 1 else 0)
            if (c && quality < 4) {
                toast("💡 Tip: Set quality to HDR for best results")
            }
        }
        findViewById<Switch>(R.id.swVulkan).setOnCheckedChangeListener { _, c ->
            vulkan = c; save("vulkan", if (c) 1 else 0)
        }
        findViewById<Switch>(R.id.swIpad).setOnCheckedChangeListener { _, c ->
            ipadView = c; save("ipad", if (c) 1 else 0)
        }
        findViewById<Switch>(R.id.swMsaa).setOnCheckedChangeListener { _, c ->
            msaa = if (c) 4 else 1; save("msaa", msaa)
        }

        // ── Preset buttons ─────────────────────────────────────────────────
        // 🏆 Competitive: Smooth + Colorful + 90fps + 720p + no shadows + no HDR
        findViewById<Button>(R.id.btnPresetCompetitive).setOnClickListener {
            fps = 90; quality = 1; style = 3; resolution = "0.75"; shadow = 0
            msaa = 1; ipadView = false; vulkan = true; hdr = false
            saveAllSettings()
            syncUiToState()
            toast("🏆 Competitive preset applied — tap APPLY to write")
        }

        // 🌈 HDR Colorful: your exact working config restored
        findViewById<Button>(R.id.btnPresetHdr).setOnClickListener {
            fps = 60; quality = 4; style = 3; resolution = "0.75"; shadow = 0
            msaa = 1; ipadView = false; vulkan = false; hdr = true
            saveAllSettings()
            syncUiToState()
            toast("🌈 HDR Colorful preset — your Poco M7 Pro optimal config!")
        }

        // ⚖️ Balanced: Balanced + Colorful + 60fps + 720p
        findViewById<Button>(R.id.btnPresetBalanced).setOnClickListener {
            fps = 60; quality = 2; style = 3; resolution = "0.75"; shadow = 0
            msaa = 1; ipadView = false; vulkan = false; hdr = false
            saveAllSettings()
            syncUiToState()
            toast("⚖️ Balanced preset — tap APPLY to write")
        }

        // 🔋 Battery: Smooth + Classic + 40fps + 540p
        findViewById<Button>(R.id.btnPresetBattery).setOnClickListener {
            fps = 40; quality = 1; style = 1; resolution = "0.5625"; shadow = 0
            msaa = 1; ipadView = false; vulkan = false; hdr = false
            saveAllSettings()
            syncUiToState()
            toast("🔋 Battery saver preset — tap APPLY to write")
        }

        // Apply
        findViewById<Button>(R.id.btnApply).setOnClickListener { applyConfig() }

        // Boost
        findViewById<Button>(R.id.btnBoost).setOnClickListener { boost() }

        // Log
        findViewById<Button>(R.id.btnLog).setOnClickListener { showLog() }
    }

    // ── Apply all three files ──────────────────────────────────────────────
    private fun applyConfig() {
        if (!Shell.isShizukuGranted()) {
            toast("⚠️ Connect Shizuku first")
            return
        }

        // Show loading dialog
        val progress = AlertDialog.Builder(this)
            .setMessage("⚡ Applying settings…\n\nWriting UserCustom.ini, Engine.ini and binary save…")
            .setCancelable(false)
            .create()
        progress.show()

        AppLog.log("APPLY", "FPS=$fps Q=$quality Style=$style Res=$resolution HDR=$hdr Vulkan=$vulkan")

        Thread {
            try {
                // Verify shell elevation
                val (_, uid, _) = Shell.exec("id")
                AppLog.log("SHELL", "id=$uid")
                if (!uid.contains("uid=2000") && !uid.contains("uid=0")) {
                    runOnUiThread {
                        progress.dismiss()
                        toast("❌ Shell not elevated. Reconnect Shizuku.")
                    }
                    return@Thread
                }

                // Check game installed
                val (pmCode, pmOut, _) = Shell.exec("pm path $gamePkg")
                if (pmCode != 0 || pmOut.isBlank()) {
                    runOnUiThread {
                        progress.dismiss()
                        toast("❌ ${if (gamePkg == GfxConfig.BGMI) "BGMI" else "PUBG"} not installed")
                    }
                    return@Thread
                }

                // Build settings object
                val settings = FileWriter.Settings(
                    pkg          = gamePkg,
                    fps          = fps,
                    quality      = quality,
                    style        = style,
                    resolution   = resolution,
                    shadow       = shadow,
                    msaa         = msaa,
                    ipadView     = ipadView,
                    vulkan       = vulkan,
                    hdr          = hdr,
                    enemyLodBias = if (quality <= 2) 1 else 0,
                    viewDistance = if (quality <= 2) 0.85f else 1.0f,
                    particleRate = 0.5f
                )

                // Write all 3 files
                val summary = FileWriter.applyAll(this@MainActivity, settings)
                AppLog.log("RESULT", summary)

                runOnUiThread {
                    progress.dismiss()
                    val success = summary.contains("✅")
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(if (success) "✅ Settings Applied!" else "⚠️ Partial Success")
                        .setMessage(
                            buildString {
                                appendLine("FPS: ${fps}fps | Quality: ${GfxConfig.qualityLabel(quality)}")
                                appendLine("Style: ${styleLabel(style)} | Res: ${resLabel(resolution)}")
                                appendLine("HDR: ${if (hdr) "Enabled ✅" else "Off"} | Vulkan: ${if (vulkan) "On" else "Off"}")
                                appendLine("")
                                appendLine(summary)
                                if (success) appendLine("\n🎮 Launch BGMI now!")
                            }
                        )
                        .setPositiveButton("Launch BGMI") { _, _ ->
                            try {
                                packageManager.getLaunchIntentForPackage(gamePkg)
                                    ?.let { startActivity(it) }
                                    ?: toast("Could not launch game")
                            } catch (e: Exception) {
                                toast("Launch failed: ${e.message}")
                            }
                        }
                        .setNegativeButton("Done", null)
                        .setNeutralButton("Debug Log") { _, _ -> showLog() }
                        .show()
                }

            } catch (e: Exception) {
                AppLog.log("CRASH", "Unhandled: ${e.message}")
                runOnUiThread {
                    progress.dismiss()
                    toast("❌ Unexpected error — tap Debug Log")
                }
            }
        }.start()
    }

    // ── Game booster ───────────────────────────────────────────────────────
    private fun boost() {
        if (!Shell.isShizukuGranted()) {
            toast("⚠️ Connect Shizuku first")
            return
        }
        Thread {
            try {
                val (_, out, _) = Shell.exec("pm list packages -3 | cut -d: -f2")
                val skip = setOf(
                    "com.android.systemui",
                    "moe.shizuku.privileged.api",
                    "com.bgmigfxtool.pro",
                    gamePkg
                )
                val pkgs = out.lines().map { it.trim() }.filter { it.isNotEmpty() && it !in skip }
                if (pkgs.isNotEmpty()) {
                    Shell.exec(pkgs.joinToString(";") { "am force-stop $it" })
                }
                Shell.exec("pm trim-caches 999G")
                runOnUiThread {
                    toast("🚀 Killed ${pkgs.size} background apps, cache cleared!")
                }
            } catch (e: Exception) {
                AppLog.log("BOOST", "Error: ${e.message}")
            }
        }.start()
    }

    // ── Status ─────────────────────────────────────────────────────────────
    private fun updateStatus() {
        val tv = findViewById<TextView>(R.id.tvStatus)
        tv.text = when {
            Shell.connected -> "🟢 Service connected — ready to apply!"
            shizukuOk()     -> "🟢 Shizuku granted — direct mode ready"
            isShizukuInstalled() -> "🟠 Tap Connect to link Shizuku"
            else            -> "🔴 Install Shizuku from Play Store"
        }
    }

    private var pollerRunning = false
    private fun startConnectionPoller() {
        if (pollerRunning) return
        pollerRunning = true
        val h = Handler(Looper.getMainLooper())
        val r = object : Runnable {
            override fun run() {
                updateStatus()
                // Shell.bind() is quick — but still keep off main thread for safety
                if (!Shell.connected && shizukuOk()) {
                    Thread { Shell.bind() }.start()
                }
                if (!Shell.connected) h.postDelayed(this, 2000) else pollerRunning = false
            }
        }
        h.postDelayed(r, 2000)
    }

    // ── Debug log ──────────────────────────────────────────────────────────
    private fun showLog() {
        val log = AppLog.getAll().ifEmpty { "(no log yet — tap Apply first)" }
        AlertDialog.Builder(this)
            .setTitle("Debug Log")
            .setMessage(log)
            .setPositiveButton("Copy") { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("GFX Log", log))
                toast("Copied")
            }
            .setNegativeButton("Clear") { _, _ -> AppLog.clear() }
            .setNeutralButton("Close", null)
            .show()
    }

    // ── Persistence ────────────────────────────────────────────────────────
    private fun loadSavedSettings() {
        gamePkg    = prefs.getString("pkg", GfxConfig.BGMI) ?: GfxConfig.BGMI
        fps        = prefs.getInt("fps", 60)
        quality    = prefs.getInt("quality", 1)
        style      = prefs.getInt("style", 3)
        resolution = prefs.getString("res", "0.75") ?: "0.75"
        shadow     = prefs.getInt("shadow", 0)
        msaa       = prefs.getInt("msaa", 1)
        ipadView   = prefs.getInt("ipad", 0) == 1
        vulkan     = prefs.getInt("vulkan", 0) == 1
        hdr        = prefs.getInt("hdr", 0) == 1
    }

    private fun saveAllSettings() {
        prefs.edit()
            .putString("pkg", gamePkg)
            .putInt("fps", fps)
            .putInt("quality", quality)
            .putInt("style", style)
            .putString("res", resolution)
            .putInt("shadow", shadow)
            .putInt("msaa", msaa)
            .putInt("ipad", if (ipadView) 1 else 0)
            .putInt("vulkan", if (vulkan) 1 else 0)
            .putInt("hdr", if (hdr) 1 else 0)
            .apply()
    }

    private fun save(key: String, value: String) = prefs.edit().putString(key, value).apply()
    private fun save(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    /** Syncs all UI controls to match the current state variables */
    private fun syncUiToState() {
        // Game
        val rgGame = findViewById<RadioGroup>(R.id.rgGame)
        rgGame.check(if (gamePkg == GfxConfig.BGMI) R.id.rbBgmi else R.id.rbPubg)

        // FPS
        val rgFps = findViewById<RadioGroup>(R.id.rgFps)
        rgFps.check(when(fps) {
            30 -> R.id.rbFps30; 40 -> R.id.rbFps40
            90 -> R.id.rbFps90; 120 -> R.id.rbFps120
            else -> R.id.rbFps60
        })

        // Quality
        val rgQ = findViewById<RadioGroup>(R.id.rgGraphics)
        rgQ.check(when(quality) {
            2 -> R.id.rbBalanced; 3 -> R.id.rbHD; 4 -> R.id.rbHDR
            else -> R.id.rbSmooth
        })

        // Style
        val rgS = findViewById<RadioGroup>(R.id.rgStyle)
        rgS.check(when(style) {
            1 -> R.id.rbClassic; 2 -> R.id.rbRealistic; 4 -> R.id.rbSoft
            else -> R.id.rbColorful
        })

        // Resolution
        val rgR = findViewById<RadioGroup>(R.id.rgRes)
        rgR.check(when(resolution) {
            "0.5625" -> R.id.rbRes540; "1.0" -> R.id.rbRes1080
            else -> R.id.rbRes720
        })

        // Shadow
        val rgSh = findViewById<RadioGroup>(R.id.rgShadow)
        rgSh.check(when(shadow) {
            1 -> R.id.rbShadowLow; 2 -> R.id.rbShadowMed; 3 -> R.id.rbShadowHigh
            else -> R.id.rbShadowOff
        })

        // Switches
        findViewById<Switch>(R.id.swHdr).isChecked    = hdr
        findViewById<Switch>(R.id.swVulkan).isChecked = vulkan
        findViewById<Switch>(R.id.swIpad).isChecked   = ipadView
        findViewById<Switch>(R.id.swMsaa).isChecked   = msaa == 4
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private fun shizukuOk() = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    private fun isShizukuInstalled() = try {
        packageManager.getPackageInfo("moe.shizuku.privileged.api", 0); true
    } catch (_: Exception) { false }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun styleLabel(s: Int) = when(s) {
        1 -> "Classic"; 2 -> "Realistic"; 4 -> "Soft"; else -> "Colorful"
    }
    private fun resLabel(r: String) = when(r) {
        "0.5625" -> "540p"; "1.0" -> "1080p"; else -> "720p"
    }
}
