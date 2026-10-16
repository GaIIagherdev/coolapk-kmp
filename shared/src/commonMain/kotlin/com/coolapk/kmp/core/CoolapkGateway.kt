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

    suspend fun followUser(uid: String, follow: Boolean): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供关注接口"))

    suspend fun setSpecialFollow(uid: String, special: Boolean): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供特别关注接口"))

    /** 问题下的回答列表；sort 为 reply（热门）、like（高赞）或 dateline（最新）。 */
    suspend fun getQuestionAnswers(
        questionId: String,
        sort: String = "reply",
        page: Int = 1,
        cursor: HomeCursor? = null,
    ): HomePage = error("当前平台未提供问题回答接口")

    /** 关注的论坛列表，需登录。 */
    suspend fun getFollowedForums(uid: String, page: Int = 1): FollowedEntryPage =
        error("当前平台未提供关注论坛接口")

    /** 关注的话题列表，需登录。 */
    suspend fun getFollowedTopics(page: Int = 1): FollowedEntryPage =
        error("当前平台未提供关注话题接口")

    /** 黑名单用户列表，需登录。 */
    suspend fun getBlackList(page: Int = 1): UserSummaryPage =
        error("当前平台未提供黑名单接口")

    suspend fun removeFromBlackList(uid: String): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供移出黑名单接口"))

    /** 关注列表：category 为 follow（全部）、special（特别关注）、friend（互相关注）。 */
    suspend fun getFollowUsers(uid: String, page: Int = 1, category: String = "follow"): UserSummaryPage =
        error("当前平台未提供关注列表接口")

    suspend fun getFansUsers(uid: String, page: Int = 1): UserSummaryPage =
        error("当前平台未提供粉丝列表接口")

    suspend fun sendPrivateMessage(uid: String, message: String): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供私信接口"))

    suspend fun removeFan(uid: String): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供移除粉丝接口"))

    suspend fun getUserQrImage(uid: String): String? = null

    suspend fun updateUserProfile(key: String, value: String): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供资料编辑接口"))

    /** 用户标签页的实体列表（图文、问答、好物榜等）。 */
    suspend fun getUserTabPage(uid: String, tab: String, page: Int = 1, firstItem: String? = null, lastItem: String? = null): DigitalPage =
        error("当前平台未提供用户标签页接口")

    /** 用户主页卡片（关注的人、关注的板块等）。 */
    suspend fun getUserHomeRows(uid: String): List<HomeCardRow> = emptyList()

    /** 用户分类动态：feedType 取 feed、reply、rating、picture、ershou、fav。 */
    suspend fun getUserFeedsTyped(uid: String, feedType: String, page: Int = 1): HomePage =
        error("当前平台未提供用户分类动态接口")

    suspend fun getRecentChats(page: Int = 1): List<ChatSession> = error("当前平台未提供私信会话接口")

    suspend fun getChatHistory(ukey: String, page: Int = 1, firstItem: String? = null, lastItem: String? = null): ChatMessagePage =
        error("当前平台未提供聊天记录接口")

    suspend fun markChatRead(ukey: String): Result<Unit> = Result.failure(IllegalStateException("当前平台未提供已读接口"))

    suspend fun deleteChat(ukey: String): Result<Unit> = Result.failure(IllegalStateException("当前平台未提供删除会话接口"))

    suspend fun getProductConfig(configId: String): ProductConfigItem? = null

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

    /** 浏览历史中的动态（其他类型暂未解析）。 */
    suspend fun getRecentHistory(page: Int = 1, cursor: HomeCursor? = null): HomePage =
        error("当前平台未提供浏览历史接口")

    /** 当前账号收藏的动态，需要登录。 */
    suspend fun getFavoriteFeeds(page: Int = 1, cursor: HomeCursor? = null): HomePage =
        error("当前平台未提供收藏动态接口")

    /** 收藏单列表；uid 为空时返回当前账号的收藏单。 */
    suspend fun getCollections(
        uid: String = "",
        page: Int = 1,
        cursor: HomeCursor? = null,
    ): FeedCollectionPage = error("当前平台未提供收藏单接口")

    suspend fun getCollectionDetail(collectionId: String): FeedCollection? =
        error("当前平台未提供收藏单详情接口")

    suspend fun getCollectionItems(
        collectionId: String,
        page: Int = 1,
        cursor: HomeCursor? = null,
    ): HomePage = error("当前平台未提供收藏单内容接口")

    suspend fun createCollection(
        title: String,
        description: String,
        cover: String,
        isOpen: Boolean,
    ): Result<Unit> = Result.failure(IllegalStateException("当前平台未提供创建收藏单接口"))

    suspend fun updateCollection(
        collectionId: String,
        title: String,
        description: String,
        cover: String,
        isOpen: Boolean,
    ): Result<Unit> = Result.failure(IllegalStateException("当前平台未提供编辑收藏单接口"))

    suspend fun deleteCollection(collectionId: String): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供删除收藏单接口"))

    /** 从收藏单移除一条内容，itemId 是收藏条目 ID，不是动态 ID。 */
    suspend fun removeCollectionItem(itemId: String): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供移出收藏接口"))

    suspend fun setCollectionFollow(collectionId: String, follow: Boolean): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供关注收藏单接口"))

    suspend fun setCollectionLike(collectionId: String, liked: Boolean): Result<Unit> =
        Result.failure(IllegalStateException("当前平台未提供点赞收藏单接口"))

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
