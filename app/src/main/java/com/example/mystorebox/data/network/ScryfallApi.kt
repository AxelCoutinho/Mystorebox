package com.example.mystorebox.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class ScryfallCard(
    val id: String,
    val name: String,
    val image_uris: ImageUris?,
    val prices: Prices?
)

data class ImageUris(
    val normal: String
)

data class Prices(
    val usd: String?,
    val eur: String?
)

interface ScryfallService {
    @GET("cards/named")
    suspend fun getCardByName(@Query("fuzzy") name: String): ScryfallCard
}

object RetrofitClient {
    private const val BASE_URL = "https://api.scryfall.com/"

    val service: ScryfallService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScryfallService::class.java)
    }
}