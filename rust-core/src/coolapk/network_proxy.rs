use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{OnceLock, RwLock};

static PROXY: OnceLock<RwLock<Option<String>>> = OnceLock::new();
static GENERATION: AtomicU64 = AtomicU64::new(0);

fn state() -> &'static RwLock<Option<String>> {
    PROXY.get_or_init(|| RwLock::new(None))
}

pub(crate) fn generation() -> u64 {
    GENERATION.load(Ordering::Relaxed)
}

pub(crate) fn configure(builder: reqwest::ClientBuilder) -> reqwest::ClientBuilder {
    let proxy = state()
        .read()
        .ok()
        .and_then(|guard| guard.clone())
        .filter(|value| !value.trim().is_empty());
    match proxy.and_then(|value| reqwest::Proxy::all(value).ok()) {
        Some(proxy) => builder.proxy(proxy),
        None => builder,
    }
}

pub(crate) fn update(proxy_url: Option<&str>) -> Result<Option<String>, String> {
    let next = proxy_url
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(str::to_string);
    if let Some(value) = next.as_deref() {
        let parsed = reqwest::Url::parse(value).map_err(|_| "代理地址无效".to_string())?;
        if !matches!(parsed.scheme(), "http" | "https" | "socks5" | "socks5h") {
            return Err("仅支持 HTTP(S) 或 SOCKS5 代理".to_string());
        }
    }
    let mut guard = state()
        .write()
        .map_err(|_| "无法更新代理配置".to_string())?;
    let previous = guard.clone();
    *guard = next;
    GENERATION.fetch_add(1, Ordering::Relaxed);
    Ok(previous)
}
