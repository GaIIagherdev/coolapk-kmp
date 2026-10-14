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

    /** 我的装备：kind 为 all / wish / buy（动态）、store（商品店铺）、album（产品专辑）。 */
    suspend fun getMyGoodsPage(uid: String, kind: String, page: Int = 1): DigitalPage =
        error("当前平台未提供我的装备接口")

    suspend fun getProductFeeds(productId: String, feedType: String = "feed", page: Int = 1): HomePage =
        error("当前平台未提供产品动态接口")

    suspend fun getProductRatings(productId: String, star: Int = 0, ownerOnly: Boolean = false, page: Int = 1): ProductRatingPage =
        error("当前平台未提供产品点评接口")

    suspend fun getProductRatingChart(productId: String): ProductRatingChart =
        error("当前平台未提供产品评分趋势接口")

    suspend fun changeProductWishStatus(productId: String, wish: Boolean): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供想要接口"))

    suspend fun changeProductFollowStatus(productId: String, follow: Boolean): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供关注接口"))

    suspend fun createProductRating(productId: String, score: Int, message: String, buyStatus: Boolean): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供产品点评发布接口"))

    suspend fun getAppPage(page: Int = 1, category: String = "recommend"): AppPage =
        error("当前平台未提供应用数据接口")

    suspend fun getAppDetailRaw(packageName: String): String =
        error("当前平台未提供应用详情接口")

    suspend fun getProfile(): UserProfile? = null

    suspend fun getUserProfile(uid: String): UserProfile? =
        error("当前平台未提供用户主页接口")

    suspend fun getUserFeeds(uid: String, page: Int = 1): HomePage =
        error("当前平台未提供用户动态接口")

    suspend fun getUserFavorites(uid: String, page: Int = 1): HomePage =
        error("当前平台未提供用户收藏接口")

    suspend fun getUserLiked(uid: String, page: Int = 1): HomePage =
        error("当前平台未提供用户赞过接口")

    suspend fun getTopicDetail(tag: String): TopicDetail =
        error("当前平台未提供话题详情接口")

    suspend fun getTopicFeeds(
        tag: String,
        page: Int = 1,
        sort: TopicFeedSort = TopicFeedSort.DEFAULT,
        cursor: HomeCursor? = null,
    ): HomePage = error("当前平台未提供话题动态接口")

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

    /** 详情接口被人机验证拦截后，带上易盾令牌重试。 */
    suspend fun getFeedDetailWithToken(
        feedId: String,
        postToken: String,
        postTokenField: String,
    ): FeedItem = error("当前平台未提供人机验证重试接口")

    /** 网页版动态入口兜底，用于验证码无法通过时仍能读到完整动态。 */
    suspend fun getWebFeedDetail(feedId: String): FeedItem =
        error("当前平台未提供网页版动态详情兜底")

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
