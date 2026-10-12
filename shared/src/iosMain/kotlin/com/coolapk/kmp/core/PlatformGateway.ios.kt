package com.coolapk.kmp.core

actual fun createPlatformGateway(): CoolapkGateway = object : CoolapkGateway {
    override suspend fun getHomePage(page: Int, firstItem: String?, lastItem: String?): HomePage =
        error("iOS Rust 桥接将在下一阶段接入")
    override suspend fun getSessionState(): SessionState = SessionState(loggedIn = false)
    override fun setSessionCookie(cookie: String) = Unit
}
