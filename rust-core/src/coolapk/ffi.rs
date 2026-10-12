use crate::coolapk::client::CoolapkClient;
use std::sync::Arc;
use tokio::runtime::Runtime;

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoolapkError {
    #[error("{message}")]
    Api { message: String },
}

impl From<String> for CoolapkError {
    fn from(message: String) -> Self {
        Self::Api { message }
    }
}

impl From<serde_json::Error> for CoolapkError {
    fn from(error: serde_json::Error) -> Self {
        Self::Api {
            message: format!("JSON 序列化失败: {error}"),
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
            message: format!("创建异步运行时失败: {error}"),
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
