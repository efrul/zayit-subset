package zayit.subset.data

import java.io.File

/** What one book holds in the source: the bytes of its text and of its references, its lines and its TOC entries. */
class BookSize(val text: Long, val refs: Long, val lines: Long, val toc: Long) {
    /**
     * Its share of a small library, alone (without the commentaries it brings along), with the costs measured for
     * [Builder.estimate]: the database row costs, and the full-text index at 0.6 of the text when it is built.
     */
    fun weight(textIndex: Boolean): Long =
        text + 2 * refs + lines * (275 + 103) + toc * 103 + if (textIndex) (text * 0.6).toLong() else 0
}

/**
 * The size of every book of a library. Reading it takes one pass over all the lines (about half a minute for the
 * full library), so it is kept in %APPDATA%\Zayit Subset\sizes-<release>-<db size>.tsv and read from there afterwards.
 */
object BookSizes {
    private fun cacheFile(lib: Library): File =
        File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Zayit Subset/sizes-${lib.version ?: "x"}-${lib.dbSize}.tsv")

    fun load(lib: Library): Map<Long, BookSize> {
        val cache = cacheFile(lib)
        runCatching {
            if (cache.isFile) {
                val m = HashMap<Long, BookSize>()
                cache.forEachLine { line ->
                    val p = line.split('\t')
                    if (p.size == 5) m[p[0].toLong()] = BookSize(p[1].toLong(), p[2].toLong(), p[3].toLong(), p[4].toLong())
                }
                if (m.isNotEmpty()) return m
            }
        }
        val text = HashMap<Long, LongArray>()
        val toc = HashMap<Long, Long>()
        Sql.openReadOnly(lib.db).use { c ->
            c.createStatement().use { s ->
                s.executeQuery(
                    "SELECT bookId, total(length(CAST(content AS BLOB))), total(length(CAST(coalesce(heRef,'') AS BLOB))), count(*) FROM line GROUP BY bookId",
                ).use { r -> while (r.next()) text[r.getLong(1)] = longArrayOf(r.getDouble(2).toLong(), r.getDouble(3).toLong(), r.getLong(4)) }
                s.executeQuery("SELECT bookId, count(*) FROM tocEntry GROUP BY bookId").use { r -> while (r.next()) toc[r.getLong(1)] = r.getLong(2) }
            }
        }
        val m = HashMap<Long, BookSize>()
        for ((id, t) in text) m[id] = BookSize(t[0], t[1], t[2], toc[id] ?: 0)
        runCatching {
            cache.parentFile.mkdirs()
            cache.writeText(m.entries.joinToString("\n") { (id, b) -> "$id\t${b.text}\t${b.refs}\t${b.lines}\t${b.toc}" })
        }
        return m
    }
}
