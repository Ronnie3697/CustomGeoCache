package com.customgeocache.app.data.network.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchResponseDto(
    val results: List<CacheResultDto>?,
    val total: Int?
)

@JsonClass(generateAdapter = true)
data class CacheResultDto(
    val code: String,
    val name: String?,
    val geocacheType: Int?,        // 2 = Traditional, 3 = Multi, 8 = Mystery, ...
    val containerType: Int?,       // 1=Not chosen, 2=Micro, 3=Regular, 4=Large, 5=Virtual, 6=Other, 8=Small
    val difficulty: Float?,
    val terrain: Float?,
    val postedCoordinates: Coords?,
    val userCorrectedCoordinates: Coords?,
    val premiumOnly: Boolean?,
    val userFound: Boolean?,
    val userDidNotFind: Boolean?,
    val cacheStatus: Int?,         // 0=active, 1=disabled, 2=archived
    val placedDate: String?,
    val lastFoundDate: String?,
    val favoritePoints: Int?,
    val owner: Owner?
)

@JsonClass(generateAdapter = true)
data class Coords(val latitude: Double, val longitude: Double)

@JsonClass(generateAdapter = true)
data class Owner(val code: String?, val username: String?)
