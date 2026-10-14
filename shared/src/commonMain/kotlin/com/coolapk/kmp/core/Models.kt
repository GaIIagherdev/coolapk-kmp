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
    val uid: String? = null,
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
    val feedCount: Int?,
    val followingCount: Int,
    val followerCount: Int,
    val cover: String? = null,
    val level: Int? = null,
    val exp: Int? = null,
    val maxExp: Int? = null,
    /** 获赞数（be_like_num）。 */
    val likeCount: Int? = null,
    /** 1 为男，0 或 2 为女，其余为未公开。 */
    val gender: Int? = null,
    /** 年龄段标签，如 “95后”。 */
    val ageTag: String? = null,
    val astro: String? = null,
    /** 生日展示文案，如 “1995年3月2日 (31岁)”。 */
    val birthLabel: String? = null,
    val city: String? = null,
    val province: String? = null,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val birthDay: Int? = null,
    val ipLocation: String? = null,
    /** 最近活跃时间（Unix 秒）。 */
    val loginTime: Long? = null,
    /** 注册时间（Unix 秒）。 */
    val regDate: Long? = null,
    val verifyTitle: String? = null,
    val isDeveloper: Boolean = false,
    val isFollow: Boolean = false,
    val isSpecialFollow: Boolean = false,
    val isFans: Boolean = false,
    val productOwnerCount: Int? = null,
    val goodsCount: Int? = null,
)

data class NotificationSummary(
    val total: Int,
)

data class SessionState(
    val loggedIn: Boolean,
    val uid: String? = null,
    val username: String? = null,
)
