package zayit.subset.data

import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Desktop Zayit on a small library, without touching the full one: SEFORIMAPP_DATABASE_PATH points it at the small
 * seforim.db (DatabasePathProvider reads it before its own setting), and APPDATA at a profile folder inside the small
 * library, because desktop keeps its tabs, history and notes under FileKit.databasesDir
 * (%APPDATA%\io.github.kdroidfilter.seforimapp): an instance sharing that folder would save its tabs over the ones of
 * the full library. Zayit runs one instance at a time: a second one hands over to the open one and quits.
 */
object DesktopZayit {
    const val ENV = "SEFORIMAPP_DATABASE_PATH"

    fun find(): File? {
        val local = System.getenv("LOCALAPPDATA")
        val pf = System.getenv("ProgramFiles")
        return listOfNotNull(
            Settings.get("ZayitExe", "").takeIf { it.isNotBlank() },
            local?.let { "$it/Programs/zayit/zayit.exe" },
            local?.let { "$it/Programs/Zayit/Zayit.exe" },
            pf?.let { "$it/zayit/zayit.exe" },
            pf?.let { "$it/Zayit/Zayit.exe" },
        ).map(::File).firstOrNull { it.isFile }
    }

    fun isRunning(exe: File): Boolean =
        ProcessHandle.allProcesses().anyMatch { p -> p.info().command().map { it.equals(exe.path, ignoreCase = true) }.orElse(false) }

    /** Starts it on [library] with its own profile; true when it is still running after a few seconds. */
    fun open(
        exe: File,
        library: File,
    ): Boolean {
        val profile = File(library, ".zayit-profile").apply { mkdirs() }
        val pb = ProcessBuilder(exe.path).directory(exe.parentFile).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.environment()[ENV] = File(library, "seforim.db").path
        pb.environment()["APPDATA"] = profile.path
        val p = pb.start()
        return !p.waitFor(6, TimeUnit.SECONDS)
    }

    // ---------- the library desktop Zayit uses for good: the user's SEFORIMAPP_DATABASE_PATH ----------

    /** The small library desktop Zayit was switched to (the user's environment variable), or null. */
    fun permanent(): String? = powershell("[Environment]::GetEnvironmentVariable('$ENV', 'User')").trim().takeIf { it.isNotEmpty() }

    /** From its next start, desktop Zayit opens [db] instead of its own library (with its usual profile). */
    fun setPermanent(db: File) {
        powershell("[Environment]::SetEnvironmentVariable('$ENV', '${db.path.replace("'", "''")}', 'User')")
    }

    /** Back to the library desktop Zayit installed itself. */
    fun clearPermanent() {
        powershell("[Environment]::SetEnvironmentVariable('$ENV', \$null, 'User')")
    }

    // ---------- shortcut ----------

    /**
     * "זית – <library>" on the desktop and in the Start menu: our launcher with --zayit <library>, which starts desktop
     * Zayit on it with its own profile. Returns the desktop shortcut.
     */
    fun createShortcut(library: File): File {
        val launcher = launcher() ?: error("לא נמצא ZayitSubset.exe (התוכנה לא מותקנת)")
        val exe = find() ?: error("לא נמצאה זית למחשב")
        val name = "זית – " + library.name
        fun q(s: String) = "'" + s.replace("'", "''") + "'"
        val script =
            """
            ${'$'}sh = New-Object -ComObject WScript.Shell
            foreach (${'$'}dir in @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs'))) {
              ${'$'}s = ${'$'}sh.CreateShortcut((Join-Path ${'$'}dir ${q("$name.lnk")}))
              ${'$'}s.TargetPath = ${q(launcher.path)}
              ${'$'}s.Arguments = ${q("--zayit \"${library.path}\"")}
              ${'$'}s.WorkingDirectory = ${q(library.path)}
              ${'$'}s.IconLocation = ${q(exe.path + ",0")}
              ${'$'}s.Description = ${q("זית על הספרייה המוקטנת " + library.name)}
              ${'$'}s.Save()
            }
            """.trimIndent()
        powershell(script)
        return File(System.getProperty("user.home"), "Desktop/$name.lnk")
    }

    /** ZayitSubset.exe of the installed app: lib\zayit-subset.jar is next to it. */
    private fun launcher(): File? {
        val jar = runCatching { File(DesktopZayit::class.java.protectionDomain.codeSource.location.toURI()) }.getOrNull() ?: return null
        return File(jar.parentFile?.parentFile, "ZayitSubset.exe").takeIf { it.isFile }
    }

    /** Runs [script] and returns what it printed; its output comes back as UTF-8, so Hebrew paths read right. */
    private fun powershell(script: String): String {
        val full = "\$ProgressPreference = 'SilentlyContinue'; [Console]::OutputEncoding = [Text.Encoding]::UTF8\n" + script
        // -EncodedCommand: UTF-16LE base64, so Hebrew paths and quotes reach PowerShell unchanged
        val encoded = Base64.getEncoder().encodeToString(full.toByteArray(Charsets.UTF_16LE))
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded).start()
        val err = StringBuilder()
        val t = Thread { err.append(p.errorStream.bufferedReader().readText()) }.apply { start() }
        val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText()
        p.waitFor(60, TimeUnit.SECONDS)
        t.join(5000)
        if (p.exitValue() != 0 || err.contains("Exception")) {
            error(err.lines().firstOrNull { it.contains("Exception") || it.contains("error", ignoreCase = true) } ?: "PowerShell exit ${p.exitValue()}")
        }
        return out
    }
}
