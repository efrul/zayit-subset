package zayit.subset.data

import org.apache.lucene.codecs.KnnVectorsReader
import org.apache.lucene.document.IntPoint
import org.apache.lucene.index.CodecReader
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.FieldInfo
import org.apache.lucene.index.FieldInfos
import org.apache.lucene.index.FilterCodecReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.DocIdSetIterator
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreMode
import org.apache.lucene.search.Sort
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.NIOFSDirectory
import org.apache.lucene.util.Bits
import org.apache.lucene.util.FixedBitSet
import java.io.File
import java.sql.Connection
import java.sql.Statement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Options(
    val books: List<Long>,
    /** COMMENTARY and TARGUM links (connection types 1 and 3). */
    val commentaries: Boolean = true,
    /** Linked books above this many lines are left out (MAX_LINES). */
    val maxLines: Long = 10_000,
    /** Linked books above this many lines keep only the lines linked to a chosen book (FULL_LINES). */
    val fullLines: Long = 3_000,
    /** Full-text search (seforim.db.lucene). */
    val textIndex: Boolean = true,
    /** Book and author suggestions (seforim.db.lookup.lucene). */
    val lookup: Boolean = true,
    /** Both limits off: every linked commentary and targum comes in whole. */
    val noLimits: Boolean = false,
    /** Commentaries chosen by hand, over what the limits would choose. */
    val overrides: Map<Long, Mode> = emptyMap(),
    /** Keep the dense vectors of the text index (desktop Zayit's search by meaning needs them; Android does not). */
    val keepVectors: Boolean = false,
) {
    /** What the limits choose for a linked commentary of [lines] lines. */
    fun autoMode(lines: Long): Mode =
        when {
            noLimits -> Mode.Full
            lines > maxLines -> Mode.Out
            lines > fullLines -> Mode.Linked
            else -> Mode.Full
        }

    fun modeOf(
        id: Long,
        lines: Long,
    ): Mode = overrides[id] ?: autoMode(lines)
}

/** How a linked commentary goes in: whole, only its lines linked to a chosen book, or not at all. */
enum class Mode { Full, Linked, Out }

/** Which books go in: the chosen ones and the commentaries in [full] whole, those in [linked] by their linked lines. */
class Plan(val chosen: List<Long>, val candidates: List<Long>, val full: List<Long>, val linked: List<Long>) {
    val keep: List<Long> get() = (full + linked).distinct()
}

class Cancelled : Exception()

/** The expected size of each part of a small library, in bytes (0: left out). */
data class Estimate(
    val books: Int = 0,
    val lines: Long = 0,
    val database: Long = 0,
    val textIndex: Long = 0,
    val lookup: Long = 0,
    val lexical: Long = 0,
    val catalog: Long = 0,
) {
    val total: Long get() = database + textIndex + lookup + lexical + catalog
}

/**
 * Builds a small library from the desktop one: the database cut to the chosen books (and what links them), then the
 * two Lucene indexes cut to the same books and lines. The index documents are merged as they are (no re-analysis), so
 * search on the small library finds what the full one finds there.
 */
class Builder(
    private val lib: Library,
    private val o: Options,
    private val out: File,
    private val log: (String) -> Unit,
    private val progress: (fraction: Float?, step: String) -> Unit,
) {
    @Volatile private var cancelled = false

    @Volatile private var statement: Statement? = null

    @Volatile private var writer: IndexWriter? = null

    fun cancel() {
        cancelled = true
        runCatching { statement?.cancel() }
        runCatching { writer?.rollback() }
    }

    private fun check() {
        if (cancelled) throw Cancelled()
    }


    fun run() {
        out.mkdirs()
        val db = File(out, "seforim.db")
        for (n in listOf("seforim.db", "seforim.db-wal", "seforim.db-shm", "seforim.db-journal", "catalog.pb")) File(out, n).delete()
        for (n in listOf("seforim.db.lucene", "seforim.db.lookup.lucene")) File(out, n).deleteRecursively()

        val started = System.nanoTime()
        progress(0.01f, "בוחר ספרים ופירושים")
        val plan = plan(lib, o) { statement = it }
        statement = null
        check()
        buildDatabase(db, plan)
        check()

        // The ids the indexes are cut to, from the new database itself.
        val books: IntArray
        val lines: IntArray
        val authors: IntArray
        Sql.openReadOnly(db).use { c ->
            books = ids(c, "SELECT id FROM book")
            lines = ids(c, "SELECT id FROM line")
            authors = ids(c, "SELECT DISTINCT authorId FROM book_author")
        }

        // catalog.pb: the book tree, cut to these books (desktop shows no book list without it)
        val catalog = File(lib.dir, "catalog.pb")
        if (catalog.isFile) {
            progress(0.58f, "רשימת הספרים (catalog.pb)")
            val (b, c) = Catalog.filter(catalog, File(out, "catalog.pb"), books.map { it.toLong() }.toSet())
            log("רשימת הספרים: %,d ספרים ב־%,d קטגוריות".format(b, c))
        }

        if (o.lookup) {
            progress(0.62f, "אינדקס הצעות ספרים ומחברים")
            filterIndex(lookup = true, File(lib.dir, "seforim.db.lookup.lucene"), File(out, "seforim.db.lookup.lucene"), books, authors)
            check()
        }
        if (o.textIndex) {
            progress(null, "אינדקס החיפוש בטקסט: קורא את האינדקס המלא (כמה דקות)")
            filterIndex(lookup = false, File(lib.dir, "seforim.db.lucene"), File(out, "seforim.db.lucene"), books, lines)
            check()
        }

        progress(0.97f, "קבצים נלווים")
        for (n in listOf("lexical.db", "release_info.txt")) {
            val s = File(lib.dir, n)
            if (s.isFile) s.copyTo(File(out, n), overwrite = true)
        }
        writeInfo(books.size, lines.size)
        progress(1f, "הסתיים")
        val secs = (System.nanoTime() - started) / 1_000_000_000
        log("הסתיים תוך %d:%02d דקות. %,d ספרים, %,d שורות.".format(secs / 60, secs % 60, books.size, lines.size))
    }

    private fun buildDatabase(
        db: File,
        plan: Plan,
    ) {
        progress(0.02f, "מעתיק את מבנה מסד הנתונים")
        Sql.openForWrite(db).use { c ->
            c.createStatement().use { s ->
                statement = s
                try {
                    for (p in listOf(
                        "PRAGMA page_size=4096", "PRAGMA journal_mode=OFF", "PRAGMA synchronous=OFF",
                        "PRAGMA foreign_keys=OFF", "PRAGMA temp_store=MEMORY", "PRAGMA cache_size=-262144",
                    )) s.execute(p)
                    s.execute("ATTACH DATABASE " + Sql.quote(Sql.uri(lib.db) + "?mode=ro") + " AS src")

                    // Tables first, their indexes once the rows are in (faster), then the views.
                    for (sql in strings(s, "SELECT sql FROM src.sqlite_master WHERE type='table' AND sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY rowid")) {
                        s.execute(sql)
                    }
                    val steps = dataSql(plan)
                    steps.forEachIndexed { i, (label, statements) ->
                        check()
                        progress(0.04f + 0.44f * i / steps.size, label)
                        for (sql in statements) s.execute(sql)
                    }
                    progress(0.49f, "בונה אינדקסים במסד")
                    for (sql in strings(s, "SELECT sql FROM src.sqlite_master WHERE type IN ('index','view','trigger') AND sql IS NOT NULL ORDER BY CASE type WHEN 'index' THEN 0 ELSE 1 END, rowid")) {
                        check()
                        s.execute(sql)
                    }
                    s.execute("DETACH DATABASE src")
                    check()
                    progress(0.55f, "דוחס את מסד הנתונים")
                    s.execute("PRAGMA journal_mode=DELETE")
                    s.execute("VACUUM")
                    s.execute("ANALYZE")
                    val books = long(s, "SELECT count(*) FROM book")
                    val lines = long(s, "SELECT count(*) FROM line")
                    val links = long(s, "SELECT count(*) FROM link")
                    log("מסד הנתונים: %,d ספרים, %,d שורות, %,d קישורים, %s".format(books, lines, links, size(db.length())))
                } catch (e: java.sql.SQLException) {
                    if (cancelled) throw Cancelled()
                    throw e
                } finally {
                    statement = null
                }
            }
        }
    }

    // The subset, step by step, on the books of [plan]: the books, their categories, every per-book table, the
    // lines (whole books, and the lines of the others linked to a chosen book), the links between kept lines.
    private fun dataSql(plan: Plan): List<Pair<String, List<String>>> {
        val b = plan.chosen.joinToString(",")
        val keep = plan.keep.joinToString(",")
        val full = plan.full.joinToString(",")
        val linked = plan.linked.joinToString(",").ifEmpty { "-1" }
        return listOf(
            "בוחר ספרים ופירושים" to listOf(
                "CREATE TEMP TABLE keep_book(id INTEGER PRIMARY KEY)",
                "INSERT INTO keep_book SELECT id FROM src.book WHERE id IN ($keep)",
            ),
            "מעתיק קטגוריות" to listOf(
                "CREATE TEMP TABLE keep_cat(id INTEGER PRIMARY KEY)",
                """INSERT OR IGNORE INTO keep_cat
                   SELECT cc.ancestorId FROM src.category_closure cc JOIN src.book b ON b.categoryId = cc.descendantId
                   WHERE b.id IN (SELECT id FROM keep_book)""",
                "INSERT OR IGNORE INTO keep_cat SELECT categoryId FROM src.book WHERE id IN (SELECT id FROM keep_book)",
                "INSERT INTO category SELECT * FROM src.category WHERE id IN (SELECT id FROM keep_cat)",
                """INSERT INTO category_closure SELECT * FROM src.category_closure
                   WHERE ancestorId IN (SELECT id FROM keep_cat) AND descendantId IN (SELECT id FROM keep_cat)""",
            ),
            "מעתיק טבלאות עזר" to listOf(
                "connection_type", "schema_meta", "source", "license", "edition", "author", "author_alias",
                "author_bio", "topic", "pub_date", "pub_place",
            ).map { "INSERT INTO $it SELECT * FROM src.$it" },
            "מעתיק ספרים" to listOf("INSERT INTO book SELECT * FROM src.book WHERE id IN (SELECT id FROM keep_book)"),
            "בוחר שורות" to listOf(
                "CREATE TEMP TABLE keep_line(id INTEGER PRIMARY KEY)",
                "INSERT OR IGNORE INTO keep_line SELECT l.id FROM src.line l WHERE l.bookId IN ($full)",
                """INSERT OR IGNORE INTO keep_line SELECT k.targetLineId FROM src.link k
                   WHERE k.sourceBookId IN ($b) AND k.targetBookId IN ($linked)""",
                """INSERT OR IGNORE INTO keep_line SELECT k.sourceLineId FROM src.link k
                   WHERE k.targetBookId IN ($b) AND k.sourceBookId IN ($linked)""",
            ),
            "מעתיק את הטקסט" to listOf("INSERT INTO line SELECT * FROM src.line WHERE id IN (SELECT id FROM keep_line)"),
            "מעתיק תוכן עניינים" to listOf(
                "INSERT INTO tocEntry SELECT * FROM src.tocEntry WHERE bookId IN (SELECT id FROM keep_book)",
                "INSERT INTO tocText SELECT * FROM src.tocText WHERE id IN (SELECT textId FROM tocEntry)",
                "INSERT INTO line_toc SELECT lt.* FROM line l JOIN src.line_toc lt ON lt.lineId = l.id",
                "INSERT INTO alt_toc_structure SELECT * FROM src.alt_toc_structure WHERE bookId IN (SELECT id FROM keep_book)",
                "INSERT INTO alt_toc_entry SELECT * FROM src.alt_toc_entry WHERE structureId IN (SELECT id FROM alt_toc_structure)",
                "INSERT OR IGNORE INTO tocText SELECT * FROM src.tocText WHERE id IN (SELECT textId FROM alt_toc_entry)",
                "INSERT INTO line_alt_toc SELECT la.* FROM line l JOIN src.line_alt_toc la ON la.lineId = l.id",
            ),
            "מעתיק פרטי ספרים" to listOf(
                "book_acronym", "book_author", "book_topic", "book_pub_date", "book_pub_place", "book_edition", "book_has_links",
            ).map { "INSERT INTO $it SELECT * FROM src.$it WHERE bookId IN (SELECT id FROM keep_book)" } + listOf(
                """INSERT INTO default_commentator SELECT * FROM src.default_commentator
                   WHERE bookId IN (SELECT id FROM keep_book) AND commentatorBookId IN (SELECT id FROM keep_book)""",
                """INSERT INTO default_targum SELECT * FROM src.default_targum
                   WHERE bookId IN (SELECT id FROM keep_book) AND targumBookId IN (SELECT id FROM keep_book)""",
            ),
            "מעתיק קישורים" to listOf(
                """INSERT INTO link SELECT k.* FROM src.link k
                   WHERE k.sourceBookId IN (SELECT id FROM keep_book) AND k.targetBookId IN (SELECT id FROM keep_book)
                     AND k.sourceLineId IN (SELECT id FROM keep_line) AND k.targetLineId IN (SELECT id FROM keep_line)""",
            ),
        )
    }

    /**
     * Copies the documents of [src] that belong to the small library into [dst]: for the full-text index the lines
     * kept (type=line by line_id) and the books' title documents (type=book_title by book_id); for the suggestions
     * index the books (type=book) and their authors (type=author). Vector fields are dropped: no Android build uses
     * them, and keeping them would rebuild the HNSW graph.
     */
    private fun filterIndex(
        lookup: Boolean,
        src: File,
        dst: File,
        books: IntArray,
        others: IntArray,
    ) {
        if (!src.isDirectory) {
            log("חסר ${src.name} בספריית המקור, דילגתי עליו")
            return
        }
        val keep: Query =
            if (lookup) {
                either(both("type", "book", IntPoint.newSetQuery("book_id", *books)), both("type", "author", IntPoint.newSetQuery("author_id", *others)))
            } else {
                either(both("type", "line", IntPoint.newSetQuery("line_id", *others)), both("type", "book_title", IntPoint.newSetQuery("book_id", *books)))
            }
        NIOFSDirectory(src.toPath()).use { inDir ->
            DirectoryReader.open(inDir).use { reader ->
                val searcher = IndexSearcher(reader).apply { queryCache = null }
                val weight = searcher.createWeight(searcher.rewrite(keep), ScoreMode.COMPLETE_NO_SCORES, 1f)
                val parts = ArrayList<CodecReader>()
                var sort: Sort? = null
                var kept = 0L
                for (ctx in reader.leaves()) {
                    check()
                    val bits = FixedBitSet(ctx.reader().maxDoc())
                    weight.scorer(ctx)?.iterator()?.let { it ->
                        var d = it.nextDoc()
                        while (d != DocIdSetIterator.NO_MORE_DOCS) {
                            bits.set(d)
                            d = it.nextDoc()
                        }
                    }
                    ctx.reader().liveDocs?.let { live -> for (d in 0 until bits.length()) if (bits.get(d) && !live.get(d)) bits.clear(d) }
                    if (sort == null) sort = ctx.reader().metaData.sort
                    val n = bits.cardinality()
                    if (n == 0) continue
                    kept += n
                    parts.add(Kept(ctx.reader() as CodecReader, bits, n, dropVectors = !(o.keepVectors && !lookup)))
                }
                log((if (lookup) "הצעות: " else "חיפוש בטקסט: ") + "%,d רשומות מתוך %,d".format(kept, reader.numDocs()))

                dst.mkdirs()
                val cfg = IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE).setRAMBufferSizeMB(256.0)
                sort?.let { cfg.setIndexSort(it) }
                FSDirectory.open(dst.toPath()).use { outDir ->
                    val w = IndexWriter(outDir, cfg)
                    writer = w
                    try {
                        if (parts.isNotEmpty()) w.addIndexes(*parts.toTypedArray())
                        check()
                        w.forceMerge(1)
                        w.commit()
                        w.close()
                    } catch (e: Exception) {
                        if (cancelled) throw Cancelled()
                        runCatching { w.rollback() }
                        throw e
                    } finally {
                        writer = null
                    }
                }
            }
        }
        log((if (lookup) "אינדקס ההצעות: " else "אינדקס החיפוש בטקסט: ") + size(dirSize(dst)))
    }

    private fun both(field: String, value: String, ids: Query): Query =
        BooleanQuery.Builder()
            .add(TermQuery(Term(field, value)), BooleanClause.Occur.FILTER)
            .add(ids, BooleanClause.Occur.FILTER)
            .build()

    private fun either(a: Query, b: Query): Query =
        BooleanQuery.Builder().add(a, BooleanClause.Occur.SHOULD).add(b, BooleanClause.Occur.SHOULD).build()

    /** A segment seen through the kept documents only, without its vector fields. */
    private class Kept(
        input: CodecReader,
        private val live: Bits,
        private val count: Int,
        private val dropVectors: Boolean,
    ) : FilterCodecReader(input) {
        private val infos =
            if (dropVectors) FieldInfos(input.fieldInfos.filter { it.vectorDimension == 0 }.toTypedArray<FieldInfo>()) else input.fieldInfos

        override fun getLiveDocs(): Bits = live

        override fun numDocs(): Int = count

        override fun getFieldInfos(): FieldInfos = infos

        override fun getVectorReader(): KnnVectorsReader? = if (dropVectors) null else super.getVectorReader()

        override fun getCoreCacheHelper(): CacheHelper? = null

        override fun getReaderCacheHelper(): CacheHelper? = null
    }

    private fun writeInfo(books: Int, lines: Int) {
        val sb = StringBuilder()
        sb.appendLine("ספרייה מוקטנת לזית")
        sb.appendLine("נוצרה: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
        sb.appendLine("ספריית מקור: ${lib.dir} (מהדורה ${lib.version})")
        sb.appendLine("פירושים ותרגומים: " + if (o.commentaries) "כן (עד ${o.maxLines} שורות; מעל ${o.fullLines} רק שורות מקושרות)" else "לא")
        sb.appendLine("סה״כ: $books ספרים, $lines שורות")
        sb.appendLine()
        sb.appendLine("הספרים שנבחרו:")
        for (id in o.books) lib.books[id]?.let { sb.appendLine("  $id  ${it.title}  (${it.path})") }
        File(out, "subset_info.txt").writeText("﻿" + sb)
    }

    companion object {
        /** The commentaries and targumim linked to the chosen books (COMMENTARY and TARGUM links), whatever their size. */
        fun candidates(
            lib: Library,
            books: List<Long>,
            onStatement: (Statement) -> Unit,
        ): List<Long> {
            if (books.isEmpty()) return emptyList()
            val list = books.joinToString(",")
            val found = LinkedHashSet<Long>()
            Sql.openReadOnly(lib.db).use { c ->
                c.createStatement().use { s ->
                    onStatement(s)
                    s.executeQuery(
                        """SELECT DISTINCT targetBookId FROM link WHERE sourceBookId IN ($list) AND connectionTypeId IN (1,3)
                           UNION SELECT DISTINCT sourceBookId FROM link WHERE targetBookId IN ($list) AND connectionTypeId IN (1,3)""",
                    ).use { r -> while (r.next()) found.add(r.getLong(1)) }
                }
            }
            found.removeAll(books.toSet())
            return found.filter { lib.books.containsKey(it) }
        }

        /** Which books go in, and how: the limits, then the choices made by hand. */
        fun plan(
            lib: Library,
            o: Options,
            onStatement: (Statement) -> Unit,
        ): Plan {
            val cands = if (o.commentaries) candidates(lib, o.books, onStatement) else emptyList()
            val full = ArrayList(o.books)
            val linked = ArrayList<Long>()
            for (id in cands) {
                when (o.modeOf(id, lib.books[id]?.lines ?: 0)) {
                    Mode.Full -> full.add(id)
                    Mode.Linked -> linked.add(id)
                    Mode.Out -> Unit
                }
            }
            return Plan(o.books, cands, full, linked)
        }

        /**
         * How big the small library will be, per part, before building it. Measured on the source: the books kept
         * (as [previewBooks]), the text bytes of their lines, the lines of the large commentaries that are linked to a
         * chosen book, the links and the table-of-contents entries. The per-row costs below were measured with SQLite's
         * dbstat on a built library (345 books: lines 218 B + their TOC rows 57 B, links 125 B at 0.82 a line, TOC entries 103 B), and
         * the full-text index comes to about 0.6 of the text bytes (two builds: 0.57 and 0.62).
         */
        fun estimate(
            lib: Library,
            o: Options,
            plan: Plan,
            onStatement: (Statement) -> Unit,
        ): Estimate {
            val keep = plan.keep
            if (keep.isEmpty()) return Estimate()
            val full = plan.full
            val partial = plan.linked
            var lines = 0L
            var text = 0L
            var refs = 0L
            var partialLines = 0L
            var toc = 0L
            Sql.openReadOnly(lib.db).use { c ->
                c.createStatement().use { s ->
                    onStatement(s)
                    s.executeQuery(
                        "SELECT count(*), total(length(CAST(content AS BLOB))), total(length(CAST(coalesce(heRef,'') AS BLOB))) " +
                            "FROM line WHERE bookId IN (${full.joinToString(",")})",
                    ).use { r ->
                        if (r.next()) {
                            lines = r.getLong(1)
                            text = r.getDouble(2).toLong()
                            refs = r.getDouble(3).toLong()
                        }
                    }
                    if (partial.isNotEmpty()) {
                        val sel = o.books.joinToString(",")
                        val part = partial.joinToString(",")
                        partialLines =
                            long(
                                s,
                                "SELECT count(*) FROM (SELECT targetLineId FROM link WHERE sourceBookId IN ($sel) AND targetBookId IN ($part) " +
                                    "UNION SELECT sourceLineId FROM link WHERE targetBookId IN ($sel) AND sourceBookId IN ($part))",
                            )
                    }
                    toc = long(s, "SELECT count(*) FROM tocEntry WHERE bookId IN (${keep.joinToString(",")})")
                }
            }
            val perLine = if (lines > 0) text.toDouble() / lines else 800.0
            val perRef = if (lines > 0) refs.toDouble() / lines else 30.0
            val allLines = lines + partialLines
            val allText = text + (partialLines * perLine).toLong()
            val allRefs = refs + (partialLines * perRef).toLong()
            // Links are counted per line (0.82 a line in the measured library) rather than by a query over the link
            // table, which takes tens of seconds for a few hundred books.
            val db = allText + 2 * allRefs + allLines * (275 + 103) + toc * 103 + 4_900_000
            return Estimate(
                books = keep.size,
                lines = allLines,
                database = db,
                // Kept vectors (int8 + HNSW graph): 1.42 GB + 264 MB for 7.04M lines in the full index, 221 B a line in
                // a small one
                textIndex = if (o.textIndex) (allText * 0.6).toLong() + (if (o.keepVectors) allLines * 225 else 0) else 0,
                lookup = if (o.lookup) keep.size * 120L + 20_000 else 0,
                lexical = File(lib.dir, "lexical.db").length(),
                catalog = if (File(lib.dir, "catalog.pb").isFile) keep.size * 95L + 2_000 else 0,
            )
        }

        private fun ids(c: Connection, sql: String): IntArray {
            val list = ArrayList<Int>()
            c.createStatement().use { s -> s.executeQuery(sql).use { r -> while (r.next()) list.add(r.getInt(1)) } }
            return list.toIntArray()
        }

        private fun strings(s: Statement, sql: String): List<String> {
            val list = ArrayList<String>()
            s.executeQuery(sql).use { r -> while (r.next()) list.add(r.getString(1)) }
            return list
        }

        private fun long(s: Statement, sql: String): Long = s.executeQuery(sql).use { r -> if (r.next()) r.getLong(1) else 0 }
    }
}

fun dirSize(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

/** "672.0 MB", with a left-to-right mark so it keeps its order inside Hebrew. */
fun size(bytes: Long): String =
    "‎" +
        when {
            bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
            else -> "${maxOf(1, bytes / 1024)} KB"
        }
