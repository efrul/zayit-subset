package zayit.subset.data

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.prefs.Preferences

/**
 * Moves the library desktop Zayit uses (the full one, or whatever it has) to another folder, an external drive
 * included, and points Zayit at it through its own "database_path" setting (what its onboarding writes; the
 * multiplatform-settings store over java.util.prefs, user root). The copy is made and checked first; only then is the
 * setting changed and the old copy deleted. The user's notes, settings and tabs stay where they are (FileKit's databases
 * folder): they are not the library.
 */
object MoveLibrary {
    private val NAMES =
        listOf("seforim.db", "seforim.db.lucene", "seforim.db.lookup.lucene", "lexical.db", "catalog.pb", "release_info.txt", "subset_info.txt")
    private val LEFTOVERS = listOf("seforim.db-wal", "seforim.db-shm", "seforim.db-journal", "delta-cache")

    /** Desktop Zayit's own folder (FileKit databasesDir), where it keeps the library unless told otherwise. */
    fun defaultDir(): File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "io.github.kdroidfilter.seforimapp/databases")

    /** Where the library goes when [chosen] is picked: the default folder itself, else a "Zayit library" folder in it. */
    fun destination(chosen: File): File =
        if (chosen.canonicalFile == defaultDir().canonicalFile) chosen else File(chosen, "Zayit library")

    fun libSize(dir: File): Long = NAMES.sumOf { dirSize(File(dir, it)) }

    /** Free bytes on the drive of [dir] (or of its nearest existing parent). */
    fun free(dir: File): Long {
        var d: File? = dir
        while (d != null && !d.exists()) d = d.parentFile
        return d?.usableSpace ?: 0
    }

    /**
     * Copies the library from [from] to [to], checks every file's size, points desktop Zayit at [to] and deletes the
     * old copy. [progress] gets the bytes copied and the total; [cancelled] stops the copy (the partial copy is deleted).
     */
    fun move(
        from: File,
        to: File,
        progress: (Long, Long) -> Unit,
        cancelled: () -> Boolean,
        log: (String) -> Unit,
        updateZayit: Boolean = true,
    ) {
        require(File(from, "seforim.db").isFile) { "אין ספרייה בתיקייה ${from.path}" }
        val src = from.canonicalFile
        val dst = to.canonicalFile
        require(src != dst) { "הספרייה כבר נמצאת שם" }
        require(!dst.path.startsWith(src.path + File.separator)) { "אי אפשר להעביר את הספרייה לתוך התיקייה שלה" }
        require(!File(dst, "seforim.db").exists()) { "כבר יש ספרייה ב־${dst.path}" }
        if (updateZayit) DesktopZayit.find()?.let { exe -> check(!DesktopZayit.isRunning(exe)) { "זית במחשב פתוחה. יש לסגור אותה ולנסות שוב." } }
        check(File(src, "seforim.db-wal").let { !it.exists() || it.length() == 0L }) {
            "זית לא נסגרה כראוי (seforim.db-wal אינו ריק). יש לפתוח ולסגור אותה, ולנסות שוב."
        }
        val total = libSize(src)
        val free = free(dst)
        check(free > total + 200L * 1024 * 1024) { "אין מספיק מקום ביעד: צריך ${size(total)}, פנויים ${size(free)}" }

        dst.mkdirs()
        var done = 0L
        try {
            for (n in NAMES) {
                val f = File(src, n)
                if (!f.exists()) continue
                val files = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.toList() else listOf(f)
                for (file in files) {
                    val target = File(dst, file.relativeTo(src).path)
                    target.parentFile.mkdirs()
                    FileChannel.open(file.toPath(), StandardOpenOption.READ).use { input ->
                        FileChannel.open(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                            var pos = 0L
                            val len = input.size()
                            while (pos < len) {
                                if (cancelled()) throw Cancelled()
                                val n2 = input.transferTo(pos, minOf(32L shl 20, len - pos), output)
                                pos += n2
                                done += n2
                                progress(done, total)
                            }
                            output.force(true)
                        }
                    }
                    check(target.length() == file.length()) { "ההעתקה של ${file.name} לא הושלמה" }
                }
            }
        } catch (e: Throwable) {
            // Nothing changed for Zayit yet: drop the partial copy
            for (n in NAMES) File(dst, n).deleteRecursively()
            if (dst.listFiles().isNullOrEmpty()) dst.delete()
            throw e
        }
        check(libSize(dst) == total) { "גודל העותק החדש שונה מהמקור; העותק הישן נשאר במקומו" }
        log("הועתקו ${size(total)} ל־${dst.path}")

        if (updateZayit) {
            // Zayit reads "" as "its own folder"
            val value = if (dst == defaultDir().canonicalFile) "" else File(dst, "seforim.db").path
            Preferences.userRoot().apply {
                put("database_path", value)
                flush()
            }
            log("זית תפתח מעכשיו את הספרייה מ־${dst.path}")
        }
        for (n in NAMES + LEFTOVERS) File(src, n).deleteRecursively()
        if (src.name == "Zayit library" && src.listFiles().isNullOrEmpty()) src.delete()
        log("העותק הישן נמחק מ־${src.path}")
    }
}
