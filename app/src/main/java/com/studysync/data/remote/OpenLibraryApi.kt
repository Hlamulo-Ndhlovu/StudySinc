package com.studysync.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Public catalogue used as the app's REST API. No key is required.
 * https://openlibrary.org/dev/docs/api/search
 */
interface OpenLibraryApi {
    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int,
        @Query("fields") fields: String = "key,title,author_name,first_publish_year,cover_i"
    ): OpenLibrarySearchResponse
}

data class OpenLibrarySearchResponse(
    @SerializedName("docs") val docs: List<OpenLibraryDoc>? = null
)

data class OpenLibraryDoc(
    val key: String? = null,
    val title: String? = null,
    @SerializedName("author_name") val authorName: List<String>? = null,
    @SerializedName("first_publish_year") val firstPublishYear: Int? = null,
    @SerializedName("cover_i") val coverId: Int? = null
)
