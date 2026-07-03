package com.bgmigfxtool.pro

import java.text.SimpleDateFormat
import java.util.*

/**
 * Thread-safe in-memory logger. Keeps last 200 entries.
 */
object AppLog {

    // Fixed: use synchronizedList to prevent ConcurrentModificationException
    private val entries = Collections.synchronizedList(mutableListOf<String>())
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(tag: String, msg: String) {
        val line = "[${fmt.format(Date())}] $tag: $msg"
        synchronized(entries) {
            entries.add(line)
            if (entries.size > 200) entries.removeAt(0)
        }
    }

    fun getAll(): String = synchronized(entries) { entries.joinToString("\n") }

    fun clear() = synchronized(entries) { entries.clear() }
}
