package com.coolapk.kmp.core

interface CoolapkGateway {
    suspend fun getHomePage(
        page: Int = 1,
        firstItem: String? = null,
        lastItem: String? = null,
    ): HomePage

    suspend fun getSessionState(): SessionState

    fun setSessionCookie(cookie: String)

    fun clearSessionCookie() {
        setSessionCookie("")
    }

    suspend fun getFeedPage(
        channel: FeedChannel,
        page: Int = 1,
        cursor: HomeCursor? = null,
    ): HomePage = when (channel) {
        FeedChannel.HOME -> getHomePage(page, cursor?.firstItem, cursor?.lastItem)
        else -> error("当前平台未提供 ${channel.name} 数据接口")
    }

    suspend fun getDiscoveryPage(
        page: Int = 1,
        cursor: HomeCursor? = null,
    ): HomePage = getFeedPage(FeedChannel.DISCOVERY, page, cursor)

    suspend fun getDiscoveryConfig(): DiscoveryConfig = DiscoveryConfig(emptyList())

    suspend fun getDiscoverySectionPage(
        tab: DiscoveryTab,
        page: Int = 1,
        cursor: HomeCursor? = null,
        pageContext: String? = null,
    ): DiscoveryPage = error("当前平台未提供发现栏目接口")

    suspend fun getDigitalConfig(): DigitalConfig = DigitalConfig(emptyList())

    suspend fun getDigitalTabPage(
        tab: DigitalTab,
        page: Int = 1,
        cursor: HomeCursor? = null,
        pageContext: String? = null,
    ): DigitalPage = error("当前平台未提供数码栏目接口")

    suspend fun getDigitalBrands(): DigitalPage = error("当前平台未提供数码品牌接口")

    suspend fun getDigitalCategories(): DigitalPage = error("当前平台未提供数码分类接口")

    suspend fun getDigitalBrandProducts(
        brand: DigitalEntity,
        page: Int = 1,
        cursor: HomeCursor? = null,
    ): DigitalPage = error("当前平台未提供数码品牌产品接口")

    suspend fun getDigitalCategoryProducts(
        category: DigitalEntity,
        page: Int = 1,
        cursor: HomeCursor? = null,
    ): DigitalPage = error("当前平台未提供数码分类产品接口")

    suspend fun getDigitalProductDetail(productId: String): DigitalProductDetail =
        error("当前平台未提供数码产品详情接口")

    suspend fun getAppPage(page: Int = 1, category: String = "recommend"): AppPage =
        error("当前平台未提供应用数据接口")

    suspend fun getAppDetailRaw(packageName: String): String =
        error("当前平台未提供应用详情接口")

    suspend fun getProfile(): UserProfile? = null

    suspend fun getNotificationSummary(): NotificationSummary = NotificationSummary(0)

    suspend fun getNotificationPage(
        category: NotificationCategory,
        page: Int = 1,
    ): NotificationPage = error("当前平台未提供通知列表接口")

    suspend fun searchFeeds(query: String, page: Int = 1): HomePage =
        error("当前平台未提供搜索接口")

    suspend fun searchByType(
        query: String,
        category: SearchCategory,
        page: Int = 1,
        cursor: HomeCursor? = null,
        askFeedType: String = "all",
    ): SearchPage = error("当前平台未提供分类搜索接口")

    suspend fun getHotSearchWords(refresh: Boolean = false): List<String> = emptyList()

    suspend fun likeFeed(feedId: String, liked: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("当前平台未提供点赞接口"))

    suspend fun replyFeed(feedId: String, message: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("当前平台未提供评论接口"))

    suspend fun createFeed(message: String): Result<String> =
        Result.failure(UnsupportedOperationException("当前平台未提供发布接口"))

    suspend fun getFeedDetail(feedId: String): FeedItem =
        error("当前平台未提供动态详情接口")

    suspend fun getFeedReplies(
        feedId: String,
        page: Int = 1,
        sort: ReplySort = ReplySort.LATEST,
        authorOnly: Boolean = false,
    ): ReplyPage = error("当前平台未提供评论列表接口")

    suspend fun getSubReplies(
        feedId: String,
        replyId: String,
        page: Int = 1,
        lastItem: String? = null,
    ): ReplyPage = error("当前平台未提供楼中楼接口")

    suspend fun replyToComment(feedId: String, replyId: String, message: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("当前平台未提供回复评论接口"))

    suspend fun likeReply(replyId: String, liked: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("当前平台未提供评论点赞接口"))

    suspend fun loginWithCookie(cookie: String): SessionState =
        error("当前平台未提供 Cookie 登录接口")

    suspend fun loginWithAccessCode(code: String, cookie: String): SessionState =
        error("当前平台未提供授权码登录接口")
}
