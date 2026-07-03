package com.bgmigfxtool.pro

/**
 * All game config constants, paths, and content generators.
 *
 * tsoml binary save naming: [quality][style][fps]
 *   quality: 1=Smooth 2=Balanced 3=HD 4=HDR/Ultra
 *   style:   1=Classic 2=Realistic 3=Colorful 4=Soft
 *   fps:     1=30 2=40 3=60 4=90 5=120
 */
object GfxConfig {

    // ─── Game packages ────────────────────────────────────────────────────────
    const val BGMI = "com.pubg.imobile"
    const val PUBG = "com.tencent.ig"

    // ─── Paths ────────────────────────────────────────────────────────────────
    private const val UE4_BASE = "files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved"

    fun configDir(pkg: String)         = "/storage/emulated/0/Android/data/$pkg/$UE4_BASE/Config/Android"
    fun internalConfigDir(pkg: String) = "/data/data/$pkg/$UE4_BASE/Config/Android"
    fun realFsConfigDir(pkg: String)   = "/data/media/0/Android/data/$pkg/$UE4_BASE/Config/Android"
    fun ptConfigDir(pkg: String)       = "/mnt/pass_through/0/Android/data/$pkg/$UE4_BASE/Config/Android"
    fun saveDir(pkg: String)           = "/storage/emulated/0/Android/data/$pkg/$UE4_BASE/SaveGames"
    fun internalSaveDir(pkg: String)   = "/data/data/$pkg/$UE4_BASE/SaveGames"
    fun realFsSaveDir(pkg: String)     = "/data/media/0/Android/data/$pkg/$UE4_BASE/SaveGames"
    fun ptSaveDir(pkg: String)         = "/mnt/pass_through/0/Android/data/$pkg/$UE4_BASE/SaveGames"

    fun userCustomIniPath(pkg: String) = "${configDir(pkg)}/UserCustom.ini"
    fun engineIniPath(pkg: String)     = "${configDir(pkg)}/Engine.ini"
    fun savePath(pkg: String)          = "${saveDir(pkg)}/SettingConfig_C.sav"

    // ─── Asset key for pre-built binary .sav ─────────────────────────────────
    /**
     * Returns the asset filename for the pre-built binary save.
     * quality: 1-4, style: 1-4, fps: 1-5
     * Lobby save uses same quality but "kk" suffix.
     */
    fun savAssetKey(quality: Int, style: Int, fps: Int) = "sav/$quality$style$fps"
    fun lobbySavAssetKey(quality: Int) = "sav/${quality}kk"

    // ─── Engine.ini builder (HDR + FPS tier unlock) ───────────────────────────
    /**
     * Writes to [SystemSettings] in Engine.ini.
     * r.AllowHDR (no value) = feature flag that enables the HDR render path.
     * r.PUBGMaxSupportQualityLevel=3 = unlocks HDR + Ultra HD in the in-game menu.
     * r.PUBGDeviceFPS* = tells the game what FPS caps each quality tier supports.
     */
    fun buildEngineIni(fps: Int, hdr: Boolean): String = buildString {
        appendLine("[SystemSettings]")
        if (hdr) {
            appendLine("r.AllowHDR")
            appendLine("r.PUBGMaxSupportQualityLevel=3")
        } else {
            appendLine("r.PUBGMaxSupportQualityLevel=2")
        }
        appendLine("r.PUBGDeviceFPSDef=$fps")
        appendLine("r.PUBGDeviceFPSLow=$fps")
        appendLine("r.PUBGDeviceFPSMid=$fps")
        appendLine("r.PUBGDeviceFPSHigh=$fps")
        appendLine("r.PUBGDeviceFPSHDR=$fps")
        if (hdr) {
            // Ultra mode caps at 60fps max (game engine limitation for Ultra HDR)
            appendLine("r.PUBGDeviceFPSUltra=${fps.coerceAtMost(60)}")
            appendLine("r.PUBGDeviceFPSUltraHDR=${fps.coerceAtMost(60)}")
        }
        appendLine("r.Vsync=0")
    }

    // ─── UserCustom.ini builder (expanded with all performance CVars) ─────────
    fun buildUserCustomIni(
        fps: Int         = 60,
        quality: Int     = 1,       // 1=Smooth 2=Balanced 3=HD 4=HDR
        resolution: String = "0.75",
        shadow: Int      = 0,       // 0=off 1=low 2=med 3=high
        msaa: Int        = 1,       // 1=off 4=on
        ipadView: Boolean = false,
        vulkan: Boolean  = false,
        hdr: Boolean     = false,
        enemyLodBias: Int = 1,      // 0=full detail 1=perf 2=ultra-perf
        viewDistance: Float = 0.85f,
        particleRate: Float = 0.5f
    ): String = buildString(1200) {
        appendLine("[UserCustom DeviceProfile]")

        // ── Core performance ─────────────────────────────────────────────────
        appendLine("+CVars=r.PUBGDeviceFPS.DefaultFrameRateLimit=$fps")
        appendLine("+CVars=r.PUBGQualityLevel=$quality")
        appendLine("+CVars=r.MobileContentScaleFactor=$resolution")

        // ── Shadow ───────────────────────────────────────────────────────────
        appendLine("+CVars=r.ShadowQuality=$shadow")
        appendLine("+CVars=r.Shadow.CSM.MaxCascades=$shadow")
        if (shadow > 0) {
            appendLine("+CVars=r.Shadow.MaxResolution=512")
        }

        // ── Anti-aliasing ─────────────────────────────────────────────────────
        appendLine("+CVars=r.Mobile.MSAA=$msaa")

        // ── HDR rendering ─────────────────────────────────────────────────────
        // MobileHDR=1 required for actual HDR display output; =0 for max perf (LDR mode)
        appendLine("+CVars=r.MobileHDR=${if (hdr) 1 else 0}")

        // ── iPad FOV ─────────────────────────────────────────────────────────
        if (ipadView) {
            appendLine("+CVars=r.PUBGFOVScaling=1")
            appendLine("+CVars=r.PUBGiPadFOV=1")
        }

        // ── Vulkan ───────────────────────────────────────────────────────────
        if (vulkan) {
            appendLine("+CVars=r.Vulkan.RHI=1")
        }

        // ── Enemy lag killers ─────────────────────────────────────────────────
        appendLine("+CVars=r.SkeletalMeshLODBias=$enemyLodBias")
        appendLine("+CVars=r.ViewDistanceScale=$viewDistance")
        appendLine("+CVars=r.StaticMeshLODDistanceScale=${(viewDistance * 0.85f).coerceAtMost(1.0f)}")

        // ── Particle system ───────────────────────────────────────────────────
        appendLine("+CVars=r.PUBGMaxGPUParticleCount=0")
        appendLine("+CVars=r.PUBGEnableGPUParticle=0")
        appendLine("+CVars=r.EmitterSpawnRateScale=$particleRate")

        // ── CPU/GPU pipeline ──────────────────────────────────────────────────
        appendLine("+CVars=r.FinishCurrentFrame=0")
        appendLine("+CVars=r.OneFrameThreadLag=1")
        appendLine("+CVars=r.AllowOcclusionQueries=1")
        appendLine("+CVars=r.HZBOcclusion=1")
        appendLine("+CVars=r.EarlyZPass=1")

        // ── Post-processing killers ───────────────────────────────────────────
        appendLine("+CVars=r.BloomQuality=0")
        appendLine("+CVars=r.DepthOfFieldQuality=0")
        appendLine("+CVars=r.LightFunctionQuality=0")
        appendLine("+CVars=r.RefractionQuality=0")
        appendLine("+CVars=r.Tonemapper.GrainQuantization=0")
        appendLine("+CVars=r.EyeAdaptationQuality=0")
        appendLine("+CVars=r.DetailMode=${if (quality >= 3) 1 else 0}")

        appendLine("")
        appendLine("[/Script/Engine.RendererSettings]")
        appendLine("r.DefaultFeature.Bloom=False")
        appendLine("r.DefaultFeature.AmbientOcclusion=False")
        appendLine("r.DefaultFeature.MotionBlur=False")
        appendLine("r.DefaultFeature.LensFlare=False")
        appendLine("r.DefaultFeature.AutoExposure=False")

        // ── Low quality extra optimisations ──────────────────────────────────
        if (quality <= 2) {
            appendLine("")
            appendLine("[/Script/Engine.StreamingSettings]")
            appendLine("s.MinBulkDataSizeForAsyncLoading=0")
            appendLine("")
            appendLine("[UserCustom DeviceProfile]")
            appendLine("+CVars=r.PUBGMaxTextureMemory=150")
            appendLine("+CVars=r.Streaming.PoolSize=150")
            appendLine("+CVars=r.Streaming.FullyLoadUsedTextures=0")
            appendLine("+CVars=foliage.LODDistanceScale=${if (quality == 1) 0.3f else 0.5f}")
            appendLine("+CVars=grass.DensityScale=${if (quality == 1) 0.3f else 0.5f}")
            appendLine("+CVars=p.ClothPhysics=0")
        } else {
            appendLine("")
            appendLine("[/Script/Engine.StreamingSettings]")
            appendLine("s.MinBulkDataSizeForAsyncLoading=0")
            appendLine("")
            appendLine("[UserCustom DeviceProfile]")
            appendLine("+CVars=r.PUBGMaxTextureMemory=${if (hdr) 400 else 250}")
            appendLine("+CVars=r.Streaming.PoolSize=${if (hdr) 400 else 250}")
            appendLine("+CVars=r.Streaming.FullyLoadUsedTextures=0")
            appendLine("+CVars=p.ClothPhysics=0")
        }
    }

    // ─── FPS to save-file index mapping ──────────────────────────────────────
    fun fpsToSavIndex(fps: Int) = when {
        fps <= 30  -> 1
        fps <= 40  -> 2
        fps <= 60  -> 3
        fps <= 90  -> 4
        else       -> 5
    }

    // ─── Quality label ────────────────────────────────────────────────────────
    fun qualityLabel(q: Int) = when(q) {
        1 -> "Smooth"; 2 -> "Balanced"; 3 -> "HD"; 4 -> "HDR/Ultra"
        else -> "Smooth"
    }
}
