package com.bgmigfxtool.pro

import android.content.Context
import android.util.Base64

/**
 * Orchestrates writing all three game config files using Shell's privileged access:
 *  1. UserCustom.ini  — render CVars
 *  2. Engine.ini      — [SystemSettings] HDR + FPS tier unlock
 *  3. SettingConfig_C.sav — pre-built binary save (quality + style + fps in-game)
 */
object FileWriter {

    data class Settings(
        val pkg: String,
        val fps: Int,
        val quality: Int,       // 1=Smooth 2=Balanced 3=HD 4=HDR
        val style: Int,         // 1=Classic 2=Realistic 3=Colorful 4=Soft
        val resolution: String,
        val shadow: Int,
        val msaa: Int,
        val ipadView: Boolean,
        val vulkan: Boolean,
        val hdr: Boolean,
        val enemyLodBias: Int,
        val viewDistance: Float,
        val particleRate: Float
    )

    sealed class WriteResult {
        data class Success(val method: String) : WriteResult()
        data class Failure(val error: String) : WriteResult()
    }

    /**
     * Applies all three files. Runs on whatever thread you call it from
     * (caller must use a background thread).
     * Returns a summary string of what succeeded.
     */
    fun applyAll(ctx: Context, s: Settings): String {
        val results = mutableListOf<String>()

        // ── 1. UserCustom.ini ────────────────────────────────────────────────
        val iniContent = GfxConfig.buildUserCustomIni(
            fps = s.fps,
            quality = s.quality,
            resolution = s.resolution,
            shadow = s.shadow,
            msaa = s.msaa,
            ipadView = s.ipadView,
            vulkan = s.vulkan,
            hdr = s.hdr,
            enemyLodBias = s.enemyLodBias,
            viewDistance = s.viewDistance,
            particleRate = s.particleRate
        )
        val iniResult = writeTextFile(iniContent, "UserCustom.ini", s.pkg, "UserCustom.ini")
        results.add(iniResult)
        AppLog.log("WRITER", "UserCustom.ini: $iniResult")

        // ── 2. Engine.ini ────────────────────────────────────────────────────
        val engineContent = GfxConfig.buildEngineIni(s.fps, s.hdr)
        val engineResult = writeTextFile(engineContent, "Engine.ini", s.pkg, "Engine.ini")
        results.add(engineResult)
        AppLog.log("WRITER", "Engine.ini: $engineResult")

        // ── 3. Binary .sav ───────────────────────────────────────────────────
        val fpsIdx = GfxConfig.fpsToSavIndex(s.fps)
        val assetKey = GfxConfig.savAssetKey(s.quality, s.style, fpsIdx)
        val lobbyKey = GfxConfig.lobbySavAssetKey(s.quality)
        val savResult = writeBinarySav(ctx, assetKey, lobbyKey, s.pkg)
        results.add(savResult)
        AppLog.log("WRITER", "SettingConfig_C.sav: $savResult")

        return results.joinToString("\n")
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Writes a plain-text config file using base64 + the 6-method cascade.
     */
    private fun writeTextFile(content: String, label: String, pkg: String, filename: String): String {
        val tmp = "/data/local/tmp/gfx_${filename.replace(".", "_")}"
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)

        // Decode base64 into temp file
        val (decCode, _, decErr) = Shell.exec("echo '$encoded' | base64 -d > '$tmp'")
        if (decCode != 0) {
            AppLog.log("WRITE", "$label temp write failed: $decErr")
        }

        val configDir      = GfxConfig.configDir(pkg)
        val internalDir    = GfxConfig.internalConfigDir(pkg)
        val realFsDir      = GfxConfig.realFsConfigDir(pkg)
        val ptDir          = GfxConfig.ptConfigDir(pkg)
        val targetPath     = "$configDir/$filename"
        val internalPath   = "$internalDir/$filename"
        val realFsPath     = "$realFsDir/$filename"
        val ptPath         = "$ptDir/$filename"

        val methods = listOf(
            // M0: Android 14/15/16 bypass via pass_through
            "mkdir -p '$ptDir' && rm -f '$ptPath'; cp -f '$tmp' '$ptPath'",
            // M1: internal app data (works on most ROMs)
            "rm -f '$internalPath'; mkdir -p '$internalDir' && cp -f '$tmp' '$internalPath'",
            // M2: DocumentsUI content provider
            "content write --uri content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata%2F${pkg}%2F${UE4_ENCODE}%2F${filename.replace(".", "%2E")} < '$tmp'",
            // M3: /data/media real filesystem path
            "rm -f '$realFsPath'; mkdir -p '$realFsDir' && cp -f '$tmp' '$realFsPath'",
            // M4: sdcard staging then force move
            "cp -f '$tmp' /sdcard/$filename && rm -f '$targetPath'; mv -f /sdcard/$filename '$targetPath'",
            // M5: chmod then write
            "mkdir -p '$configDir' && chmod 777 '$configDir' && rm -f '$targetPath'; cp -f '$tmp' '$targetPath' && chmod 771 '$configDir'",
            // M6: restorecon SELinux fix then write
            "restorecon -R '$configDir' 2>/dev/null; mkdir -p '$configDir' && rm -f '$targetPath'; cp -f '$tmp' '$targetPath'",
            // M7: root/shell cat directly (bypasses some move restrictions)
            "cat '$tmp' > '$targetPath'"
        )

        var successMethod = ""
        for ((i, cmd) in methods.withIndex()) {
            val (code, out, err) = Shell.exec(cmd)
            AppLog.log("M${i+1}[$label]", "code=$code err=${err.take(80)}")
            if (code == 0) {
                // Verify file has content
                val (_, vo, _) = Shell.exec(
                    "wc -c '$targetPath' 2>/dev/null || wc -c '$internalPath' 2>/dev/null || wc -c '$ptPath' 2>/dev/null"
                )
                val size = vo.trim().split(" ").firstOrNull()?.toIntOrNull() ?: 0
                if (size > 0) {
                    successMethod = "M${i + 1}"
                    AppLog.log("VERIFY[$label]", "✅ Written via M${i+1}, size=${size}B")
                    break
                }
            }
        }

        Shell.exec("rm -f '$tmp'")
        return if (successMethod.isNotEmpty()) "✅ $label via $successMethod" else "❌ $label failed"
    }

    /**
     * Copies the pre-built binary .sav from assets to the game's SaveGames directory.
     */
    private fun writeBinarySav(ctx: Context, assetKey: String, lobbyKey: String, pkg: String): String {
        val tmp = "/data/local/tmp/gfx_settings.sav"
        val lobbyTmp = "/data/local/tmp/gfx_lobby.sav"

        // Read main sav asset
        val savBytes = try {
            ctx.assets.open(assetKey).readBytes()
        } catch (e: Exception) {
            AppLog.log("SAV", "Asset not found: $assetKey — ${e.message}")
            return "❌ Save asset missing ($assetKey)"
        }

        // Read lobby sav asset
        val lobbySavBytes = try {
            ctx.assets.open(lobbyKey).readBytes()
        } catch (e: Exception) {
            null // lobby sav is optional
        }

        // Write bytes as base64 to temp
        val encoded = Base64.encodeToString(savBytes, Base64.NO_WRAP)
        Shell.exec("echo '$encoded' | base64 -d > '$tmp'")

        if (lobbySavBytes != null) {
            val lobbyEncoded = Base64.encodeToString(lobbySavBytes, Base64.NO_WRAP)
            Shell.exec("echo '$lobbyEncoded' | base64 -d > '$lobbyTmp'")
        }

        val saveDir     = GfxConfig.saveDir(pkg)
        val internalSaveDir  = GfxConfig.internalSaveDir(pkg)
        val realFsSaveDir    = GfxConfig.realFsSaveDir(pkg)
        val ptSaveDir   = GfxConfig.ptSaveDir(pkg)
        val savFile     = GfxConfig.savePath(pkg)
        val internalSav = "$internalSaveDir/SettingConfig_C.sav"
        val realFsSav   = "$realFsSaveDir/SettingConfig_C.sav"
        val ptSav       = "$ptSaveDir/SettingConfig_C.sav"

        val methods = listOf(
            "mkdir -p '$ptSaveDir' && rm -f '$ptSav'; cp -f '$tmp' '$ptSav'",
            "rm -f '$internalSav'; mkdir -p '$internalSaveDir' && cp -f '$tmp' '$internalSav'",
            "content write --uri content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata%2F${pkg}%2F${UE4_SAVE_ENCODE}%2FSettingConfig_C%2Esav < '$tmp'",
            "rm -f '$realFsSav'; mkdir -p '$realFsSaveDir' && cp -f '$tmp' '$realFsSav'",
            "mkdir -p '$saveDir' && chmod 777 '$saveDir' && rm -f '$savFile'; cp -f '$tmp' '$savFile' && chmod 771 '$saveDir'",
            "cp -f '$tmp' /sdcard/SettingConfig_C.sav && rm -f '$savFile'; mv -f /sdcard/SettingConfig_C.sav '$savFile'",
            "cat '$tmp' > '$savFile'"
        )

        var success = ""
        for ((i, cmd) in methods.withIndex()) {
            val (code, _, err) = Shell.exec(cmd)
            AppLog.log("SAV_M${i+1}", "code=$code err=${err.take(60)}")
            if (code == 0) {
                val (_, vo, _) = Shell.exec(
                    "wc -c '$savFile' 2>/dev/null || wc -c '$internalSav' 2>/dev/null || wc -c '$ptSav' 2>/dev/null"
                )
                val size = vo.trim().split(" ").firstOrNull()?.toIntOrNull() ?: 0
                if (size > 100) { // binary files are several KB
                    success = "M${i + 1}"
                    AppLog.log("SAV_VERIFY", "✅ SAV written via M${i+1}, size=${size}B")
                    break
                }
            }
        }

        Shell.exec("rm -f '$tmp' '$lobbyTmp'")
        return if (success.isNotEmpty()) "✅ Save file via $success" else "❌ Save file failed"
    }

    // UE4 path segment URL-encoded for content:// URI
    private const val UE4_ENCODE =
        "files%2FUE4Game%2FShadowTrackerExtra%2FShadowTrackerExtra%2FSaved%2FConfig%2FAndroid"
    private const val UE4_SAVE_ENCODE =
        "files%2FUE4Game%2FShadowTrackerExtra%2FShadowTrackerExtra%2FSaved%2FSaveGames"
}
