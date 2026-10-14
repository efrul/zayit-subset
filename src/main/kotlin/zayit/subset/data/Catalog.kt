@file:OptIn(ExperimentalSerializationApi::class)

package zayit.subset.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import java.io.File

// catalog.pb, as SeforimLibrary writes it (core/models/PrecomputedCatalog.kt at 381fbea, the commit Zayit 0.6.1-0.6.2
// pin): the book tree desktop Zayit shows. Desktop shows no book list without it; Android falls back to the database.

@Serializable
data class PrecomputedCatalog(
    @ProtoNumber(1) val rootCategories: List<CatalogCategory>,
    @ProtoNumber(2) val version: Int = 1,
    @ProtoNumber(3) val totalBooks: Int = 0,
    @ProtoNumber(4) val totalCategories: Int = 0,
)

@Serializable
data class CatalogCategory(
    @ProtoNumber(1) val id: Long,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(3) val level: Int,
    @ProtoNumber(4) val parentId: Long? = null,
    @ProtoNumber(5) val books: List<CatalogBook>,
    @ProtoNumber(6) val subcategories: List<CatalogCategory>,
)

@Serializable
data class CatalogBook(
    @ProtoNumber(1) val id: Long,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(3) val categoryId: Long,
    @ProtoNumber(4) val order: Float = 999f,
    @ProtoNumber(5) val authors: List<String> = emptyList(),
    @ProtoNumber(6) val totalLines: Int = 0,
    @ProtoNumber(7) val isBaseBook: Boolean = false,
    @ProtoNumber(8) val hasTargumConnection: Boolean = false,
    @ProtoNumber(9) val hasReferenceConnection: Boolean = false,
    @ProtoNumber(10) val hasSourceConnection: Boolean = false,
    @ProtoNumber(11) val hasCommentaryConnection: Boolean = false,
    @ProtoNumber(12) val hasOtherConnection: Boolean = false,
    @ProtoNumber(13) val hasAltStructures: Boolean = false,
)

object Catalog {
    /**
     * Writes [dst]: the catalog of [src] with only the books in [keep], and only the categories that still hold one,
     * as the generator would have written it for this library. Returns the books and categories written.
     */
    fun filter(
        src: File,
        dst: File,
        keep: Set<Long>,
    ): Pair<Int, Int> {
        val full = ProtoBuf.decodeFromByteArray<PrecomputedCatalog>(src.readBytes())
        var books = 0
        var cats = 0

        fun cut(c: CatalogCategory): CatalogCategory? {
            val kids = c.subcategories.mapNotNull { cut(it) }
            val own = c.books.filter { it.id in keep }
            if (kids.isEmpty() && own.isEmpty()) return null
            books += own.size
            cats++
            return c.copy(books = own, subcategories = kids)
        }
        val roots = full.rootCategories.mapNotNull { cut(it) }
        val small = full.copy(rootCategories = roots, totalBooks = books, totalCategories = cats)
        dst.writeBytes(ProtoBuf.encodeToByteArray(small))
        return books to cats
    }
}
