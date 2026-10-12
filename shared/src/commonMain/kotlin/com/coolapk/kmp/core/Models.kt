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
)

data class HomePage(
    val items: List<FeedItem>,
    val firstItem: String? = null,
    val lastItem: String? = null,
    val hasMore: Boolean = items.isNotEmpty(),
)

data class SessionState(
    val loggedIn: Boolean,
    val uid: String? = null,
    val username: String? = null,
)
