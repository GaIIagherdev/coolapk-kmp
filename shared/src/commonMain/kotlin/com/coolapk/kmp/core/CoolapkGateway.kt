package com.coolapk.kmp.core

interface CoolapkGateway {
    suspend fun getHomePage(
        page: Int = 1,
        firstItem: String? = null,
        lastItem: String? = null,
    ): HomePage

    suspend fun getSessionState(): SessionState

    fun setSessionCookie(cookie: String)
}
