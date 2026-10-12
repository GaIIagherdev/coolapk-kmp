package com.coolapk.kmp.core

class HomeRepository(
    private val gateway: CoolapkGateway,
) {
    suspend fun load(page: Int = 1, cursor: HomeCursor? = null): HomePage {
        return gateway.getHomePage(
            page = page,
            firstItem = cursor?.firstItem,
            lastItem = cursor?.lastItem,
        )
    }
}

data class HomeCursor(
    val firstItem: String,
    val lastItem: String,
)
