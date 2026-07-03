package com.bgmigfxtool.pro

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * Privileged shell executor.
 * Priority: 1) Shizuku.newProcess (reflection)  2) UserService AIDL  3) local fallback
 */
object Shell {

    private var service: IShellService? = null

    // Fixed: @Volatile ensures cross-thread visibility (no race condition)
    @Volatile var connected = false
        private set

    private var newProcessMethod: Method? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder != null && binder.pingBinder()) {
                service = IShellService.Stub.asInterface(binder)
                connected = true
                AppLog.log("SHELL", "UserService CONNECTED")
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            connected = false
            AppLog.log("SHELL", "UserService DISCONNECTED")
        }
    }

    fun bind() {
        if (connected) return
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName("com.bgmigfxtool.pro", "com.bgmigfxtool.pro.ShellService")
            ).tag("gfx_shell").version(4).daemon(true).processNameSuffix("shell")
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            AppLog.log("SHELL", "bindUserService error: ${e.message}")
        }
    }

    fun exec(command: String): Triple<Int, String, String> {
        // Method 1: Shizuku.newProcess via reflection (most reliable — runs as UID 2000)
        if (isShizukuGranted()) {
            try {
                val result = execViaNewProcess(command)
                if (result != null) return result
            } catch (e: Exception) {
                AppLog.log("EXEC", "newProcess failed: ${e.message}")
            }
        }

        // Method 2: UserService AIDL (if binder is connected)
        val svc = service
        if (svc != null && connected) {
            try {
                val raw = svc.exec(command) ?: "1||null"
                val parts = raw.split("|", limit = 3)
                return Triple(
                    parts.getOrNull(0)?.toIntOrNull() ?: 1,
                    parts.getOrNull(1) ?: "",
                    parts.getOrNull(2) ?: ""
                )
            } catch (e: Exception) {
                AppLog.log("EXEC", "UserService failed: ${e.message}")
                connected = false
                service = null
            }
        }

        // Method 3: Local (no elevated permissions — diagnostic only)
        AppLog.log("EXEC", "Using local fallback (no elevation)")
        return execLocal(command)
    }

    private fun execViaNewProcess(command: String): Triple<Int, String, String>? {
        return try {
            if (newProcessMethod == null) {
                newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).also { it.isAccessible = true }
                AppLog.log("REFLECT", "newProcess method found")
            }

            val process = newProcessMethod!!.invoke(
                null,
                arrayOf("sh", "-c", command),
                null as Array<String>?,
                null as String?
            ) as Process

            // Fixed: read stdout and stderr concurrently to prevent pipe-buffer deadlock
            val outFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().readText()
            }
            val err = process.errorStream.bufferedReader().readText()
            val out = outFuture.get()
            val code = process.waitFor()

            Triple(code, out, err)
        } catch (e: Exception) {
            AppLog.log("REFLECT", "newProcess error: ${e.javaClass.simpleName}: ${e.message}")
            e.cause?.let { AppLog.log("REFLECT", "Caused by: ${it.javaClass.simpleName}: ${it.message}") }
            null
        }
    }

    private fun execLocal(command: String): Triple<Int, String, String> {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val outFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                proc.inputStream.bufferedReader().readText()
            }
            val err = proc.errorStream.bufferedReader().readText()
            val out = outFuture.get()
            Triple(proc.waitFor(), out, err)
        } catch (e: Exception) {
            Triple(1, "", e.message ?: "failed")
        }
    }

    fun isShizukuGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }
}
