package com.coolapk.kmp.core

class HomeRepository(
    private val gateway: CoolapkGateway,
) {
    suspend fun loadSessionState(): SessionState = gateway.getSessionState()

    suspend fun load(page: Int = 1, cursor: HomeCursor? = null): HomePage {
        return gateway.getHomePage(
            page = page,
            firstItem = cursor?.firstItem,
            lastItem = cursor?.lastItem,
        )
    }

    suspend fun load(channel: FeedChannel, page: Int = 1, cursor: HomeCursor? = null): HomePage =
        gateway.getFeedPage(channel, page, cursor)

    suspend fun loadDiscoveryConfig(): DiscoveryConfig = gateway.getDiscoveryConfig()

    suspend fun loadDiscoverySection(
        tab: DiscoveryTab,
        page: Int = 1,
        cursor: HomeCursor? = null,
        pageContext: String? = null,
    ): DiscoveryPage = gateway.getDiscoverySectionPage(tab, page, cursor, pageContext)

    suspend fun loadDigitalConfig(): DigitalConfig = gateway.getDigitalConfig()

    suspend fun loadDigitalTab(
        tab: DigitalTab,
        page: Int = 1,
        cursor: HomeCursor? = null,
        pageContext: String? = null,
    ): DigitalPage = gateway.getDigitalTabPage(tab, page, cursor, pageContext)

    suspend fun loadDigitalBrands(): DigitalPage = gateway.getDigitalBrands()

    suspend fun loadDigitalCategories(): DigitalPage = gateway.getDigitalCategories()

    suspend fun loadDigitalBrandProducts(
        brand: DigitalEntity,
        page: Int = 1,
        cursor: HomeCursor? = null,
    ): DigitalPage = gateway.getDigitalBrandProducts(brand, page, cursor)

    suspend fun loadDigitalCategoryProducts(
        category: DigitalEntity,
        page: Int = 1,
        cursor: HomeCursor? = null,
    ): DigitalPage = gateway.getDigitalCategoryProducts(category, page, cursor)

    suspend fun loadDigitalProductDetail(productId: String): DigitalProductDetail =
        gateway.getDigitalProductDetail(productId)

    suspend fun loadApps(page: Int = 1, category: String = "recommend"): AppPage =
        gateway.getAppPage(page, category)

    suspend fun getAppDetailRaw(packageName: String): String = gateway.getAppDetailRaw(packageName)

    suspend fun loadProfile(): UserProfile? = gateway.getProfile()

    suspend fun loadUserProfile(uid: String): UserProfile? = gateway.getUserProfile(uid)

    suspend fun loadUserFeeds(uid: String, page: Int = 1): HomePage = gateway.getUserFeeds(uid, page)

    suspend fun loadProductFeeds(productId: String, page: Int = 1): HomePage = gateway.getProductFeeds(productId, "feed", page)

    suspend fun loadProductRatings(productId: String, star: Int = 0, ownerOnly: Boolean = false, page: Int = 1): ProductRatingPage =
        gateway.getProductRatings(productId, star, ownerOnly, page)

    suspend fun loadProductRatingChart(productId: String): ProductRatingChart = gateway.getProductRatingChart(productId)

    suspend fun setProductWish(productId: String, wish: Boolean): Result<Unit> = gateway.changeProductWishStatus(productId, wish)

    suspend fun setProductFollow(productId: String, follow: Boolean): Result<Unit> = gateway.changeProductFollowStatus(productId, follow)

    suspend fun submitProductRating(productId: String, score: Int, message: String, buyStatus: Boolean): Result<Unit> =
        gateway.createProductRating(productId, score, message, buyStatus)

    suspend fun loadUserFavorites(uid: String, page: Int = 1): HomePage = gateway.getUserFavorites(uid, page)

    suspend fun loadUserLiked(uid: String, page: Int = 1): HomePage = gateway.getUserLiked(uid, page)

    suspend fun loadTopicDetail(tag: String): TopicDetail = gateway.getTopicDetail(tag)

    suspend fun loadTopicFeeds(
        tag: String,
        page: Int = 1,
        sort: TopicFeedSort = TopicFeedSort.DEFAULT,
        cursor: HomeCursor? = null,
    ): HomePage = gateway.getTopicFeeds(tag, page, sort, cursor)

    suspend fun loadNotifications(): NotificationSummary = gateway.getNotificationSummary()

    suspend fun loadNotificationPage(
        category: NotificationCategory,
        page: Int = 1,
    ): NotificationPage = gateway.getNotificationPage(category, page)

    suspend fun search(query: String, page: Int = 1): HomePage = gateway.searchFeeds(query, page)

    suspend fun searchByType(
        query: String,
        category: SearchCategory,
        page: Int = 1,
        cursor: HomeCursor? = null,
        askFeedType: String = "all",
    ): SearchPage = gateway.searchByType(query, category, page, cursor, askFeedType)

    suspend fun loadHotSearchWords(refresh: Boolean = false): List<String> =
        gateway.getHotSearchWords(refresh)

    suspend fun likeFeed(feedId: String, liked: Boolean): Result<Unit> = gateway.likeFeed(feedId, liked)

    suspend fun replyFeed(feedId: String, message: String): Result<Unit> =
        gateway.replyFeed(feedId, message)

    suspend fun createFeed(message: String): Result<String> = gateway.createFeed(message)

    suspend fun loadFeedDetail(feedId: String): FeedItem = gateway.getFeedDetail(feedId)

    suspend fun loadFeedDetailWithToken(
        feedId: String,
        postToken: String,
        postTokenField: String,
    ): FeedItem = gateway.getFeedDetailWithToken(feedId, postToken, postTokenField)

    suspend fun loadWebFeedDetail(feedId: String): FeedItem = gateway.getWebFeedDetail(feedId)

    suspend fun loadFeedReplies(
        feedId: String,
        page: Int = 1,
        sort: ReplySort = ReplySort.LATEST,
        authorOnly: Boolean = false,
    ): ReplyPage = gateway.getFeedReplies(feedId, page, sort, authorOnly)

    suspend fun loadSubReplies(
        feedId: String,
        replyId: String,
        page: Int = 1,
        lastItem: String? = null,
    ): ReplyPage = gateway.getSubReplies(feedId, replyId, page, lastItem)

    suspend fun replyToComment(feedId: String, replyId: String, message: String): Result<Unit> =
        gateway.replyToComment(feedId, replyId, message)

    suspend fun likeReply(replyId: String, liked: Boolean): Result<Unit> =
        gateway.likeReply(replyId, liked)

    suspend fun loginWithCookie(cookie: String): SessionState = gateway.loginWithCookie(cookie)

    suspend fun loginWithAccessCode(code: String, cookie: String): SessionState =
        gateway.loginWithAccessCode(code, cookie)

    fun clearSession() = gateway.clearSessionCookie()
}

data class HomeCursor(
    val firstItem: String,
    val lastItem: String,
)
