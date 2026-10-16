package com.coolapk.kmp.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import uniffi.coolapk_core.CoolapkCore
import uniffi.coolapk_core.CoolapkException

class RustCoolapkGateway(
    context: Context? = null,
    private val parser: FeedJsonParser = FeedJsonParser(),
    private val appParser: AppJsonParser = AppJsonParser(),
    private val profileParser: ProfileJsonParser = ProfileJsonParser(
        currentYear = { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) },
    ),
    private val notificationParser: NotificationJsonParser = NotificationJsonParser(),
    private val discoveryParser: DiscoveryJsonParser = DiscoveryJsonParser(),
    private val digitalParser: DigitalJsonParser = DigitalJsonParser(),
    private val digitalProductParser: DigitalProductDetailJsonParser = DigitalProductDetailJsonParser(),
    private val productInteractionParser: ProductInteractionJsonParser = ProductInteractionJsonParser(),
    private val userListParser: UserListJsonParser = UserListJsonParser(),
    private val userHomeParser: UserHomeJsonParser = UserHomeJsonParser(),
    private val chatParser: ChatJsonParser = ChatJsonParser(),
    private val productConfigParser: ProductConfigJsonParser = ProductConfigJsonParser(),
    private val searchParser: SearchJsonParser = SearchJsonParser(),
    private val topicParser: TopicJsonParser = TopicJsonParser(),
    private val notificationListParser: NotificationListJsonParser = NotificationListJsonParser(),
    private val detailParser: FeedDetailJsonParser = FeedDetailJsonParser(),
    private val collectionParser: CollectionJsonParser = CollectionJsonParser(),
    private val listEntryParser: ListEntryJsonParser = ListEntryJsonParser(),
) : CoolapkGateway {
    private val sessionStore = context?.let(::SecureSessionStore)
    private val core: CoolapkCore by lazy {
        CoolapkCore().also { createdCore ->
            sessionStore?.read()?.takeIf(String::isNotBlank)?.let(createdCore::setSessionCookie)
        }
    }

    private suspend fun <T> onCore(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (error: CoolapkException.Api) {
            throw IllegalStateException(error.detail, error)
        }
    }

    override suspend fun getHomePage(
        page: Int,
        firstItem: String?,
        lastItem: String?,
    ): HomePage = onCore {
        val safePage = page.coerceAtLeast(1).toUInt()
        val raw = core.getHomePage(safePage, firstItem.orEmpty(), lastItem.orEmpty())
        parser.parseHomePage(raw)
    }

    override suspend fun getSessionState(): SessionState = onCore {
        val cookie = core.getSessionCookie()
        SessionState(
            loggedIn = core.isLoggedIn(),
            uid = cookie.cookieValue("uid"),
            username = cookie.cookieValue("username")?.decodeCookieValue(),
        )
    }

    override fun setSessionCookie(cookie: String) {
        val previousCookie = core.getSessionCookie()
        core.setSessionCookie(cookie)
        try {
            if (cookie.isBlank()) sessionStore?.clear() else sessionStore?.write(cookie)
        } catch (error: Exception) {
            core.setSessionCookie(previousCookie.orEmpty())
            throw error
        }
    }

    override fun clearSessionCookie() {
        setSessionCookie("")
    }

    override suspend fun getFeedPage(
        channel: FeedChannel,
        page: Int,
        cursor: HomeCursor?,
    ): HomePage = onCore {
        val safePage = page.coerceAtLeast(1).toUInt()
        val raw = when (channel) {
            FeedChannel.HOME -> core.getHomePage(
                safePage,
                cursor?.firstItem.orEmpty(),
                cursor?.lastItem.orEmpty(),
            )
            FeedChannel.HOT -> core.getHotFeeds(safePage)
            FeedChannel.DISCOVERY -> core.getLatestFeeds(safePage)
        }
        parser.parseHomePage(raw)
    }

    override suspend fun getDiscoveryPage(page: Int, cursor: HomeCursor?): HomePage =
        getFeedPage(FeedChannel.DISCOVERY, page, cursor)

    override suspend fun getDiscoveryConfig(): DiscoveryConfig = onCore {
        discoveryParser.parseConfig(core.getDiscoveryConfig())
    }

    override suspend fun getDiscoverySectionPage(
        tab: DiscoveryTab,
        page: Int,
        cursor: HomeCursor?,
        pageContext: String?,
    ): DiscoveryPage = onCore {
        discoveryParser.parsePage(
            core.getDiscoveryPageData(
                tab.url,
                tab.title,
                tab.subTitle.orEmpty(),
                page.coerceAtLeast(1).toUInt(),
                cursor?.firstItem.orEmpty(),
                cursor?.lastItem.orEmpty(),
                pageContext.orEmpty(),
                "{}",
            ),
        )
    }

    override suspend fun getDigitalConfig(): DigitalConfig = onCore {
        digitalParser.parseConfig(core.getTabConfig())
    }

    override suspend fun getDigitalTabPage(
        tab: DigitalTab,
        page: Int,
        cursor: HomeCursor?,
        pageContext: String?,
    ): DigitalPage = onCore {
        digitalParser.parsePage(
            core.getDiscoveryPageData(
                tab.url,
                tab.title,
                tab.subTitle.orEmpty(),
                page.coerceAtLeast(1).toUInt(),
                cursor?.firstItem.orEmpty(),
                cursor?.lastItem.orEmpty(),
                pageContext.orEmpty(),
                tab.requestArgs.toString(),
            ),
        )
    }

    override suspend fun getDigitalBrands(): DigitalPage = onCore {
        digitalParser.parsePage(core.getProductBrandList())
    }

    override suspend fun getDigitalCategories(): DigitalPage = onCore {
        digitalParser.parsePage(core.getProductCategoryList())
    }

    override suspend fun getDigitalBrandProducts(
        brand: DigitalEntity,
        page: Int,
        cursor: HomeCursor?,
    ): DigitalPage = onCore {
        val brandType = (brand.raw["type"] as? JsonPrimitive)?.contentOrNull ?: "recommend"
        digitalParser.parsePage(
            core.getProductBrandProducts(
                brand.id,
                brandType,
                page.coerceAtLeast(1).toUInt(),
                cursor?.firstItem.orEmpty(),
                cursor?.lastItem.orEmpty(),
            ),
        )
    }

    override suspend fun getDigitalCategoryProducts(
        category: DigitalEntity,
        page: Int,
        cursor: HomeCursor?,
    ): DigitalPage = onCore {
        val url = category.url?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("数码分类缺少服务端地址")
        digitalParser.parsePage(
            core.getProductList(
                url,
                category.title.orEmpty(),
                category.subTitle.orEmpty(),
                page.coerceAtLeast(1).toUInt(),
                cursor?.firstItem.orEmpty(),
                cursor?.lastItem.orEmpty(),
            ),
        )
    }

    override suspend fun getDigitalProductDetail(productId: String): DigitalProductDetail = onCore {
        digitalProductParser.parse(core.getProductDetail(productId), productId)
            ?: throw IllegalStateException("数码产品详情数据不完整")
    }

    override suspend fun getUserHomeRows(uid: String): List<HomeCardRow> = onCore {
        userHomeParser.parseRows(core.getUserSpace(uid))
    }

    override suspend fun getUserTabPage(uid: String, tab: String, page: Int, firstItem: String?, lastItem: String?): DigitalPage = onCore {
        digitalParser.parsePage(
            core.getUserTabData(uid, tab, page.coerceAtLeast(1).toUInt(), firstItem.orEmpty(), lastItem.orEmpty()),
        )
    }

    override suspend fun getProductConfig(configId: String): ProductConfigItem? = onCore {
        productConfigParser.parse(core.getProductConfig(configId), configId)
    }

    override suspend fun getRecentChats(page: Int): List<ChatSession> = onCore {
        chatParser.parseSessions(core.getRecentChatUsers(page.coerceAtLeast(1).toUInt()))
    }

    override suspend fun getChatHistory(ukey: String, page: Int, firstItem: String?, lastItem: String?): ChatMessagePage = onCore {
        chatParser.parseHistory(core.listChatHistory(ukey, page.coerceAtLeast(1).toUInt(), firstItem.orEmpty(), lastItem.orEmpty()))
    }

    override suspend fun markChatRead(ukey: String): Result<Unit> = runCatching {
        onCore<Unit> { core.readMessage(ukey) }
    }

    override suspend fun deleteChat(ukey: String): Result<Unit> = runCatching {
        onCore<Unit> { core.deleteMessageChat(ukey) }
    }

    override suspend fun getUserFeedsTyped(uid: String, feedType: String, page: Int): HomePage = onCore {
        parser.parseHomePage(core.getUserFeedsTyped(uid, page.coerceAtLeast(1).toUInt(), feedType))
    }

    override suspend fun followUser(uid: String, follow: Boolean): Result<Unit> = runCatching {
        onCore<Unit> { if (follow) core.followUser(uid) else core.unfollowUser(uid) }
    }

    override suspend fun setSpecialFollow(uid: String, special: Boolean): Result<Unit> = runCatching {
        onCore<Unit> { core.specialFollowUser(uid, special) }
    }

    override suspend fun getQuestionAnswers(
        questionId: String,
        sort: String,
        page: Int,
        cursor: HomeCursor?,
    ): HomePage = onCore {
        parser.parseHomePage(
            core.getQuestionAnswers(
                questionId,
                sort,
                page.coerceAtLeast(1).toUInt(),
                cursor?.firstItem.orEmpty(),
                cursor?.lastItem.orEmpty(),
            ),
        )
    }

    override suspend fun getEvents(page: Int): ListEntryPage = onCore {
        listEntryParser.parsePage(core.getEventList(page.coerceAtLeast(1).toUInt()))
    }

    override suspend fun getFollowedForums(uid: String, page: Int): ListEntryPage = onCore {
        listEntryParser.parsePage(core.getUserForumFollowList(uid, page.coerceAtLeast(1).toUInt()))
    }

    override suspend fun getFollowedTopics(page: Int): ListEntryPage = onCore {
        listEntryParser.parsePage(core.getFollowedTopics(page.coerceAtLeast(1).toUInt()))
    }

    override suspend fun getBlackList(page: Int): UserSummaryPage = onCore {
        userListParser.parsePage(core.getBlackList(page.coerceAtLeast(1).toUInt()))
    }

    override suspend fun removeFromBlackList(uid: String): Result<Unit> = runCatching {
        onCore<Unit> { core.removeFromBlackList(uid) }
    }

    override suspend fun getFollowUsers(uid: String, page: Int, category: String): UserSummaryPage = onCore {
        userListParser.parsePage(core.getFollowUserList(uid, page.coerceAtLeast(1).toUInt(), category))
    }

    override suspend fun getFansUsers(uid: String, page: Int): UserSummaryPage = onCore {
        userListParser.parsePage(core.getFansUserList(uid, page.coerceAtLeast(1).toUInt()))
    }

    override suspend fun sendPrivateMessage(uid: String, message: String): Result<Unit> = runCatching {
        onCore<Unit> { core.sendPrivateMessage(uid, message.trim()) }
    }

    override suspend fun removeFan(uid: String): Result<Unit> = runCatching {
        onCore<Unit> { core.cancelFollower(uid) }
    }

    override suspend fun getUserQrImage(uid: String): String? = onCore {
        userListParser.parseQrImage(core.getUserQrImage(uid))
    }

    override suspend fun updateUserProfile(key: String, value: String): Result<Unit> = runCatching {
        onCore<Unit> { core.updateUserProfile(key, value) }
    }

    override suspend fun getMyGoodsPage(uid: String, kind: String, page: Int): DigitalPage = onCore {
        val safePage = page.coerceAtLeast(1).toUInt()
        val raw = when (kind) {
            "store" -> core.getGoodsStoreItems(uid, safePage)
            "album" -> core.getProductAlbums(uid, safePage)
            else -> core.getMyGoodsFeeds(uid, kind, safePage)
        }
        digitalParser.parsePage(raw)
    }

    override suspend fun getProductFeeds(productId: String, feedType: String, page: Int): HomePage = onCore {
        parser.parseHomePage(core.getProductFeeds(productId, feedType, "", page.coerceAtLeast(1).toUInt()))
    }

    override suspend fun getProductRatings(productId: String, star: Int, ownerOnly: Boolean, page: Int): ProductRatingPage = onCore {
        productInteractionParser.parseRatings(
            core.getProductRatingList(productId, star, if (ownerOnly) 1 else 0, page.coerceAtLeast(1).toUInt()),
        )
    }

    override suspend fun getProductRatingChart(productId: String): ProductRatingChart = onCore {
        productInteractionParser.parseChart(core.getProductRatingChart(productId))
    }

    override suspend fun changeProductWishStatus(productId: String, wish: Boolean): Result<Unit> = runCatching {
        onCore<Unit> { core.changeProductWishStatus(productId, if (wish) 1 else 0) }
    }

    override suspend fun changeProductFollowStatus(productId: String, follow: Boolean): Result<Unit> = runCatching {
        onCore<Unit> { core.changeProductFollowStatus(productId, if (follow) 1 else 0) }
    }

    override suspend fun createProductRating(productId: String, score: Int, message: String, buyStatus: Boolean): Result<Unit> = runCatching {
        onCore<Unit> { core.createProductRating(productId, score, message.trim(), buyStatus) }
    }

    override suspend fun getAppPage(page: Int, category: String): AppPage = onCore {
        appParser.parsePage(core.getAppList(page.coerceAtLeast(1).toUInt(), category))
    }

    override suspend fun getAppDetailRaw(packageName: String): String = onCore {
        core.getAppDetail(packageName)
    }

    override suspend fun getProfile(): UserProfile? = onCore {
        val uid = core.getSessionCookie()?.cookieValue("uid")?.takeIf(String::isNotBlank)
            ?: return@onCore null
        profileParser.parse(core.getMyProfile(uid))
    }

    override suspend fun getUserProfile(uid: String): UserProfile? = onCore {
        profileParser.parse(core.getUserProfile(uid))
    }

    override suspend fun getUserFeeds(uid: String, page: Int): HomePage = onCore {
        parser.parseHomePage(core.getUserFeeds(uid, page.coerceAtLeast(1).toUInt()))
    }

    override suspend fun getUserFavorites(uid: String, page: Int): HomePage = onCore {
        parser.parseHomePage(core.getUserFavorites(uid, page.coerceAtLeast(1).toUInt()))
    }

    override suspend fun getUserLiked(uid: String, page: Int): HomePage = onCore {
        parser.parseHomePage(core.getUserLiked(uid, page.coerceAtLeast(1).toUInt()))
    }

    override suspend fun getRecentHistory(page: Int, cursor: HomeCursor?): HomePage = onCore {
        parser.parseHomePage(
            core.getRecentHistory(
                page.coerceAtLeast(1).toUInt(),
                cursor?.firstItem.orEmpty(),
                cursor?.lastItem.orEmpty(),
            ),
        )
    }

    override suspend fun getFavoriteFeeds(page: Int, cursor: HomeCursor?): HomePage = onCore {
        parser.parseHomePage(
            core.getFavoriteList(
                "feed",
                page.coerceAtLeast(1).toUInt(),
                cursor?.firstItem.orEmpty(),
                cursor?.lastItem.orEmpty(),
            ),
        )
    }

    override suspend fun getCollections(uid: String, page: Int, cursor: HomeCursor?): FeedCollectionPage = onCore {
        collectionParser.parsePage(
            core.getCollectionList(
                uid,
                page.coerceAtLeast(1).toUInt(),
                cursor?.firstItem.orEmpty(),
                cursor?.lastItem.orEmpty(),
            ),
        )
    }

    override suspend fun getCollectionDetail(collectionId: String): FeedCollection? = onCore {
        collectionParser.parseDetail(core.getCollectionDetail(collectionId), collectionId)
    }

    override suspend fun getCollectionItems(collectionId: String, page: Int, cursor: HomeCursor?): HomePage = onCore {
        parser.parseHomePage(
            core.getCollectionItemList(
                collectionId,
                page.coerceAtLeast(1).toUInt(),
                cursor?.firstItem.orEmpty(),
                cursor?.lastItem.orEmpty(),
            ),
        )
    }

    override suspend fun createCollection(
        title: String,
        description: String,
        cover: String,
        isOpen: Boolean,
    ): Result<Unit> = runCatching {
        onCore<Unit> { core.createCollection(title, description, cover, if (isOpen) 1 else 0, "") }
    }

    override suspend fun updateCollection(
        collectionId: String,
        title: String,
        description: String,
        cover: String,
        isOpen: Boolean,
    ): Result<Unit> = runCatching {
        onCore<Unit> { core.updateCollection(collectionId, title, description, cover, if (isOpen) 1 else 0) }
    }

    override suspend fun deleteCollection(collectionId: String): Result<Unit> = runCatching {
        onCore<Unit> { core.deleteCollection(collectionId) }
    }

    override suspend fun removeCollectionItem(itemId: String): Result<Unit> = runCatching {
        onCore<Unit> { core.removeCollectionItem(itemId) }
    }

    override suspend fun setCollectionFollow(collectionId: String, follow: Boolean): Result<Unit> = runCatching {
        onCore<Unit> {
            if (follow) core.followCollection(collectionId) else core.unfollowCollection(collectionId)
        }
    }

    override suspend fun setCollectionLike(collectionId: String, liked: Boolean): Result<Unit> = runCatching {
        onCore<Unit> {
            if (liked) core.likeCollection(collectionId) else core.unlikeCollection(collectionId)
        }
    }

    override suspend fun getTopicDetail(tag: String): TopicDetail = onCore {
        topicParser.parseDetail(core.getTopicDetail(tag), tag)
    }

    override suspend fun getTopicFeeds(
        tag: String,
        page: Int,
        sort: TopicFeedSort,
        cursor: HomeCursor?,
    ): HomePage = onCore {
        parser.parseHomePage(core.getTopicFeeds(
            tag,
            page.coerceAtLeast(1).toUInt(),
            sort.apiValue,
            cursor?.firstItem.orEmpty(),
            cursor?.lastItem.orEmpty(),
        ))
    }

    override suspend fun getNotificationSummary(): NotificationSummary = onCore {
        notificationParser.parse(core.getNotificationCount())
    }

    override suspend fun getNotificationPage(
        category: NotificationCategory,
        page: Int,
    ): NotificationPage = onCore {
        notificationListParser.parsePage(
            core.getNotifications(category.apiValue, page.coerceAtLeast(1).toUInt()),
            category,
        )
    }

    override suspend fun searchFeeds(query: String, page: Int): HomePage = onCore {
        parser.parseHomePage(core.searchFeeds(query.trim(), page.coerceAtLeast(1).toUInt()))
    }

    override suspend fun searchByType(
        query: String,
        category: SearchCategory,
        page: Int,
        cursor: HomeCursor?,
        askFeedType: String,
    ): SearchPage = onCore {
        searchParser.parsePage(
            core.searchByType(
                category.apiValue,
                query.trim(),
                page.coerceAtLeast(1).toUInt(),
                cursor?.firstItem.orEmpty(),
                cursor?.lastItem.orEmpty(),
                askFeedType,
            ),
            category,
        )
    }

    override suspend fun getHotSearchWords(refresh: Boolean): List<String> = onCore {
        searchParser.parseHotWords(core.getHotSearches(refresh))
    }

    override suspend fun likeFeed(feedId: String, liked: Boolean): Result<Unit> = runCatching {
        onCore<Unit> {
            if (liked) core.likeFeed(feedId) else core.unlikeFeed(feedId)
        }
    }

    override suspend fun replyFeed(feedId: String, message: String): Result<Unit> = runCatching {
        onCore<Unit> {
            core.replyFeed(feedId, message)
        }
    }

    override suspend fun createFeed(message: String): Result<String> = runCatching {
        onCore {
            val trimmed = message.trim()
            require(trimmed.isNotEmpty()) { "动态内容不能为空" }
            val root = Json.parseToJsonElement(core.createFeed(trimmed)) as? JsonObject
                ?: error("发布接口返回格式无效")
            val data = root["data"] as? JsonObject
                ?: error("发布接口未返回动态数据")
            data["id"]?.let { value ->
                (value as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            } ?: error("发布接口未返回动态 ID")
        }
    }

    override suspend fun getFeedDetail(feedId: String): FeedItem = onCore {
        detailParser.parseDetail(core.getFeedDetail(feedId))
    }

    override suspend fun getFeedDetailWithToken(
        feedId: String,
        postToken: String,
        postTokenField: String,
    ): FeedItem = onCore {
        detailParser.parseDetail(core.getFeedDetailWithToken(feedId, postToken, postTokenField))
    }

    override suspend fun getWebFeedDetail(feedId: String): FeedItem = onCore {
        val body = FeedDetailFallback.extractWebDetailJson(
            core.fetchExternalPage(FeedDetailFallback.webDetailUrl(feedId)),
        )
        // 动态本身不可见时，服务端的说明比解析器的「缺少有效内容」有用得多
        FeedDetailFallback.webDetailServerMessage(body)?.let { reason ->
            throw FeedUnavailableException(reason)
        }
        detailParser.parseDetail(body)
    }

    override suspend fun getFeedReplies(
        feedId: String,
        page: Int,
        sort: ReplySort,
        authorOnly: Boolean,
    ): ReplyPage = onCore {
        detailParser.parseReplies(
            core.getFeedReplies(
                feedId,
                page.coerceAtLeast(1).toUInt(),
                if (authorOnly) "" else sort.apiValue,
                authorOnly,
            ),
        )
    }

    override suspend fun getSubReplies(
        feedId: String,
        replyId: String,
        page: Int,
        lastItem: String?,
    ): ReplyPage = onCore {
        detailParser.parseReplies(
            core.getSubReplies(feedId, replyId, page.coerceAtLeast(1).toUInt(), lastItem.orEmpty()),
        )
    }

    override suspend fun replyToComment(
        feedId: String,
        replyId: String,
        message: String,
    ): Result<Unit> = runCatching {
        onCore<Unit> { core.replyToComment(feedId, replyId, message) }
    }

    override suspend fun likeReply(replyId: String, liked: Boolean): Result<Unit> = runCatching {
        onCore<Unit> {
            if (liked) core.likeReply(replyId) else core.unlikeReply(replyId)
        }
    }

    override suspend fun loginWithCookie(cookie: String): SessionState = onCore {
        val previousCookie = core.getSessionCookie()
        core.loginByWebviewCookie(cookie.trim())
        persistVerifiedSession(previousCookie)
        getSessionState()
    }

    override suspend fun loginWithAccessCode(code: String, cookie: String): SessionState = onCore {
        val previousCookie = core.getSessionCookie()
        core.loginByAccessCode(code.trim(), cookie.trim())
        persistVerifiedSession(previousCookie)
        getSessionState()
    }

    private fun persistVerifiedSession(previousCookie: String?) {
        try {
            val verifiedCookie = core.getSessionCookie()
                ?: error("已验证登录缺少会话凭据")
            sessionStore?.write(verifiedCookie)
        } catch (error: Exception) {
            core.setSessionCookie(previousCookie.orEmpty())
            throw error
        }
    }
}

private class SecureSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun write(value: String) {
        if (value.isBlank()) {
            clear()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(KEY_COOKIE, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "无法保存加密登录凭据"
        }
    }

    fun read(): String? = runCatching {
        val encoded = preferences.getString(KEY_COOKIE, null) ?: return null
        val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val payload = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        String(cipher.doFinal(payload), Charsets.UTF_8)
    }.getOrNull()

    fun clear() {
        check(preferences.edit().remove(KEY_COOKIE).commit()) {
            "无法清除加密登录凭据"
        }
    }

    private fun secretKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "coolapk_kmp_session"
        const val PREFERENCES = "coolapk_kmp_secure_session"
        const val KEY_COOKIE = "encrypted_cookie"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }
}

private fun String?.cookieValue(name: String): String? = this
    ?.split(';')
    ?.asSequence()
    ?.map(String::trim)
    ?.mapNotNull { part ->
        val separator = part.indexOf('=')
        if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
    }
    ?.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
    ?.second

private fun String.decodeCookieValue(): String = runCatching {
    java.net.URLDecoder.decode(this, Charsets.UTF_8.name())
}.getOrDefault(this)
