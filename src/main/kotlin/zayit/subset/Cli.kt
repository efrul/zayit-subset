package zayit.subset

import zayit.subset.data.Builder
import zayit.subset.data.Library
import zayit.subset.data.MoveLibrary
import zayit.subset.data.Options
import zayit.subset.data.ReplaceLibrary
import zayit.subset.ui.AppState
import java.io.File

/**
 * Builds a small library without the window, for checks: `java -cp <lib folder>\* zayit.subset.CliKt OUT_DIR [book ids...]`.
 * Without ids it builds the emulator test set (AppState.TEST_SET) with the default options.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: CliKt OUT_DIR [book ids...]" }
    if (System.getenv("REPLACE_TEST") != null) return replaceTest(File(args[0]))
    if (System.getenv("MOVE_TEST") != null) return moveTest(File(args[0]))
    val out = File(args[0])
    val ids = args.drop(1).mapNotNull { it.toLongOrNull() }.ifEmpty { AppState.TEST_SET }
    val lib = Library.load(Library.defaultDir())
    println("source: ${lib.dir} (${lib.version}), ${lib.books.size} books")
    val t0 = System.nanoTime()
    val o = Options(ids.sorted(), noLimits = System.getenv("NO_LIMITS") != null, keepVectors = System.getenv("KEEP_VECTORS") != null)
    val plan = Builder.plan(lib, o) {}
    val e = Builder.estimate(lib, o, plan) {}
    println("estimate took ${(System.nanoTime() - t0) / 1_000_000} ms")
    if (System.getenv("ESTIMATE_ONLY") != null) {
        println("estimate: books=${e.books} lines=${e.lines} db=${e.database} text=${e.textIndex} lookup=${e.lookup} catalog=${e.catalog} total=${e.total}")
        return
    }
    println("estimate: books=${e.books} lines=${e.lines} db=${e.database} text=${e.textIndex} lookup=${e.lookup} lexical=${e.lexical} catalog=${e.catalog} total=${e.total}")
    Builder(lib, o, out, log = { println(it) }, progress = { f, s -> println("[${f?.let { (it * 100).toInt() } ?: "…"}%] $s") }).run()
}

/** ReplaceLibrary on fake libraries in [dir]: a plain replacement, then one where a locked file makes it roll back. */
private fun replaceTest(dir: File) {
    fun lib(d: File, tag: String) {
        d.mkdirs()
        for (n in listOf("seforim.db", "lexical.db", "catalog.pb", "release_info.txt")) File(d, n).writeText(tag)
        File(d, "seforim.db.lucene").mkdirs()
        File(d, "seforim.db.lucene/_0.cfs").writeText(tag)
    }
    fun show(d: File) = d.listFiles()!!.sortedBy { it.name }.joinToString(" ") { f -> f.name + (if (f.isFile) "=" + f.readText() else "/") }
    dir.deleteRecursively()
    val target = File(dir, "zayit")
    lib(target, "FULL")
    File(target, "settings").mkdirs()
    File(target, "settings/user_settings.db").writeText("NOTES")
    File(target, "session").mkdirs()
    val small = File(dir, "small")
    lib(small, "SMALL")
    File(small, "subset_info.txt").writeText("SMALL")
    ReplaceLibrary.replace(small, target, { println("log: $it") }, checkZayit = false)
    println("after replace, target: " + show(target))
    println("  notes kept: " + File(target, "settings/user_settings.db").readText() + ", small left: " + show(small))

    // A file of the old library held open: Windows refuses to move its folder, so everything must go back
    lib(small, "SMALL2")
    val held = java.io.RandomAccessFile(File(target, "seforim.db.lucene/_0.cfs"), "r")
    try {
        ReplaceLibrary.replace(small, target, { println("log: $it") }, checkZayit = false)
        println("UNEXPECTED: replaced while a file was open")
    } catch (e: Exception) {
        println("failed as expected: " + e.message)
    } finally {
        held.close()
    }
    println("after failure, target: " + show(target))
    println("  small: " + show(small))
}

/** MoveLibrary on a fake library in [dir] (Zayit's setting untouched): a move, then a move stopped half way. */
private fun moveTest(dir: File) {
    dir.deleteRecursively()
    val from = File(dir, "databases").apply { mkdirs() }
    File(from, "seforim.db").writeBytes(ByteArray(70 shl 20) { (it % 251).toByte() })
    File(from, "seforim.db.lucene").mkdirs()
    File(from, "seforim.db.lucene/_0.cfs").writeBytes(ByteArray(5 shl 20) { 7 })
    File(from, "lexical.db").writeText("lex")
    File(from, "settings").mkdirs()
    File(from, "settings/user_settings.db").writeText("NOTES")
    val drive = File(dir, "external")
    val to = MoveLibrary.destination(drive)
    var last = 0L
    MoveLibrary.move(from, to, { done, _ -> last = done }, { false }, { println("log: $it") }, updateZayit = false)
    println("moved: bytes=$last to=${to.listFiles()!!.map { it.name }.sorted()} from now=${from.listFiles()!!.map { it.name }.sorted()}")
    println("  same bytes: " + File(to, "seforim.db").readBytes().contentEquals(ByteArray(70 shl 20) { (it % 251).toByte() }))

    // Back, stopped half way: the partial copy goes, the library stays where it was
    var calls = 0
    try {
        MoveLibrary.move(to, from, { _, _ -> }, { ++calls > 1 }, { println("log: $it") }, updateZayit = false)
        println("UNEXPECTED: finished")
    } catch (e: Exception) {
        println("stopped: ${e::class.simpleName}")
    }
    println("after stop: library at ${to.listFiles()!!.map { it.name }.sorted()}, target has ${from.listFiles()!!.map { it.name }.sorted()}")
}
