use crate::coolapk::client::CoolapkClient;
use std::sync::Arc;
use tokio::runtime::Runtime;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoolapkError {
    #[error("{detail}")]
    Api { detail: String },
}

impl From<String> for CoolapkError {
    fn from(message: String) -> Self {
        Self::Api { detail: message }
    }
}

impl From<serde_json::Error> for CoolapkError {
    fn from(error: serde_json::Error) -> Self {
        Self::Api {
            detail: format!("JSON 序列化失败: {error}"),
        }
    }
}

#[derive(uniffi::Object)]
pub struct CoolapkCore {
    client: Arc<CoolapkClient>,
    runtime: Arc<Runtime>,
}

#[uniffi::export]
impl CoolapkCore {
    #[uniffi::constructor]
    pub fn new() -> Result<Arc<Self>, CoolapkError> {
        let runtime = Runtime::new().map_err(|error| CoolapkError::Api {
            detail: format!("创建异步运行时失败: {error}"),
        })?;
        Ok(Arc::new(Self {
            client: Arc::new(CoolapkClient::new()),
            runtime: Arc::new(runtime),
        }))
    }

    /// 首页卡片实体接口。客户端负责把服务端 JSON 转换成领域模型。
    pub fn get_home_page(
        &self,
        page: u32,
        first_item: String,
        last_item: String,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(
            self.client
                .get_index_v8_entities_paged(page, &first_item, &last_item),
        )?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 兼容只关心动态列表的调用方。
    pub fn get_home_feeds(&self, page: u32) -> Result<String, CoolapkError> {
        let value = self
            .runtime
            .block_on(self.client.get_index_v8_feeds(page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 热榜动态列表。
    pub fn get_hot_feeds(&self, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_hot_feeds(page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 科技快讯列表。
    pub fn get_latest_feeds(&self, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_latest_feeds(page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 热门话题列表。
    pub fn get_hot_topics(&self) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_hot_topics())?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_topic_detail(&self, tag: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_topic_detail(&tag))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_topic_feeds(
        &self,
        tag: String,
        page: u32,
        list_type: String,
        first_item: String,
        last_item: String,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_topic_feeds(
            &tag,
            page,
            &list_type,
            &first_item,
            &last_item,
            0,
        ))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 应用中心列表。
    pub fn get_app_list(&self, page: u32, category: String) -> Result<String, CoolapkError> {
        let value = self
            .runtime
            .block_on(self.client.get_app_list(page, &category))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 发现页动态列表。
    pub fn get_discovery_page_data(
        &self,
        url: String,
        title: String,
        sub_title: String,
        page: u32,
        first_item: String,
        last_item: String,
        page_context: String,
        request_args_json: String,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_discovery_page_data(
            &url,
            &title,
            &sub_title,
            page,
            &first_item,
            &last_item,
            &page_context,
            &request_args_json,
        ))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 发现频道服务端栏目配置。
    pub fn get_discovery_config(&self) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_discovery_config())?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 首页初始化响应同时包含数码服务端栏目配置。
    pub fn get_tab_config(&self) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_tab_config())?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_product_brand_list(&self) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_brand_list())?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_product_category_list(&self) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_category_list())?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_product_list(
        &self,
        url: String,
        title: String,
        sub_title: String,
        page: u32,
        first_item: String,
        last_item: String,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_list(
            &url,
            &title,
            &sub_title,
            page,
            &first_item,
            &last_item,
        ))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_product_brand_products(
        &self,
        brand_id: String,
        brand_type: String,
        page: u32,
        first_item: String,
        last_item: String,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_brand_products(
            &brand_id,
            &brand_type,
            page,
            &first_item,
            &last_item,
        ))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_product_detail(&self, product_id: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_detail(&product_id))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 产品版本列表（型号/版本切换）。
    pub fn get_product_versions(&self, product_id: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_versions(&product_id))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 产品下的动态（点评、讨论等）；list_type 为空时使用服务端默认排序。
    pub fn get_product_feeds(
        &self,
        product_id: String,
        feed_type: String,
        list_type: String,
        page: u32,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_feeds(&product_id, &feed_type, &list_type, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 产品子标签下的动态。
    pub fn get_product_subtab_feeds(&self, product_id: String, sub_id: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_subtab_feeds(&product_id, &sub_id, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 产品图片/视频列表。
    pub fn get_product_media_list(
        &self,
        product_id: String,
        media_type: String,
        is_recommend: i32,
        page: u32,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_media_list(&product_id, &media_type, is_recommend, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 产品点评列表；star=0 表示全部星级，is_owner=1 只看拥有者。
    pub fn get_product_rating_list(
        &self,
        product_id: String,
        star: i32,
        is_owner: i32,
        page: u32,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_rating_list(&product_id, star, is_owner, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 产品评分趋势图数据。
    pub fn get_product_rating_chart(&self, product_id: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_rating_chart(&product_id))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 更新个人资料字段（如 bio、gender、birthday、location）；key 为空时 value 为 JSON 对象。
    pub fn update_user_profile(&self, key: String, value: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.update_user_profile(&key, &value))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 个人二维码名片图片。
    pub fn get_user_qr_image(&self, uid: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_user_qr_image(&uid))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 关注某个用户。
    pub fn follow_user(&self, uid: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.follow_user(&uid))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 取消关注某个用户。
    pub fn unfollow_user(&self, uid: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.unfollow_user(&uid))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 设置或取消特别关注。
    pub fn special_follow_user(&self, uid: String, special: bool) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.special_follow_user(&uid, special))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 发送私信（文字）。
    pub fn send_private_message(&self, uid: String, message: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.send_private_message(&uid, &message))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 移除粉丝。
    pub fn cancel_follower(&self, uid: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.cancel_follower(&uid))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 关注列表：category 为 follow（全部）、special（特别关注）或 friend（互相关注）；空字符串视为全部。
    pub fn get_follow_user_list(&self, uid: String, page: u32, category: String) -> Result<String, CoolapkError> {
        let category = if category.is_empty() { None } else { Some(category.as_str()) };
        let value = self.runtime.block_on(self.client.get_follow_user_list(&uid, page, category))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 粉丝列表。
    pub fn get_fans_user_list(&self, uid: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_fans_user_list(&uid, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 产品的想要名单（分页）。
    pub fn get_product_wish_list(&self, product_id: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_wish_list(&product_id, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_product_buy_list(&self, product_id: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_buy_list(&product_id, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 我的装备动态：goods_type 为 all / wish（想买）/ buy（买过）。
    pub fn get_my_goods_feeds(&self, uid: String, goods_type: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_my_goods_feeds(&uid, &goods_type, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 我的商品店铺列表。
    pub fn get_goods_store_items(&self, uid: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_goods_store_items(&uid, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 我的产品专辑列表。
    pub fn get_product_albums(&self, uid: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_product_albums(&uid, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 当前用户的产品清单（想要/已拥有等）。
    pub fn get_my_product_list(&self, uid: String, product_type: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_my_product_list(&uid, &product_type, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 设置想要（status=1）或取消想要（status=0）。
    pub fn change_product_wish_status(&self, product_id: String, status: i32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.change_product_wish_status(&product_id, status))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 关注或取消关注产品。
    pub fn change_product_follow_status(&self, product_id: String, status: i32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.change_product_follow_status(&product_id, status))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 发表产品点评（1–5 星）。
    pub fn create_product_rating(
        &self,
        product_id: String,
        score: i32,
        message: String,
        buy_status: bool,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.create_product_rating(&product_id, score, &message, buy_status))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 发布一条不带媒体的普通动态。验证码或风控失败直接返回服务端错误，交由界面提示。
    pub fn create_feed(&self, message: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.create_feed_with_options(
            message.trim(),
            None,
            None,
            None,
        ))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 应用详情保留原始实体，交由共享层按界面需要解析。
    pub fn get_app_detail(&self, package_name: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_app_detail(&package_name))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_feed_detail(&self, feed_id: String) -> Result<String, CoolapkError> {
        let value = self
            .runtime
            .block_on(self.client.get_feed_detail(&feed_id, None, None))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 动态详情被人机验证拦截后，带上易盾令牌重试。
    ///
    /// `post_token_field` 由服务端在验证码响应里下发，缺省为 `_v2_post_token`；
    /// 传空字符串表示不携带该字段，行为与 [`Self::get_feed_detail`] 一致。
    pub fn get_feed_detail_with_token(
        &self,
        feed_id: String,
        post_token: String,
        post_token_field: String,
    ) -> Result<String, CoolapkError> {
        let token = post_token.trim();
        let field = post_token_field.trim();
        let value = self.runtime.block_on(self.client.get_feed_detail(
            &feed_id,
            (!token.is_empty()).then_some(token),
            (!field.is_empty()).then_some(field),
        ))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 读取酷安网页版动态页。
    ///
    /// `https://www.coolapk.com/feed/{id}` 在 XHR 指纹头下直接返回 JSON 动态详情，
    /// 可绕过 `api.coolapk.com/v6/feed/detail` 的验证码风控，用作详情兜底。
    pub fn fetch_external_page(&self, url: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.fetch_external_page(&url))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_feed_replies(
        &self,
        feed_id: String,
        page: u32,
        list_type: String,
        from_feed_author: bool,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_feed_replies_paged(
            &feed_id,
            page,
            "",
            "",
            &list_type,
            from_feed_author as u32,
        ))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_sub_replies(
        &self,
        feed_id: String,
        reply_id: String,
        page: u32,
        last_item: String,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_sub_replies_paged(
            &feed_id,
            &reply_id,
            page,
            &last_item,
        ))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 当前登录用户资料。
    pub fn get_my_profile(&self, uid: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_my_profile(&uid))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_user_profile(&self, uid: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_user_profile(&uid))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_user_feeds(&self, uid: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_user_feeds(&uid, page, "feed"))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 用户空间数据（含主页卡片 homeTabCardRows）。
    pub fn get_user_space(&self, uid: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_user_space(&uid))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 用户标签页数据（图文 article、问答 qa、好物榜 goods_rank、收藏单 collection 等）。
    pub fn get_user_tab_data(
        &self,
        uid: String,
        tab: String,
        page: u32,
        first_item: String,
        last_item: String,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_user_tab_data(&uid, &tab, page, &first_item, &last_item, "all"))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 用户的分类动态：feed_type 为 feed（动态）、reply（回复）、rating（评分）、picture/coolpic（酷图）、ershou（二手）、fav（收藏）。
    pub fn get_user_feeds_typed(&self, uid: String, page: u32, feed_type: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_user_feeds(&uid, page, &feed_type))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 用户收藏的动态（收藏列表，无需额外 id）。
    pub fn get_user_favorites(&self, uid: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_user_feeds(&uid, page, "fav"))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 用户赞过的内容。
    pub fn get_user_liked(&self, uid: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_user_like_list(&uid, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 当前用户的通知数量。
    pub fn get_notification_count(&self) -> Result<String, CoolapkError> {
        let value = self
            .runtime
            .block_on(self.client.get_notification_count())?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_notifications(
        &self,
        notification_type: String,
        page: u32,
    ) -> Result<String, CoolapkError> {
        let value = self
            .runtime
            .block_on(self.client.get_notifications(&notification_type, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn clear_notification_count(
        &self,
        notification_type: String,
    ) -> Result<String, CoolapkError> {
        let value = self
            .runtime
            .block_on(self.client.clear_notification_count(&notification_type))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 全站搜索。
    pub fn search_all(&self, query: String, page: u32) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.search_all(&query, page))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 按官方搜索类型取结果，游标由调用方在分页时原样传回。
    pub fn search_by_type(
        &self,
        search_type: String,
        query: String,
        page: u32,
        first_item: String,
        last_item: String,
        feed_type: String,
    ) -> Result<String, CoolapkError> {
        let supported = ["all", "apk", "game", "user", "feed", "ask", "product", "feedTopic"];
        if !supported.contains(&search_type.as_str()) {
            return Err(CoolapkError::Api { detail: "不支持的搜索类型".to_string() });
        }
        if query.trim().is_empty() {
            return Err(CoolapkError::Api { detail: "搜索关键词不能为空".to_string() });
        }
        let is_feed = search_type == "feed";
        let value = self.runtime.block_on(self.client.search_by_type(
            &search_type,
            query.trim(),
            page.max(1),
            &first_item,
            &last_item,
            if is_feed { "search" } else { "" },
            "",
            if is_feed { "all" } else if search_type == "ask" { &feed_type } else { "" },
            if is_feed { "default" } else { "" },
            0,
            "",
            "",
        ))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn get_hot_searches(&self, refresh: bool) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.get_hot_searches(refresh))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 只返回动态结果，供当前 Android 信息流搜索页使用。
    pub fn search_feeds(&self, query: String, page: u32) -> Result<String, CoolapkError> {
        let value = self
            .runtime
            .block_on(self.client.search_feeds(&query, page, ""))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 点赞动态。
    pub fn like_feed(&self, feed_id: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.like_feed(&feed_id))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 取消点赞动态。
    pub fn unlike_feed(&self, feed_id: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.unlike_feed(&feed_id))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 发表评论。
    pub fn reply_feed(&self, feed_id: String, message: String) -> Result<String, CoolapkError> {
        let value = self
            .runtime
            .block_on(self.client.reply_feed(&feed_id, &message, None, None, None))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn reply_to_comment(
        &self,
        feed_id: String,
        reply_id: String,
        message: String,
    ) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.reply_feed(
            &feed_id,
            &message,
            Some(&reply_id),
            None,
            None,
        ))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn like_reply(&self, reply_id: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.like_reply(&reply_id))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn unlike_reply(&self, reply_id: String) -> Result<String, CoolapkError> {
        let value = self.runtime.block_on(self.client.unlike_reply(&reply_id))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 使用官方登录 WebView 返回的 Cookie 登录。
    pub fn login_by_webview_cookie(&self, cookie: String) -> Result<String, CoolapkError> {
        let value = self
            .runtime
            .block_on(self.client.login_by_webview_cookie(&cookie))?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 使用官方登录回调中的授权码和 WebView Cookie 完成登录。
    pub fn login_by_access_code(&self, code: String, cookie: String) -> Result<String, CoolapkError> {
        let value = self
            .runtime
            .block_on(self.client.login_by_access_code(&code, Some(&cookie)))?;
        Ok(serde_json::to_string(&value)?)
    }

    pub fn set_session_cookie(&self, cookie: String) {
        let _ = self.client.set_user_cookie(cookie);
    }

    pub fn get_session_cookie(&self) -> Option<String> {
        self.client.get_user_cookie()
    }

    pub fn is_logged_in(&self) -> bool {
        self.client
            .get_user_cookie()
            .is_some_and(|cookie| CoolapkClient::has_valid_session_cookie(&cookie))
    }

    pub fn get_device_info(&self) -> Result<String, CoolapkError> {
        let value = self.client.get_device_info()?;
        Ok(serde_json::to_string(&value)?)
    }

    /// 返回不含凭据的运行时诊断信息。
    pub fn diagnostics(&self) -> String {
        serde_json::json!({
            "rustCore": true,
            "homeEndpoint": "/v6/main/indexV8",
            "sessionConfigured": self.client.get_user_cookie().is_some(),
        })
        .to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::CoolapkError;

    #[test]
    fn api_error_preserves_actionable_detail() {
        let error = CoolapkError::from("network unavailable".to_string());
        assert_eq!(error.to_string(), "network unavailable");
    }
}
