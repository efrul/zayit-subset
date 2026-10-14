package zayit.subset.data

import java.io.File
import java.util.concurrent.TimeUnit

data class Device(val serial: String, val model: String)

/** adb, and the transfer to the phone through push-desktop-library.ps1 (bundled as a resource). */
object Phone {
    fun adb(): File? {
        val candidates = ArrayList<File>()
        System.getenv("PATH")?.split(File.pathSeparator)?.filter { it.isNotBlank() }?.forEach { candidates.add(File(it, "adb.exe")) }
        for (e in listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")) System.getenv(e)?.let { candidates.add(File(it, "platform-tools/adb.exe")) }
        System.getenv("LOCALAPPDATA")?.let { candidates.add(File(it, "Android/Sdk/platform-tools/adb.exe")) }
        candidates.add(File("C:/adb/adb.exe"))
        return candidates.firstOrNull { runCatching { it.isFile }.getOrDefault(false) }
    }

    fun devices(adb: File): List<Device> {
        val p = ProcessBuilder(adb.path, "devices", "-l").redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor(10, TimeUnit.SECONDS)
        return text.lines().drop(1).mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2 || parts[1] != "device") return@mapNotNull null
            val model = parts.firstOrNull { it.startsWith("model:") }?.removePrefix("model:")?.replace('_', ' ') ?: parts[0]
            Device(parts[0], model)
        }
    }

    /**
     * Runs the transfer script on [source] for [device]. The script prints "==> English" then "    Hebrew" for each
     * step: the Hebrew line goes to [log]; adb's own progress goes to [status]. Returns the script's exit code.
     */
    fun push(
        adb: File,
        device: Device,
        source: File,
        log: (String, Boolean) -> Unit,
        status: (String) -> Unit,
    ): Int {
        val dir = File(System.getProperty("java.io.tmpdir"), "ZayitSubset").apply { mkdirs() }
        val script = File(dir, "push-desktop-library.ps1")
        Phone::class.java.getResourceAsStream("/push-desktop-library.ps1")!!.use { input -> script.outputStream().use { input.copyTo(it) } }
        val pb = ProcessBuilder(
            "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.path,
            "-Source", source.path, "-Serial", device.serial,
        ).redirectErrorStream(true)
        pb.environment()["PATH"] = adb.parent + File.pathSeparator + (System.getenv("PATH") ?: "")
        val p = pb.start()
        var hebrewNext = false
        var error = false
        p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { raw ->
            val line = raw.trimEnd()
            when {
                line.isEmpty() -> Unit
                line.startsWith("==> ") -> hebrewNext = true
                line.startsWith("ERROR:") -> error = true
                hebrewNext && raw.startsWith("    ") -> {
                    hebrewNext = false
                    log(line.trim(), false)
                }
                error && raw.startsWith("       ") -> log(line.trim(), true)
                line.contains("%]") || line.contains("pushed") -> status(line.trim())
                else -> hebrewNext = false
            }
        }
        return p.waitFor()
    }
}
