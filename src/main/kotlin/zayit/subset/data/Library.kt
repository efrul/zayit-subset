package zayit.subset.data

import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteOpenMode
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class Cat(val id: Long, val parent: Long, val title: String, val order: Long) {
    val kids = ArrayList<Cat>()
    val books = ArrayList<BookInfo>()

    /** Every book of the subtree. */
    val all = ArrayList<BookInfo>()
}

class BookInfo(val id: Long, val catId: Long, val title: String, val order: Long, val lines: Long) {
    /** The category path, shown under search results. */
    var path: String = ""
    val key: String = normalize(title)
}

/** The desktop app's library folder (seforim.db, the Lucene indexes, lexical.db, release_info.txt). */
class Library(
    val dir: File,
    val version: String?,
    val dbSize: Long,
    val roots: List<Cat>,
    val cats: Map<Long, Cat>,
    val books: Map<Long, BookInfo>,
) {
    val db: File get() = File(dir, "seforim.db")

    /** "11/10/2026" from release_info.txt's yyyyMMddHHmmss. */
    fun versionText(): String {
        val v = version ?: return "גרסה לא ידועה"
        if (v.length < 8) return v
        return v.substring(6, 8) + "/" + v.substring(4, 6) + "/" + v.substring(0, 4)
    }

    companion object {
        /** Where desktop Zayit keeps its library (FileKit databasesDir), or SEFORIMAPP_DATABASE_PATH's folder. */
        fun defaultDir(): File {
            System.getenv("SEFORIMAPP_DATABASE_PATH")?.takeIf { it.isNotBlank() }?.let { p ->
                val f = File(p)
                val dir = if (f.isFile) f.parentFile else f
                // A small library desktop Zayit was switched to is no source: the full one is still in its own place
                if (!File(dir, "subset_info.txt").isFile) return dir
            }
            // Where desktop Zayit keeps it: its own setting (a library moved elsewhere), else its own folder
            return ReplaceLibrary.zayitLibraryDir()
        }

        fun load(dir: File): Library {
            val db = File(dir, "seforim.db")
            require(db.isFile) { "לא נמצא seforim.db בתיקייה הזו" }
            val version = runCatching { File(dir, "release_info.txt").readText().trim() }.getOrNull()
            val cats = HashMap<Long, Cat>()
            val books = HashMap<Long, BookInfo>()
            Sql.openReadOnly(db).use { c ->
                c.createStatement().use { s ->
                    s.executeQuery("SELECT id, parentId, title, orderIndex FROM category").use { r ->
                        while (r.next()) cats[r.getLong(1)] = Cat(r.getLong(1), r.getLong(2), r.getString(3), r.getLong(4))
                    }
                    s.executeQuery("SELECT id, categoryId, title, orderIndex, totalLines FROM book").use { r ->
                        while (r.next()) books[r.getLong(1)] = BookInfo(r.getLong(1), r.getLong(2), r.getString(3), r.getLong(4), r.getLong(5))
                    }
                }
            }
            val roots = ArrayList<Cat>()
            for (c in cats.values) {
                val p = cats[c.parent]
                if (c.parent != 0L && p != null) p.kids.add(c) else roots.add(c)
            }
            for (b in books.values) {
                var c = cats[b.catId] ?: continue
                c.books.add(b)
                val names = ArrayList<String>()
                while (true) {
                    c.all.add(b)
                    names.add(0, c.title)
                    c = cats[c.parent]?.takeIf { c.parent != 0L } ?: break
                }
                b.path = names.joinToString(" › ")
            }
            val byCat = compareBy<Cat>({ it.order }, { it.id })
            val byBook = compareBy<BookInfo>({ it.order }, { it.id })
            roots.sortWith(byCat)
            for (c in cats.values) {
                c.kids.sortWith(byCat)
                c.books.sortWith(byBook)
            }
            return Library(dir, version, db.length(), roots, cats, books)
        }
    }
}

/** A Hebrew title as typed in a search box: no niqqud, no quotes or geresh. */
fun normalize(s: String): String =
    buildString(s.length) {
        for (ch in s) {
            if (ch in '\u0591'..'\u05C7') continue
            if (ch == '"' || ch == '\'' || ch == '״' || ch == '׳') continue
            append(ch.lowercaseChar())
        }
    }

object Sql {
    init {
        Class.forName("org.sqlite.JDBC")
    }

    /** file:///C:/... with the characters SQLite reads specially escaped (for ATTACH). */
    fun uri(file: File): String =
        "file:///" + file.absolutePath.replace('\\', '/').replace("%", "%25").replace("?", "%3f").replace("#", "%23")

    fun quote(s: String): String = "'" + s.replace("'", "''") + "'"

    /** Read-only: nothing is written next to the desktop library. */
    fun openReadOnly(file: File): Connection {
        val cfg = SQLiteConfig()
        cfg.setReadOnly(true)
        return DriverManager.getConnection("jdbc:sqlite:" + file.absolutePath, cfg.toProperties())
    }

    /** Read-write, creating the file; URI file names allowed so ATTACH can open the source read-only. */
    fun openForWrite(file: File): Connection {
        val cfg = SQLiteConfig()
        cfg.setOpenMode(SQLiteOpenMode.OPEN_URI)
        return DriverManager.getConnection("jdbc:sqlite:" + file.absolutePath, cfg.toProperties())
    }
}
