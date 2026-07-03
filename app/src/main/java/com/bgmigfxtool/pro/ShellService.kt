package com.bgmigfxtool.pro

import android.content.Context

/**
 * Shizuku User Service — runs as UID 2000 (shell) in a separate process.
 * 
 * Shizuku v13 tries constructor(Context) first, then falls back to constructor().
 * We provide both.
 */
class ShellService : IShellService.Stub {

    // Default constructor (required for older Shizuku)
    constructor()

    // Context constructor (Shizuku v13+)
    constructor(context: Context)

    override fun exec(command: String?): String {
        if (command.isNullOrBlank()) return "1||empty command"
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            val code = proc.waitFor()
            "$code|$out|$err"
        } catch (e: Exception) {
            "1||${e.message}"
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}
