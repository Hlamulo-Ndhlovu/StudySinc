package com.studysync.data.remote

import com.studysync.data.LibraryBook
import com.studysync.data.local.LibraryBookEntity
import com.studysync.data.local.StudySyncDatabase
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class LibrarySearch(
    val books: List<LibraryBook>,
    val fromCache: Boolean,
    val error: String?
)

/**
 * Reads the Open Library REST API and keeps the last results in Room so the
 * catalogue still opens when the phone is offline.
 */
class LibraryRepository(database: StudySyncDatabase) {

    private val dao = database.libraryBookDao()

    private val api: OpenLibraryApi = Retrofit.Builder()
        .baseUrl("https://openlibrary.org/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "StudySync/1.0 (educational Android app)")
                        .build()
                    chain.proceed(request)
                }
                .build()
        )
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenLibraryApi::class.java)

    suspend fun search(query: String, online: Boolean): LibrarySearch {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return LibrarySearch(emptyList(), fromCache = false, error = null)
        if (!online) {
            return LibrarySearch(loadCache(trimmed), fromCache = true, error = null)
        }
        return try {
            val response = api.search(trimmed, limit = 15)
            val books = response.docs.orEmpty().mapNotNull { it.toBook() }.distinctBy { it.key }
            cache(trimmed, books)
            LibrarySearch(books, fromCache = false, error = null)
        } catch (e: Exception) {
            val cached = loadCache(trimmed)
            LibrarySearch(
                books = cached,
                fromCache = cached.isNotEmpty(),
                error = if (cached.isEmpty()) e.message ?: "Could not reach the library API" else null
            )
        }
    }

    private suspend fun loadCache(query: String): List<LibraryBook> {
        val exact = dao.forQuery(query.lowercase())
        val rows = if (exact.isNotEmpty()) exact else dao.recent()
        return rows.map { it.toBook() }
    }

    private suspend fun cache(query: String, books: List<LibraryBook>) {
        val key = query.lowercase()
        val now = System.currentTimeMillis()
        dao.deleteQuery(key)
        if (books.isEmpty()) return
        dao.insertAll(
            books.map { book ->
                LibraryBookEntity(
                    id = "$key|${book.key}",
                    bookKey = book.key,
                    searchQuery = key,
                    title = book.title,
                    authors = book.authors,
                    year = book.year,
                    coverUrl = book.coverUrl,
                    cachedAt = now
                )
            }
        )
    }

    private fun OpenLibraryDoc.toBook(): LibraryBook? {
        val id = key?.takeIf { it.isNotBlank() } ?: return null
        val bookTitle = title?.takeIf { it.isNotBlank() } ?: return null
        return LibraryBook(
            key = id,
            title = bookTitle,
            authors = authorName?.filter { it.isNotBlank() }?.joinToString(", ").orEmpty(),
            year = firstPublishYear?.toString().orEmpty(),
            coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }.orEmpty()
        )
    }

    private fun LibraryBookEntity.toBook() = LibraryBook(
        key = bookKey,
        title = title,
        authors = authors,
        year = year,
        coverUrl = coverUrl
    )
}
