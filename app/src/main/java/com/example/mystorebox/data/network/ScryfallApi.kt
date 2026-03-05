package com.example.mystorebox.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import okhttp3.OkHttpClient
import retrofit2.http.Url

data class ScryfallSearchResponse(
    val data: List<ScryfallCard>
)

data class ScryfallCard(
    val id: String,
    val name: String,
    val set: String,
    val collector_number: String,
    val image_uris: ImageUris?,
    val prices: Prices?,
    val prints_search_uri: String?
)

data class ImageUris(
    val normal: String,
    val small: String?
)

data class Prices(
    val usd: String?,
    val eur: String?
)

interface ScryfallService {
    @GET("cards/named")
    suspend fun getCardByName(
        @Query("fuzzy") name: String,
        @Query("set") set: String? = null
    ): ScryfallCard

    @GET
    suspend fun getCardPrints(@Url printsUrl: String): ScryfallSearchResponse
}

object RetrofitClient {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "MyStoreBoxApp/1.0")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    val service: ScryfallService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.scryfall.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScryfallService::class.java)
    }
}