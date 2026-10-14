package zayit.subset.data

import java.io.File

/** key=value settings in %APPDATA%\Zayit Subset\settings.ini (the same file the first version used). */
object Settings {
    private val file = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Zayit Subset/settings.ini")
    private val values: MutableMap<String, String> by lazy {
        val m = LinkedHashMap<String, String>()
        runCatching {
            file.readLines(Charsets.UTF_8).forEach { line ->
                val i = line.indexOf('=')
                if (i > 0) m[line.substring(0, i).trimStart('﻿')] = line.substring(i + 1)
            }
        }
        m
    }

    fun get(key: String, def: String): String = values[key]?.takeIf { it.isNotEmpty() } ?: def

    fun flag(key: String, def: Boolean): Boolean = get(key, if (def) "1" else "0") == "1"

    fun set(key: String, value: String) {
        values[key] = value
        runCatching {
            file.parentFile.mkdirs()
            file.writeText(values.entries.joinToString("\r\n") { it.key + "=" + it.value }, Charsets.UTF_8)
        }
    }

    fun set(key: String, value: Boolean) = set(key, if (value) "1" else "0")
}
