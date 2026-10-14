package com.coolapk.kmp.core

data class FeedItem(
    val id: String,
    val title: String?,
    val message: String,
    val username: String,
    val avatarUrl: String?,
    val device: String?,
    val createdAt: String?,
    val likeCount: Int,
    val replyCount: Int,
    val imageUrls: List<String>,
    val isLiked: Boolean = false,
)

data class HomePage(
    val items: List<FeedItem>,
    val firstItem: String? = null,
    val lastItem: String? = null,
    val hasMore: Boolean = items.isNotEmpty(),
)

enum class FeedChannel {
    HOME,
    HOT,
    DISCOVERY,
}

data class AppItem(
    val id: String,
    val name: String,
    val packageName: String?,
    val iconUrl: String?,
    val summary: String?,
    val version: String?,
    val score: Double?,
    val downloadCount: String?,
)

data class AppPage(
    val items: List<AppItem>,
    val hasMore: Boolean = items.isNotEmpty(),
)

data class UserProfile(
    val uid: String,
    val username: String,
    val avatarUrl: String?,
    val bio: String?,
    val feedCount: Int,
    val followingCount: Int,
    val followerCount: Int,
)

data class NotificationSummary(
    val total: Int,
)

data class SessionState(
    val loggedIn: Boolean,
    val uid: String? = null,
    val username: String? = null,
)
