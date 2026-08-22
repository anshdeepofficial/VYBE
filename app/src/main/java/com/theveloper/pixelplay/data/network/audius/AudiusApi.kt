package com.theveloper.pixelplay.data.network.audius

import retrofit2.http.GET
import retrofit2.http.Query

interface AudiusApi {
    @GET("v1/tracks/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
        @Query("app_name") appName: String = "VYBE",
        @retrofit2.http.Header("Authorization") token: String? = null
    ): AudiusSearchResponse

    @GET("v1/tracks/trending")
    suspend fun getTrendingTracks(
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
        @Query("app_name") appName: String = "VYBE",
        @retrofit2.http.Header("Authorization") token: String? = null
    ): AudiusSearchResponse
}
