package com.theveloper.pixelplay.data.network.audius

import com.google.gson.annotations.SerializedName

data class AudiusSearchResponse(
    val data: List<AudiusTrack>
)

data class AudiusTrack(
    val id: String,
    val title: String,
    val duration: Int,
    @SerializedName("artwork") val artwork: AudiusArtwork?,
    @SerializedName("user") val user: AudiusUser?,
    @SerializedName("stream") val stream: AudiusStream?
)

data class AudiusArtwork(
    @SerializedName("150x150") val small: String?,
    @SerializedName("480x480") val medium: String?,
    @SerializedName("1000x1000") val large: String?
)

data class AudiusUser(
    val id: String,
    val name: String,
    val handle: String
)

data class AudiusStream(
    val url: String?
)
