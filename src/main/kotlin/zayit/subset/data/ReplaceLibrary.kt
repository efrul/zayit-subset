package zayit.subset.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

/**
 * Puts a small library in the place of the one desktop Zayit uses, for good. Only the library's own files move (the
 * names desktop's DatabaseCleanupUseCase treats as the database's); the user's notes, settings and tabs (settings\,
 * session\) stay. The old files go aside first, in the same folder; the new ones move in; only then are the old ones
 * deleted. If a step fails, everything goes back where it was.
 */
object ReplaceLibrary {
    private val NAMES =
        listOf(
            "seforim.db", "seforim.db-wal", "seforim.db-shm", "seforim.db-journal", "seforim.db.lucene",
            "seforim.db.lookup.lucene", "lexical.db", "catalog.pb", "release_info.txt", "delta-cache", "subset_info.txt",
        )

    /**
     * The folder of the library desktop Zayit opens on its own: its "database_path" setting (multiplatform-settings over
     * java.util.prefs, the user root), else FileKit's databases folder (DatabasePathProvider without the environment
     * variable, which only this app's "switch" option sets).
     */
    fun zayitLibraryDir(): File {
        val saved = runCatching { Preferences.userRoot().get("database_path", "") }.getOrDefault("")
        if (saved.isNotBlank() && !saved.endsWith("lexical.db", ignoreCase = true)) File(saved).parentFile?.let { return it }
        return File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "io.github.kdroidfilter.seforimapp/databases")
    }

    /** The size of the library in [dir] (its own files only). */
    fun size(dir: File): Long = NAMES.sumOf { dirSize(File(dir, it)) }

    fun replace(
        small: File,
        target: File,
        log: (String) -> Unit,
        checkZayit: Boolean = true,
    ) {
        require(File(small, "seforim.db").isFile) { "בספרייה המוקטנת אין seforim.db" }
        require(small.canonicalFile != target.canonicalFile) { "הספרייה המוקטנת כבר נמצאת במקום של ספריית זית" }
        target.mkdirs()
        if (checkZayit) DesktopZayit.find()?.let { exe -> check(!DesktopZayit.isRunning(exe)) { "זית במחשב פתוחה. יש לסגור אותה ולנסות שוב." } }

        val aside = File(target, ".old-library-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
        aside.mkdirs()
        val movedAside = ArrayList<String>()
        val movedIn = ArrayList<String>()
        try {
            for (n in NAMES) {
                val f = File(target, n)
                if (!f.exists()) continue
                Files.move(f.toPath(), File(aside, n).toPath())
                movedAside.add(n)
            }
            for (n in NAMES) {
                val f = File(small, n)
                if (!f.exists()) continue
                move(f, File(target, n))
                movedIn.add(n)
            }
        } catch (e: Exception) {
            // Back as it was: the small library's files to their folder, the full library's to theirs
            for (n in movedIn.reversed()) runCatching { move(File(target, n), File(small, n)) }
            for (n in movedAside.reversed()) runCatching { Files.move(File(aside, n).toPath(), File(target, n).toPath()) }
            aside.delete()
            throw IllegalStateException("ההחלפה נכשלה והספרייה הקודמת הוחזרה למקומה: " + (e.message ?: e.toString()), e)
        }
        log("הספרייה המוקטנת הועברה ל־" + target.path)
        if (!aside.deleteRecursively()) log("חלק מקבצי הספרייה הקודמת לא נמחקו: " + aside.path)
        // The "switch" option points desktop Zayit elsewhere; the library in its own place is the one wanted now
        if (checkZayit && DesktopZayit.permanent() != null) DesktopZayit.clearPermanent()
    }

    /** A rename on the same drive; across drives, a copy then a delete. */
    private fun move(
        from: File,
        to: File,
    ) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            if (from.isDirectory) from.copyRecursively(to, overwrite = false) else from.copyTo(to, overwrite = false)
            from.deleteRecursively()
        }
    }
}
