/* =========================================================================
   Coolapk KMP · Android Material 3 Expressive 交互原型
   纯前端演示：确定性虚构数据，不调用任何真实接口。
   ========================================================================= */

/* ---------------------------- 基础工具 ---------------------------- */
const $ = (sel, root = document) => root.querySelector(sel);
const esc = (s) =>
  String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const ic = (name, cls = "") => `<span class="material-symbols-rounded ${cls}">${name}</span>`;

function photo(label, a, b) {
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 400 260'>
  <defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>
  <stop offset='0' stop-color='${a}'/><stop offset='1' stop-color='${b}'/></linearGradient></defs>
  <rect width='400' height='260' fill='url(#g)'/>
  <circle cx='330' cy='46' r='96' fill='rgba(255,255,255,0.12)'/>
  <circle cx='64' cy='224' r='72' fill='rgba(255,255,255,0.10)'/>
  <text x='26' y='150' font-family='sans-serif' font-size='27' font-weight='700' fill='rgba(255,255,255,0.94)'>${label}</text>
  </svg>`;
  return "data:image/svg+xml;utf8," + encodeURIComponent(svg);
}
function avatarImg(hue) {
  const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'>
  <defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'>
  <stop offset='0' stop-color='hsl(${hue},52%,64%)'/><stop offset='1' stop-color='hsl(${(hue + 34) % 360},58%,44%)'/></linearGradient></defs>
  <rect width='100' height='100' fill='url(#g)'/>
  <circle cx='50' cy='37' r='17' fill='rgba(255,255,255,.86)'/>
  <path d='M17 97c4-23 18-33 33-33s29 10 33 33z' fill='rgba(255,255,255,.86)'/></svg>`;
  return "data:image/svg+xml;utf8," + encodeURIComponent(svg);
}
function compact(n) {
  if (n >= 10000) return (n / 10000).toFixed(1) + "万";
  if (n >= 1000) return (n / 1000).toFixed(1) + "k";
  return String(n);
}
function avatar(name, size = "a46", img = null, cls = "") {
  const initial = esc(String(name || "酷").trim().slice(0, 1));
  return `<span class="avatar ${size} ${cls}">${initial}${img ? `<img src="${img}" alt="">` : ""}</span>`;
}
function chip(label, selected = false, attrs = "") {
  return `<button class="chip ${selected ? "selected" : ""}" ${attrs}>${esc(label)}</button>`;
}

/* ---------------------------- 虚构数据 ---------------------------- */
const ME = { name: "青柠不酸", uid: "u_884210", hue: 152, bio: "爱折腾数码，也爱记录生活。相机、耳机、键盘都缺一不可。", feeds: 128, following: 236, followers: 1842 };

const USERS = {
  u1001: { name: "极客小张", hue: 150, bio: "专注手机影像与性能实测，理性种草。" },
  u1002: { name: "溪山", hue: 205, bio: "机械键盘与桌面搭建爱好者。" },
  u1003: { name: "阿岚", hue: 32, bio: "用镜头记录城市与光影。" },
  u1004: { name: "无线小站", hue: 268, bio: "耳机、音箱、HiFi 折腾日常。" },
  u1005: { name: "老周说机", hue: 12, bio: "十年数码编辑，聊点真话。" },
  u1006: { name: "数码小灵通", hue: 190, bio: "第一时间分享新品资讯。" },
};

const FEEDS = [
  {
    id: "5101", user: "极客小张", uid: "u1001", hue: 150, device: "Pixel 9 Pro", time: "12分钟前",
    title: "用 Pixel 9 Pro 拍了组夜景，HDR 依然是强项",
    message: "周末在江边随手拍了一组，几乎没怎么修图。手持长曝光也很稳，算法压高光很克制，暗部细节保留得不错。\n\n色彩比上一代更中性，不再一味加饱和；夜景模式出片速度也快了不少。",
    images: [photo("夜景 · 江边", "#0E3B2E", "#1E8F63"), photo("长曝光 · 车流", "#123A4A", "#2E8CA8")],
    likes: 328, replies: 46, liked: false,
  },
  {
    id: "5102", user: "溪山", uid: "u1002", hue: 205, device: "桌面自组", time: "1小时前",
    title: "把桌面重新整理了一遍，终于清爽了",
    message: "换了块 68 键的铝坨坨，配了一套奶油轴，声音闷而不塌。显示器臂让桌面空出一大块，晚上开灯氛围感直接拉满。",
    images: [photo("桌面 · 全景", "#1C2B3A", "#3E6E9C")],
    likes: 214, replies: 33, liked: true,
  },
  {
    id: "5103", user: "阿岚", uid: "u1003", hue: 32, device: "Fujifilm X-T5", time: "2小时前",
    title: null,
    message: "落日时分的城市天际线，等了半个小时才等到云被点亮的瞬间。",
    images: [photo("落日 · 天际线", "#4A2B10", "#D98E3A"), photo("云 · 丁达尔", "#5A3A12", "#E8B45C"), photo("剪影 · 塔吊", "#3A2410", "#B4742E")],
    likes: 156, replies: 12, liked: false,
  },
  {
    id: "5104", user: "无线小站", uid: "u1004", hue: 268, device: "iBasso DX320", time: "3小时前",
    title: "入门价位的人声塞，这枚值得一听",
    message: "换线之后中频密度上来了，齿音控制得比预想好。搭配小尾巴推得动，手机直推也能听个大概。\n\n整体走向偏流行，适合人声和小编制，不适合追求极低频的下潜力。",
    images: [photo("耳机 · 特写", "#2A1B3A", "#7B4FB0")],
    likes: 98, replies: 27, liked: false,
  },
  {
    id: "5105", user: "老周说机", uid: "u1005", hue: 12, device: "iPhone 16 Pro", time: "5小时前",
    title: "用了两周，聊聊这代芯片的真实体验",
    message: "跑分看看就好，真正影响体验的是持续负载下的发热和降频曲线。这一代改进主要体现在能效，长时间游戏帧率更稳。\n\n影像依旧是长板，视频防抖和电影模式是很多人买它的理由。",
    images: [],
    likes: 512, replies: 88, liked: false,
  },
  {
    id: "5106", user: "数码小灵通", uid: "u1006", hue: 190, device: "Web", time: "昨天",
    title: "下周新品发布汇总，先把时间记下来",
    message: "手机、平板、笔电都有，重点看两家旗舰的影像方案。具体参数以发布会为准。",
    images: [photo("新品 · 预告", "#0E2E3A", "#2E9CB0")],
    likes: 76, replies: 9, liked: false,
  },
];

const COMMENTS = [
  { user: "阿岚", hue: 32, time: "8分钟前", floor: 1, text: "这组夜景的暗部真的很干净，请问是手持还是上脚架？", likes: 12, liked: false, replies: 3, isAuthor: false,
    preview: [{ user: "极客小张", text: "全程手持，夜景模式大概两秒。" }, { user: "阿岚", text: "稳得离谱，学到了。" }] },
  { user: "无线小站", hue: 268, time: "22分钟前", floor: 2, text: "色彩确实比上一代耐看，不再抹一层高饱和的滤镜感了。", likes: 8, liked: true, replies: 0, isAuthor: false },
  { user: "老周说机", hue: 12, time: "40分钟前", floor: 3, text: "今年这代算法调得比较成熟，直出发朋友圈基本不用动。", likes: 21, liked: false, replies: 5, isAuthor: false },
  { user: "极客小张", hue: 150, time: "1小时前", floor: 4, text: "补充一下：白天长焦裁切后的可用度也比上一代高，样张晚点补。", likes: 34, liked: false, replies: 2, isAuthor: true,
    preview: [{ user: "溪山", text: "等样张，主要想看长焦。" }, { user: "拾光者", text: "蹲一个。" }] },
];

const PRODUCTS = [
  { id: "p1", name: "小米 15 Ultra", brand: "Xiaomi", cat: "影像旗舰", score: 8.9, ratingCount: 1268, hot: 3221, desc: "一英寸主摄搭配潜望长焦，徕卡四摄全焦段覆盖。屏幕为 2K 全等深微曲屏，支持高频调光。", specs: [["屏幕", "6.73 英寸 2K LTPO"], ["处理器", "骁龙 8 至尊版"], ["主摄", "50MP 一英寸"], ["长焦", "200MP 潜望 4.3x"], ["电池", "6000mAh"], ["快充", "90W 有线 / 80W 无线"]], img: ["夜景 · 长焦", "#2A1B10", "#B0742E"] },
  { id: "p2", name: "一加 13", brand: "OnePlus", cat: "性能旗舰", score: 8.6, ratingCount: 892, hot: 2650, desc: "2K 东方屏，第二代骁龙 8 至尊版，6000mAh 大电池。哈苏联名的三摄方案。", specs: [["屏幕", "6.82 英寸 2K"], ["处理器", "骁龙 8 至尊版"], ["主摄", "50MP"], ["电池", "6000mAh"], ["快充", "100W"]], img: ["机身 · 特写", "#102A3A", "#2E7AA0"] },
  { id: "p3", name: "iPhone 16 Pro", brand: "Apple", cat: "影像旗舰", score: 8.7, ratingCount: 2043, hot: 2860, desc: "A18 Pro 芯片，四棱镜 5 倍长焦，支持 4K120 视频与电影模式。", specs: [["屏幕", "6.3 英寸"], ["处理器", "A18 Pro"], ["长焦", "5x 四棱镜"], ["视频", "4K120 杜比视界"], ["接口", "USB-C"]], img: ["机身 · 背面", "#2A2A2A", "#6E6E78"] },
  { id: "p4", name: "华为 Mate 70 Pro", brand: "HUAWEI", cat: "商务旗舰", score: 9.0, ratingCount: 1533, hot: 3510, desc: "红枫原色影像，超可靠玄武架构，卫星通信。", specs: [["屏幕", "6.7 英寸 LTPO"], ["影像", "红枫原色"], ["通信", "双向卫星"], ["电池", "5500mAh"]], img: ["机身 · 配色", "#1A2E24", "#3E8A5E"] },
  { id: "p5", name: "三星 Galaxy S25 Ultra", brand: "Samsung", cat: "影像旗舰", score: 8.8, ratingCount: 976, hot: 2210, desc: "2 亿像素主摄，内置 S Pen，钛金属边框。", specs: [["屏幕", "6.9 英寸 2K"], ["主摄", "200MP"], ["特色", "S Pen"], ["边框", "钛金属"]], img: ["机身 · 全焦段", "#1A1A2E", "#4E4E9C"] },
  { id: "p6", name: "Redmi K80 Pro", brand: "Redmi", cat: "性能旗舰", score: 8.4, ratingCount: 1187, hot: 1980, desc: "2K 屏与旗舰芯片下放，主打高性价比性能体验。", specs: [["屏幕", "6.67 英寸 2K"], ["处理器", "骁龙 8 至尊版"], ["电池", "6000mAh"], ["快充", "120W"]], img: ["机身 · 跑分", "#2E1A1A", "#A04E4E"] },
];

const APPS = [
  { id: "com.coolapk.market", name: "酷安", dev: "酷安科技", score: 4.6, size: "78.4 MB", version: "13.2.1", downloads: "2.3亿", desc: "数码爱好者的聚集地，分享你与数码生活的每一个瞬间。", changelog: "1. 优化首页信息流加载速度；2. 修复部分机型图片预览异常。", shots: [photo("首页", "#0E3B2E", "#1E8F63"), photo("数码", "#123A4A", "#2E8CA8"), photo("社区", "#3A2A10", "#B07A2E")] },
  { id: "com.tencent.mm", name: "微信", dev: "腾讯", score: 4.2, size: "248 MB", version: "8.0.55", downloads: "50亿", desc: "一款跨平台的通讯工具，支持语音、视频、朋友圈。", changelog: "修复已知问题，优化使用体验。", shots: [photo("聊天", "#123A1A", "#3EA050"), photo("朋友圈", "#122E3A", "#2E8CA0")] },
  { id: "com.autonavi.minimap", name: "高德地图", dev: "高德软件", score: 4.5, size: "156 MB", version: "15.1.0", downloads: "20亿", desc: "导航、打车、公交地铁，一站式出行服务。", changelog: "优化路线规划速度。", shots: [photo("导航", "#123A2A", "#2E9C6E")] },
  { id: "com.netease.cloudmusic", name: "网易云音乐", dev: "网易", score: 4.7, size: "132 MB", version: "9.2.0", downloads: "10亿", desc: "音乐社区，发现好音乐。", changelog: "新增每日推荐优化。", shots: [photo("乐库", "#3A1212", "#A03E3E")] },
];

const NOTIFICATIONS = {
  COMMENT: [
    { user: "阿岚", hue: 32, time: "8分钟前", msg: "回复了你的评论：这组夜景的暗部真的很干净，请问是手持还是上脚架？", target: "用 Pixel 9 Pro 拍了组夜景，HDR 依然是强项", unread: true },
    { user: "老周说机", hue: 12, time: "30分钟前", msg: "评论了你的动态：今年这代算法调得比较成熟。", target: "用 Pixel 9 Pro 拍了组夜景，HDR 依然是强项", unread: true },
    { user: "无线小站", hue: 268, time: "2小时前", msg: "回复了你的评论：换线之后中频密度确实上来了一些。", target: "入门价位的人声塞，这枚值得一听", unread: false },
  ],
  MENTION: [
    { user: "溪山", hue: 205, time: "1小时前", msg: "在动态中 @ 了你：这个桌面搭配想听听 @青柠不酸 的意见。", target: "把桌面重新整理了一遍，终于清爽了", unread: true },
  ],
  COMMENT_MENTION: [
    { user: "数码小灵通", hue: 190, time: "3小时前", msg: "在评论中 @ 了你：新品信息可以看 @青柠不酸 整理的那条。", target: "下周新品发布汇总，先把时间记下来", unread: false },
  ],
  LIKE: [
    { user: "拾光者", hue: 320, time: "5分钟前", msg: "赞了你的动态", target: "把桌面重新整理了一遍，终于清爽了", unread: true },
    { user: "果冻布丁", hue: 95, time: "1小时前", msg: "赞了你的评论", target: "入门价位的人声塞，这枚值得一听", unread: false },
  ],
  FOLLOW: [
    { user: "屏幕捕手", hue: 170, time: "20分钟前", msg: "关注了你", target: null, unread: true },
    { user: "阿岚", hue: 32, time: "2天前", msg: "关注了你", target: null, unread: false },
  ],
};

const HOT_WORDS = ["小米15Ultra", "一加13", "iPhone16Pro", "机械键盘轴体", "人像镜头推荐", "鸿蒙原生", "桌面搭建", "HiFi入门", "夜景拍摄技巧", "百元耳机"];

const SEARCH = {
  ALL: [
    { kind: "动态", title: "小米 15 Ultra 两周体验：影像真的补齐了吗", sub: "极客小张 · 328 赞 · 46 评论", icon: "chat_bubble" },
    { kind: "应用", title: "酷安", sub: "酷安科技 · 数码社区", icon: "apps" },
    { kind: "数码", title: "小米 15 Ultra", sub: "Xiaomi · 影像旗舰 · 评分 8.9", icon: "smartphone" },
    { kind: "用户", title: "极客小张", sub: "专注手机影像与性能实测", icon: "person" },
    { kind: "话题", title: "#数码好物#", sub: "12.6万 讨论 · 3.2万 关注", icon: "tag" },
  ],
  APP: APPS.map((a) => ({ kind: "应用", title: a.name, sub: `${a.dev} · 评分 ${a.score}`, icon: "apps" })),
  GAME: [
    { kind: "应用", title: "原神", sub: "米哈游 · 评分 4.5", icon: "sports_esports" },
    { kind: "应用", title: "崩坏：星穹铁道", sub: "米哈游 · 评分 4.6", icon: "sports_esports" },
  ],
  USER: Object.entries(USERS).map(([uid, u]) => ({ kind: "用户", title: u.name, sub: u.bio, icon: "person" })),
  FEED: FEEDS.slice(0, 4).map((f) => ({ kind: "动态", title: f.title || f.message.slice(0, 18) + "…", sub: `${f.user} · ${compact(f.likes)} 赞`, icon: "chat_bubble" })),
  ASK: [
    { kind: "问题", title: "小米 15 Ultra 和 iPhone 16 Pro 怎么选？", sub: "提问 · 36 回答", icon: "help" },
    { kind: "问题", title: "新手相机入门，预算 5000 有什么推荐？", sub: "提问 · 58 回答", icon: "help" },
    { kind: "问题", title: "机械键盘轴体怎么挑？", sub: "提问 · 21 回答", icon: "help" },
  ],
  PRODUCT: PRODUCTS.map((p) => ({ kind: "数码", title: p.name, sub: `${p.brand} · ${p.cat} · 评分 ${p.score}`, icon: "smartphone" })),
  TOPIC: [
    { kind: "话题", title: "#数码好物#", sub: "12.6万 讨论 · 3.2万 关注", icon: "tag" },
    { kind: "话题", title: "#桌面搭建#", sub: "3.4万 讨论 · 8.1千 关注", icon: "tag" },
    { kind: "话题", title: "#夜景摄影#", sub: "2.1万 讨论 · 5.6千 关注", icon: "tag" },
  ],
};

const DISCOVERY_TABS = ["推荐", "科技", "数码", "影像", "游戏", "出行"];
const DISCOVERY_FILTERS = ["全部", "评价", "资讯", "教程", "榜单"];
const DISCOVERY = [
  { type: "selector" },
  { type: "item", img: photo("科技 · 每周快讯", "#123A2A", "#2E9C6E"), title: "本周科技要闻：三款旗舰同周发布", desc: "手机、平板、笔电轮番登场，我们把关键信息整理成一张清单，帮你快速抓住重点。" },
  { type: "feed", feed: FEEDS[0] },
  { type: "item", img: photo("影像 · 器材", "#3A2410", "#B4742E"), title: "从入门到进阶：2026 相机选购指南", desc: "按预算和拍摄题材分类，覆盖微单、卡片机和镜头搭配建议。" },
  { type: "item", img: photo("榜单 · 数码", "#102A3A", "#2E7AA0"), title: "酷友口碑榜：本月最受关注数码产品", desc: "由站内真实评价聚合而成，每周更新。" },
];

const DIGITAL_TABS = ["推荐", "手机", "电脑", "智能家居", "影像器材"];
const BRANDS = [
  { name: "Xiaomi", hue: 22 }, { name: "Apple", hue: 210 }, { name: "HUAWEI", hue: 0 },
  { name: "OnePlus", hue: 350 }, { name: "OPPO", hue: 150 }, { name: "vivo", hue: 220 },
  { name: "Samsung", hue: 230 }, { name: "荣耀", hue: 195 },
];
const CATEGORIES = ["旗舰手机", "折叠屏", "平板电脑", "笔记本", "智能手表", "耳机音箱"];

/* ---------------------------- 通用组件 ---------------------------- */
function feedCard(f, opts = {}) {
  const liked = opts.liked ?? f.liked;
  const images = f.images && f.images.length
    ? f.images.length === 1
      ? `<div class="feed-media"><img src="${f.images[0]}" alt=""><span class="badge-count">1/1</span></div>`
      : `<div class="feed-gallery">${f.images.map((u, i) => `<img class="gi" src="${u}" alt="">`).join("")}</div>`
    : "";
  const title = f.title ? `<div class="feed-title">${esc(f.title)}</div>` : "";
  const meta = [f.device, f.time].filter(Boolean).join(" · ");
  const click = opts.clickable === false ? "" : `data-go="${opts.go || "feedDetail"}" data-id="${f.id}"`;
  return `<button class="feed-card press" ${click} style="text-align:left">
    <span class="fc-inner" style="display:block">
      <span class="feed-head">
        ${avatar(f.user, "a46", avatarImg(f.hue))}
        <span class="fh-main">
          <span class="fh-name">${esc(f.user)}</span>
          <span class="fh-meta">${esc(meta)}</span>
        </span>
      </span>
      ${title}
      <span class="feed-body clamp8">${esc(f.message)}</span>
      ${images}
      <span class="feed-divider"></span>
      <span class="feed-actions">
        <span class="feed-action ${liked ? "liked" : ""}" data-like="${f.id}">${ic(liked ? "favorite" : "favorite_border", liked ? "ms-fill" : "")}${compact(f.likes + (liked ? 1 : 0))}</span>
        <span class="feed-action" data-go="feedDetail" data-id="${f.id}" data-focus="1">${ic("chat_bubble")}${compact(f.replies)}</span>
        <span class="feed-action">${ic("share")}</span>
      </span>
    </span>
  </button>`;
}

function appbar({ title, brand = false, sub = "", actions = [], back = false, immersive = false }) {
  const left = back
    ? `<button class="icon-btn" data-back>${ic("arrow_back")}</button>`
    : "";
  return `<header class="appbar ${immersive ? "immersive" : ""}">
    ${left}
    <span class="ab-title ${brand ? "brand" : ""}">${esc(title)}${sub ? `<span class="ab-sub">${esc(sub)}</span>` : ""}</span>
    ${actions.join("")}
  </header>`;
}

const act = {
  search: `<button class="icon-btn tonal" data-go="searchHome">${ic("search")}</button>`,
  refresh: (n = "") => `<button class="icon-btn" data-toast="已是最新内容">${ic("refresh")}</button>`,
  bell: (n) => `<button class="icon-btn" data-go="notifications">${ic("notifications")}${n ? `<span class="badge">${n > 99 ? "99+" : n}</span>` : ""}</button>`,
  share: `<button class="icon-btn" data-toast="已打开系统分享">${ic("share")}</button>`,
  close: `<button class="icon-btn" data-overlay-close>${ic("close")}</button>`,
  save: `<button class="icon-btn" data-toast="草稿已保存">${ic("save")}</button>`,
  more: `<button class="icon-btn">${ic("more_vert")}</button>`,
};

function navBar(active) {
  const items = [["home", "首页"], ["apps", "数码"], ["add", "发布"], ["explore", "发现"], ["person", "我的"]];
  return `<nav class="bottom-nav">
    ${items.map(([icon, label], i) => {
      if (i === 2)
        return `<button class="nav-publish" data-go="publish">${ic("add")}</button>`;
      const on = i === active;
      return `<button class="nav-item ${on ? "active" : ""}" ${on ? "" : `data-tab="${i}"`}>
        <span class="nav-pill">${ic(icon, on ? "ms-fill" : "")}<span>${label}</span></span>
      </button>`;
    }).join("")}
  </nav>`;
}

function rail(active) {
  const items = [["home", "首页"], ["apps", "数码"], ["explore", "发现"], ["person", "我的"]];
  return `<nav class="nav-rail">
    <div style="height:6px"></div>
    <button class="rail-item ${active === 0 ? "active" : ""}" data-tab="0">${ic("home", active === 0 ? "ms-fill" : "")}<span>首页</span></button>
    <button class="rail-item ${active === 1 ? "active" : ""}" data-tab="1">${ic("apps", active === 1 ? "ms-fill" : "")}<span>数码</span></button>
    <button class="rail-publish" data-go="publish">${ic("add")}</button>
    <button class="rail-item ${active === 3 ? "active" : ""}" data-tab="3">${ic("explore", active === 3 ? "ms-fill" : "")}<span>发现</span></button>
    <button class="rail-item ${active === 4 ? "active" : ""}" data-tab="4">${ic("person", active === 4 ? "ms-fill" : "")}<span>我的</span></button>
  </nav>`;
}

const state = {
  screen: "home",
  params: {},
  tab: 0,
  theme: "light",
  width: "412",
  view: "canvas",
  overlay: null,
  snack: null,
  history: [],
  liked: {},
  commentSort: "默认",
  searchCat: "综合",
  searchAsk: "全部",
  digitalTab: "推荐",
  digitalMode: "品牌",
  digitalBrand: "Xiaomi",
  discoveryTab: "推荐",
  discoveryFilter: "全部",
  notifTab: "评论回复",
  publishMode: "编辑",
  publishText: "",
};

function go(screen, params = {}, push = true) {
  if (push && (state.screen !== screen || JSON.stringify(params) !== JSON.stringify(state.params)))
    state.history.push({ screen: state.screen, params: state.params });
  state.screen = screen;
  state.params = params;
  render();
}
function back() {
  const prev = state.history.pop();
  if (prev) {
    state.screen = prev.screen;
    state.params = prev.params;
    render(true);
  }
}
function toast(msg) {
  state.snack = msg;
  render();
  clearTimeout(toast._t);
  toast._t = setTimeout(() => { state.snack = null; render(); }, 2400);
}

/* ---------------------------- 页面 ---------------------------- */
const SCREENS = {};

/* 01 首页动态流 */
SCREENS.home = () => ({
  nav: 0,
  appbar: appbar({ title: "酷安", brand: true, actions: [act.search, act.bell(3), act.refresh()] }),
  body: `
    <div class="pad-x" style="padding-top:4px">
      <div class="chips" style="padding-left:0;padding-right:0">
        ${["关注", "推荐", "热榜", "数码"].map((t, i) => chip(t, i === 1, `data-toast="切换到「${t}」信息流（演示）"`)).join("")}
      </div>
    </div>
    <div class="pad-x feed-list mt10" style="padding-bottom:24px">
      ${FEEDS.map((f) => feedCard(f)).join("")}
      <div class="loading-inline"><span class="spinner sm"></span></div>
    </div>`,
});

/* 02 数码内容首页 */
SCREENS.digital = () => ({
  nav: 1,
  appbar: appbar({ title: "数码", actions: [act.search, act.refresh()] }),
  body: `
    <div class="chips mt6">${DIGITAL_TABS.map((t) => chip(t, t === state.digitalTab, `data-digital-tab="${t}"`)).join("")}</div>
    <div class="mt10" style="padding-bottom:24px">
      <div class="pad-x">
        <div class="card" style="border-radius:var(--r-lg)">
          <div class="feed-media" style="border-radius:var(--r-lg);margin:0">
            <img src="${photo("数码 · 本周精选", "#123A2A", "#2E9C6E")}" alt="">
            <span class="badge-count">1/4</span>
          </div>
        </div>
      </div>
      <div class="section"><span class="s-title">热门产品</span><span class="s-action" data-go="digitalLibrary">全部${ic("chevron_right")}</span></div>
      <div class="feed-gallery pad-x" style="padding-top:0">
        ${PRODUCTS.slice(0, 4).map((p) => `<button class="card press" data-go="productDetail" data-id="${p.id}" style="width:172px;flex-shrink:0;text-align:left">
          <img src="${photo(p.name, "#1A2E24", "#3E8A5E")}" style="width:100%;height:110px;object-fit:cover">
          <span style="display:block;padding:10px 12px">
            <span class="lr-title" style="font-size:14px">${esc(p.name)}</span>
            <span class="lr-meta" style="display:block;margin-top:4px">${esc(p.brand)} · 评分 ${p.score}</span>
          </span>
        </button>`).join("")}
      </div>
      <div class="section"><span class="s-title">品牌快选</span><span class="s-action" data-go="digitalLibrary">更多${ic("chevron_right")}</span></div>
      <div class="pad-x grid2">
        ${BRANDS.slice(0, 4).map((b) => `<button class="brand-item press" data-go="digitalLibrary">
          <span class="bi-logo" style="background:hsl(${b.hue},45%,90%);color:hsl(${b.hue},55%,32%)">${esc(b.name.slice(0, 1))}</span>
          <span class="bi-name">${esc(b.name)}</span>
        </button>`).join("")}
      </div>
      <div class="section"><span class="s-title">数码社区</span></div>
      <div class="pad-x feed-list">
        ${FEEDS.slice(1, 3).map((f) => feedCard(f)).join("")}
      </div>
    </div>`,
});

/* 03 数码品牌/分类浏览 */
SCREENS.digitalLibrary = () => {
  const list = PRODUCTS;
  return {
    nav: 1,
    appbar: appbar({ title: "数码", actions: [act.search], back: true }),
    body: `
      <div class="pad-x" style="padding-top:8px">
        <div class="segmented">
          <button class="${state.digitalMode === "品牌" ? "active" : ""}" data-digital-mode="品牌">品牌</button>
          <button class="${state.digitalMode === "分类" ? "active" : ""}" data-digital-mode="分类">分类</button>
        </div>
      </div>
      <div class="library mt10" style="min-height:calc(100% - 80px)">
        <div class="lib-rail">
          ${(state.digitalMode === "品牌" ? BRANDS.map((b) => b.name) : CATEGORIES).map((n) =>
            `<div class="li ${n === state.digitalBrand ? "active" : ""}" data-digital-pick="${n}">${esc(n)}</div>`).join("")}
        </div>
        <div class="lib-pane">
          <div class="between" style="margin-bottom:10px">
            <span class="s-title" style="font:var(--t-title-lg);font-weight:700">${esc(state.digitalBrand)}</span>
            <span class="muted small">${list.length} 个产品</span>
          </div>
          <div style="display:flex;flex-direction:column;gap:10px;padding-bottom:24px">
            ${list.map((p) => `<button class="card press" data-go="productDetail" data-id="${p.id}" style="display:flex;gap:12px;padding:12px;text-align:left;align-items:center">
              <img src="${photo(p.name, "#1A2E24", "#3E8A5E")}" style="width:76px;height:76px;border-radius:14px;object-fit:cover;flex-shrink:0">
              <span class="grow">
                <span class="lr-title">${esc(p.name)}</span>
                <span class="lr-sub" style="display:block">${esc(p.cat)}</span>
                <span class="lr-meta" style="display:block">评分 ${p.score} · 热度 ${compact(p.hot)}</span>
              </span>
              ${ic("chevron_right", "chev")}
            </button>`).join("")}
          </div>
        </div>
      </div>`,
  };
};

/* 04 发布编辑器 */
SCREENS.publish = () => {
  const len = state.publishText.length;
  const over = len > 1000;
  return {
    nav: null,
    appbar: appbar({ title: "发布动态", back: true, actions: [act.save] }),
    body: `
      <div class="pad-x" style="padding-top:8px">
        <div class="between">
          <div class="segmented">
            <button class="${state.publishMode === "编辑" ? "active" : ""}" data-publish-mode="编辑">编辑</button>
            <button class="${state.publishMode === "预览" ? "active" : ""}" data-publish-mode="预览">预览</button>
          </div>
          <span class="counter ${over ? "over" : ""}">${len}/1000</span>
        </div>
      </div>
      <div class="pad-x mt16" style="position:relative;min-height:320px">
        <div class="feed-head" style="margin-bottom:14px">
          ${avatar(ME.name, "a46", avatarImg(ME.hue))}
          <span class="fh-main"><span class="fh-name">${ME.name}</span><span class="fh-meta">将发布为纯文本动态</span></span>
        </div>
        <div class="editor-field" id="publish-editor" contenteditable="true" data-placeholder="分享这一刻的酷搞感受，与酷友讨论数码生活…">
          ${state.publishText ? esc(state.publishText) : ""}
        </div>
      </div>
      <div class="pad-x mt16">
        <div class="status-strip"><span class="ms">${"check_circle"}</span>已登录 · ${esc(ME.name)}</div>
        <div class="status-strip mt10" style="background:transparent;color:var(--on-surface-variant);padding-left:2px">
          ${ic("info")} 纯文本动态，发布前可切换到预览
        </div>
      </div>`,
    bottom: `<div class="pad-x" style="display:flex;gap:10px;align-items:center;padding-top:10px;padding-bottom:12px;border-top:1px solid var(--outline-variant)">
      <button class="btn btn-text" data-clear-draft>${ic("delete")}清除草稿</button>
      <span class="grow"></span>
      <button class="btn btn-filled" data-go="publishPreview" ${len === 0 ? "disabled" : ""}>${ic("check")}发布</button>
    </div>`,
  };
};

/* 05 发布预览与草稿确认 */
SCREENS.publishPreview = () => ({
  nav: null,
  appbar: appbar({ title: "发布预览", back: true, actions: [act.save] }),
  body: `
    <div class="pad-x" style="padding-top:10px">
      <div class="pill-info">${ic("visibility")} 这是其他酷友看到的预览效果</div>
      <div class="mt16">
        ${feedCard({
          id: "preview", user: ME.name, hue: ME.hue, device: "本机", time: "刚刚",
          title: null, message: state.publishText || "输入内容后这里会显示动态预览",
          images: [], likes: 0, replies: 0, liked: false,
        }, { clickable: false })}
      </div>
    </div>`,
  bottom: `<div class="pad-x" style="display:flex;gap:10px;padding-top:10px;padding-bottom:12px;border-top:1px solid var(--outline-variant)">
      <button class="btn btn-tonal grow" data-publish-mode="编辑" data-go="publish">${ic("edit")}返回编辑</button>
      <button class="btn btn-filled grow" data-publish-done>${ic("check")}确认发布</button>
    </div>`,
});

/* 06 发现 */
SCREENS.discovery = () => ({
  nav: 3,
  appbar: appbar({ title: "发现", actions: [act.search, act.refresh()] }),
  body: `
    <div class="chips mt6">${DISCOVERY_TABS.map((t) => chip(t, t === state.discoveryTab, `data-discovery-tab="${t}"`)).join("")}</div>
    <div class="pad-x" style="padding-top:0">
      <div class="section plain"><span class="s-title" style="font:var(--t-title-lg);font-weight:700">科技前沿</span><span class="s-action" data-toast="打开网页（演示）">网页版${ic("open_in_new")}</span></div>
      <div class="chips plain" style="margin-bottom:12px">
        ${DISCOVERY_FILTERS.map((f) => chip(f, f === state.discoveryFilter, `data-discovery-filter="${f}"`)).join("")}
      </div>
      <div style="display:flex;flex-direction:column;gap:12px;padding-bottom:24px">
        ${DISCOVERY.map((d) => {
          if (d.type === "selector") return "";
          if (d.type === "feed") return feedCard(d.feed);
          return `<button class="card press" data-go="feedDetail" data-id="5101" style="text-align:left">
            <img src="${d.img}" style="width:100%;height:150px;object-fit:cover">
            <span style="display:block;padding:12px 14px">
              <span class="feed-title" style="margin:0">${esc(d.title)}</span>
              <span class="feed-body clamp3" style="font-size:14px;margin-top:6px">${esc(d.desc)}</span>
              <span class="row small muted mt10">${ic("schedule", "")}<span style="margin-left:4px">2小时前 · 站内内容</span></span>
            </span>
          </button>`;
        }).join("")}
      </div>
    </div>`,
});

/* 07 我的（已登录） */
function profileBody(own) {
  const u = own ? ME : { name: SEARCH.USER[0].title, hue: 150, uid: "u_1001", bio: USERS.u1001.bio, feeds: 96, following: 128, followers: 2033 };
  return `
    <div class="pad-x" style="padding-top:20px">
      <div style="display:flex;flex-direction:column;align-items:center;text-align:center">
        ${avatar(u.name, "a92", avatarImg(u.hue))}
        <div class="mt10" style="font:var(--t-headline-sm);font-weight:800">${esc(u.name)}</div>
        <div class="muted small mt6">UID ${esc(u.uid)}</div>
        <div class="muted mt10" style="max-width:280px">${esc(u.bio)}</div>
      </div>
      <div class="mt24" style="display:flex;justify-content:center">
        <div class="stats">
          <div class="stat"><div class="v">${compact(u.feeds)}</div><div class="l">动态</div></div>
          <div class="stat"><div class="v">${compact(u.following)}</div><div class="l">关注</div></div>
          <div class="stat"><div class="v">${compact(u.followers)}</div><div class="l">粉丝</div></div>
        </div>
      </div>
      ${own
        ? `<div class="mt24" style="display:flex;gap:10px">
             <button class="btn btn-tonal grow" data-toast="编辑资料（演示）">${ic("edit")}编辑资料</button>
             <button class="btn btn-outlined grow" data-overlay="logout">${ic("logout")}退出登录</button>
           </div>`
        : `<div class="mt24" style="display:flex;gap:10px">
             <button class="btn btn-filled grow" data-toast="已关注（演示）">${ic("person_add")}关注</button>
             <button class="btn btn-outlined grow" data-toast="已发送私信入口（演示）">${ic("mail")}私信</button>
           </div>`}
      <div class="section plain mt16"><span class="s-title" style="font:var(--t-title-lg);font-weight:700">动态</span><span class="muted small">${compact(u.feeds)}</span></div>
      <div class="feed-list" style="padding-bottom:24px">
        ${FEEDS.slice(0, own ? 3 : 2).map((f) => feedCard(f)).join("")}
      </div>
    </div>`;
}
SCREENS.profileOwn = () => ({
  nav: 4,
  appbar: appbar({ title: "我的", actions: [act.refresh()] }),
  body: profileBody(true),
});

/* 08 我的（游客） */
SCREENS.profileGuest = () => ({
  nav: 4,
  appbar: appbar({ title: "我的" }),
  body: `
    <div class="state primary" style="padding-top:64px">
      <div class="st-icon">${ic("account_circle")}</div>
      <div class="st-title">还没有登录</div>
      <div class="st-detail">登录后可查看个人主页、消息通知，并参与发布和互动。浏览内容不受影响。</div>
      <div class="st-action" style="display:flex;flex-direction:column;gap:10px;width:220px">
        <button class="btn btn-filled btn-block" data-go="officialLogin">${ic("login")}网页登录</button>
        <button class="btn btn-text btn-block" data-go="cookieImport">${ic("key")}改用 Cookie 导入</button>
      </div>
    </div>
    <div class="pad-x" style="padding-bottom:24px">
      <div class="card tonal" style="padding:16px">
        <div class="row" style="font-weight:600">${ic("info")}登录说明</div>
        <div class="muted small mt6" style="line-height:1.7">本客户端通过酷安官方网页完成登录，会话仅保存在本机并使用系统密钥库加密。我们不会读取或上传你的账号密码。</div>
      </div>
    </div>`,
});

/* 09 搜索入口 */
SCREENS.searchHome = () => ({
  nav: null,
  appbar: appbar({ title: "", back: true }),
  body: `
    <div class="pad-x" style="padding-top:0">
      <div class="searchbar" onclick="this.querySelector('input').focus()">
        ${ic("search")}
        <input placeholder="搜索酷安内容" data-search-input autofocus>
      </div>
    </div>
    <div class="section plain pad-x" style="padding-top:24px"><span class="s-title" style="font:var(--t-title-lg);font-weight:700">热门搜索</span><span class="s-action" data-toast="已换一批">${ic("refresh")}换一批</span></div>
    <div class="group" style="margin:0 16px">
      ${HOT_WORDS.slice(0, 8).map((w, i) => `<button class="list-row" data-go="searchResults">
        <span class="rank-badge ${i < 3 ? "top" + (i === 0 ? "" : i + 1) : ""}">${i + 1}</span>
        <span class="grow" style="font:var(--t-body-lg)">${esc(w)}</span>
        ${ic("chevron_right", "chev")}
      </button>`).join("")}
    </div>`,
});

/* 10 搜索结果 */
SCREENS.searchResults = () => {
  const cats = ["综合", "应用", "游戏", "用户", "动态", "问答", "数码", "话题"];
  const catKey = { 综合: "ALL", 应用: "APP", 游戏: "GAME", 用户: "USER", 动态: "FEED", 问答: "ASK", 数码: "PRODUCT", 话题: "TOPIC" }[state.searchCat];
  let rows = SEARCH[catKey] || [];
  if (state.searchCat === "问答" && state.searchAsk !== "全部") {
    rows = state.searchAsk === "提问" ? rows.filter((r) => r.kind === "问题") : rows.filter((r) => r.kind === "回答");
  }
  const empty = rows.length === 0;
  return {
    nav: null,
    appbar: `<header class="appbar" style="gap:8px;padding-left:6px">
      <button class="icon-btn" data-back>${ic("arrow_back")}</button>
      <div class="searchbar grow"><span class="ms">search</span><input value="小米 15 Ultra" data-search-input><button class="icon-btn small" data-toast="执行搜索">${ic("search")}</button></div>
    </header>`,
    body: `
      <div class="tabs">${cats.map((c) => `<button class="tab ${c === state.searchCat ? "active" : ""}" data-search-cat="${c}">${c}</button>`).join("")}</div>
      ${state.searchCat === "问答" ? `<div class="chips" style="padding-top:12px">${["全部", "提问", "回答"].map((a) => chip(a, a === state.searchAsk, `data-search-ask="${a}"`)).join("")}</div>` : ""}
      ${empty
        ? `<div class="state"><div class="st-icon">${ic("search_off")}</div><div class="st-title">未找到相关结果</div><div class="st-detail">试试其他关键词或切换分类</div></div>`
        : `<div>
            ${rows.map((r) => `<button class="result-row" data-go="${resultTarget(r.kind)}">
              <span class="rr-icon">${ic(r.icon)}</span>
              <span class="grow">
                <span class="rr-kind">${esc(r.kind)}</span>
                <span class="rr-title" style="display:block">${esc(r.title)}</span>
                <span class="rr-sub">${esc(r.sub)}</span>
              </span>
              ${ic("chevron_right", "chev")}
            </button><div class="divider-inset"></div>`).join("")}
            <div class="center-box muted small" style="padding:20px">没有更多结果</div>
          </div>`}`,
  };
};
function resultTarget(kind) {
  return { 应用: "appDetail", 动态: "feedDetail", 数码: "productDetail", 用户: "userProfile", 话题: "topicDetail" }[kind] || "searchResults";
}

/* 11 动态详情与评论 */
SCREENS.feedDetail = () => {
  const f = FEEDS.find((x) => x.id === state.params.id) || FEEDS[0];
  const liked = state.liked[f.id] ?? f.liked;
  return {
    nav: null,
    appbar: appbar({ title: "动态详情", back: true, actions: [act.share, act.refresh()] }),
    body: `
      <div class="pad-x" style="padding-top:8px">
        <div class="feed-head">
          ${avatar(f.user, "a46", avatarImg(f.hue))}
          <span class="fh-main"><span class="fh-name">${esc(f.user)}</span><span class="fh-meta">${esc([f.device, f.time].join(" · "))}</span></span>
          <button class="icon-btn small">${ic("more_horiz")}</button>
        </div>
        ${f.title ? `<div class="feed-title">${esc(f.title)}</div>` : ""}
        <div class="feed-body" style="margin-top:8px">${esc(f.message)}</div>
        ${f.images.map((u) => `<div class="feed-media"><img src="${u}" alt=""></div>`).join("")}
        <div class="row mt16" style="justify-content:space-around;padding:8px 0">
          <span class="feed-action ${liked ? "liked" : ""}" data-like="${f.id}">${ic(liked ? "favorite" : "favorite_border", liked ? "ms-fill" : "")}${compact(f.likes + (liked ? 1 : 0))} 赞</span>
          <span class="feed-action">${ic("chat_bubble")}${compact(f.replies)} 评论</span>
          <span class="feed-action" data-go="imageViewer">${ic("share")}分享</span>
        </div>
      </div>
      <div class="hr" style="margin-top:8px"></div>
      <div class="section plain pad-x"><span class="s-title" style="font:var(--t-title-lg);font-weight:700">评论 ${compact(f.replies)}</span></div>
      <div class="chips">${["默认", "最新", "热门"].map((c) => chip(c, c === state.commentSort, `data-comment-sort="${c}"`)).join("")}${chip("只看楼主", false, 'data-toast="仅显示楼主评论（演示）"')}</div>
      <div class="group" style="margin:12px 16px 24px">
        ${COMMENTS.map(commentsBody).join("")}
        <div class="center-box muted small">加载更多</div>
      </div>`,
    bottom: `<div class="pad-x" style="display:flex;gap:12px;align-items:center;padding-top:8px;padding-bottom:12px;border-top:1px solid var(--outline-variant)">
      <button class="btn btn-filled grow" data-overlay="reply">${ic("edit")}写评论</button>
      <button class="icon-btn tonal" data-like="${f.id}">${ic(liked ? "favorite" : "favorite_border", liked ? "ms-fill" : "")}</button>
    </div>`,
  };
};
function commentsBody(c) {
  return `<div class="reply">
    <div class="r-head">
      ${avatar(c.user, "a38", avatarImg(c.hue))}
      <div class="grow">
        <div class="row" style="gap:6px"><span class="r-name">${esc(c.user)}</span>${c.isAuthor ? '<span class="badge-lz">楼主</span>' : ""}</div>
        <div class="r-meta">${esc(c.time)} · ${c.floor} 楼</div>
      </div>
      <button class="icon-btn small ${c.liked ? "" : ""}" data-comment-like="${c.user}" style="${c.liked ? "color:var(--error)" : ""}">${ic("favorite", c.liked ? "ms-fill" : "")}</button>
    </div>
    <div class="r-body">${esc(c.text)}</div>
    ${c.preview ? `<div class="thread-preview">${c.preview.map((p) => `<div class="tp"><b>${esc(p.user)}：</b>${esc(p.text)}</div>`).join("")}</div>` : ""}
    <div class="r-foot">
      <span class="r-act ${c.liked ? "liked" : ""}">${ic("favorite", "")}${c.likes}</span>
      <span class="r-act" data-overlay="reply">${ic("reply")}回复</span>
      ${c.replies > 0 ? `<span class="r-act" data-go="feedThread" data-user="${c.user}">查看 ${compact(c.replies)} 条回复</span>` : ""}
    </div>
  </div>`;
}

/* 12 楼中楼与回复编辑 */
SCREENS.feedThread = () => {
  const parent = COMMENTS.find((c) => c.user === state.params.user) || COMMENTS[0];
  const replies = parent.preview || [];
  return {
    nav: null,
    appbar: appbar({ title: "评论详情", back: true, actions: [act.share] }),
    body: `
      <div class="group" style="margin:8px 16px 0">
        <div class="reply">
          <div class="r-head">${avatar(parent.user, "a38", avatarImg(parent.hue))}
            <div class="grow"><div class="r-name">${esc(parent.user)}</div><div class="r-meta">${esc(parent.time)} · ${parent.floor} 楼</div></div>
          </div>
          <div class="r-body">${esc(parent.text)}</div>
        </div>
      </div>
      <div class="section plain pad-x"><span class="s-title" style="font:var(--t-title-lg);font-weight:700">全部回复</span></div>
      <div class="group" style="margin:0 16px 24px">
        ${(replies.length ? replies : [{ user: "青柠不酸", text: "同问，蹲一个后续。" }]).map((r, i) => `
          <div class="reply">
            <div class="r-head">${avatar(r.user, "a38", avatarImg((r.user.length * 47) % 360))}
              <div class="grow"><div class="r-name">${esc(r.user)}</div><div class="r-meta">${i + 1} 楼 · 回复 @${esc(parent.user)}</div></div>
            </div>
            <div class="r-body">${esc(r.text)}</div>
            <div class="r-foot"><span class="r-act">${ic("favorite")}${3 + i}</span><span class="r-act" data-overlay="reply">${ic("reply")}回复</span></div>
          </div>`).join("")}
      </div>`,
    bottom: `<div class="pad-x" style="display:flex;gap:12px;align-items:center;padding-top:8px;padding-bottom:12px;border-top:1px solid var(--outline-variant)">
      <button class="btn btn-filled grow" data-overlay="reply">${ic("reply")}回复 ${esc(parent.user)}</button>
    </div>`,
  };
};

/* 13 数码产品详情 */
SCREENS.productDetail = () => {
  const p = PRODUCTS.find((x) => x.id === state.params.id) || PRODUCTS[0];
  return {
    nav: null,
    appbar: appbar({ title: p.name, back: true, actions: [act.share] }),
    body: `
      <div class="content-narrow">
        <div class="photo-hero" style="padding:0 16px">
          <div class="card" style="border-radius:var(--r-lg)">
            <img src="${photo(p.name, "#1A2E24", "#3E8A5E")}" style="width:100%;height:210px;object-fit:cover;display:block">
          </div>
        </div>
        <div class="pad-x mt16">
          <div style="font:var(--t-headline-sm);font-weight:800">${esc(p.name)}</div>
          <div class="muted mt6">${esc(p.brand)} · ${esc(p.cat)}</div>
          <div class="row mt16" style="gap:28px">
            <div><div class="rating-big">${p.score}</div><div class="small muted mt6">酷友评分 · ${compact(p.ratingCount)} 人</div></div>
            <div><div class="rating-big" style="color:var(--secondary)">${compact(p.hot)}</div><div class="small muted mt6">热度</div></div>
          </div>
          <div class="feed-body mt16">${esc(p.desc)}</div>
        </div>
        <div class="section pad-x plain"><span class="s-title" style="font:var(--t-title-lg);font-weight:700">产品规格</span></div>
        <div class="group" style="margin:0 16px">
          ${p.specs.map(([k, v]) => `<div class="spec-row"><span class="k">${esc(k)}</span><span class="v">${esc(v)}</span></div>`).join("")}
        </div>
        <div class="section pad-x plain"><span class="s-title" style="font:var(--t-title-lg);font-weight:700">产品图片</span></div>
        <div class="feed-gallery pad-x" style="padding-top:0;padding-bottom:24px">
          ${[1, 2, 3].map((i) => `<img class="gi" style="width:128px;height:128px;border-radius:16px" src="${photo(`${p.name} ${i}`, "#1A2E24", "#3E8A5E")}" data-go="imageViewer" alt="">`).join("")}
        </div>
      </div>`,
  };
};

/* 14 应用详情 */
SCREENS.appDetail = () => {
  const a = APPS.find((x) => x.name === state.params.name) || APPS[0];
  return {
    nav: null,
    appbar: appbar({ title: a.name, back: true }),
    body: `
      <div class="content-narrow">
        <div class="pad-x mt10" style="display:flex;gap:16px">
          <span class="avatar squircle" style="width:72px;height:72px;background:var(--primary-container);color:var(--on-primary-container);font-size:30px">酷</span>
          <div class="grow">
            <div style="font:var(--t-title-lg);font-weight:700">${esc(a.name)}</div>
            <div class="muted small mt6">${esc(a.dev)}</div>
            <div class="muted small" style="margin-top:2px">${esc(a.id)}</div>
          </div>
        </div>
        <div class="pad-x mt16">
          <div class="row" style="gap:22px;flex-wrap:wrap">
            <span class="pill-info">${ic("star", "ms-fill")}评分 ${a.score}</span>
            <span class="pill-info" style="background:var(--secondary-container);color:var(--on-secondary-container)">${ic("download")}下载 ${a.downloads}</span>
            <span class="pill-info" style="background:var(--surface-high);color:var(--on-surface-variant)">${ic("sd_storage")}${a.size}</span>
          </div>
          <div class="muted small mt10">版本 ${a.version} · 更新 3天前</div>
          <button class="btn btn-filled btn-block mt16" data-toast="将在酷安客户端中打开（演示）">${ic("open_in_new")}在酷安打开</button>
        </div>
        <div class="section pad-x plain"><span class="s-title" style="font:var(--t-title-lg);font-weight:700">截图</span></div>
        <div class="feed-gallery pad-x" style="padding-top:0">
          ${a.shots.map((s) => `<img class="gi" style="width:150px;height:266px;border-radius:16px" src="${s}" data-go="imageViewer" alt="">`).join("")}
        </div>
        <div class="section pad-x plain"><span class="s-title" style="font:var(--t-title-lg);font-weight:700">应用介绍</span></div>
        <div class="pad-x feed-body" style="padding-bottom:8px">${esc(a.desc)}</div>
        <div class="section pad-x plain"><span class="s-title" style="font:var(--t-title-lg);font-weight:700">更新日志</span></div>
        <div class="pad-x feed-body" style="padding-bottom:32px">${esc(a.changelog)}</div>
      </div>`,
  };
};

/* 15 通知中心 */
SCREENS.notifications = () => {
  const cats = Object.keys(NOTIFICATIONS);
  const titles = { COMMENT: "评论回复", MENTION: "@ 提及", COMMENT_MENTION: "评论 @", LIKE: "收到的赞", FOLLOW: "新关注" };
  const list = NOTIFICATIONS[cats.find((k) => titles[k] === state.notifTab)] || [];
  return {
    nav: null,
    appbar: appbar({ title: "消息", back: true, actions: [act.refresh()] }),
    body: `
      <div class="tabs">${cats.map((k) => `<button class="tab ${titles[k] === state.notifTab ? "active" : ""}" data-notif-tab="${titles[k]}">${titles[k]}</button>`).join("")}</div>
      ${list.length === 0
        ? `<div class="state"><div class="st-icon">${ic("notifications_off")}</div><div class="st-title">暂无${esc(state.notifTab)}通知</div><div class="st-action"><button class="btn btn-text" data-toast="已刷新">刷新</button></div></div>`
        : `<div class="group" style="margin:12px 16px 24px">
            ${list.map((n) => `<button class="notif-row" ${n.target ? 'data-go="feedDetail" data-id="5101"' : 'data-go="userProfile"'}>
              ${avatar(n.user, "a44", avatarImg(n.hue))}
              <span class="n-main">
                <span class="n-top"><span class="n-name">${esc(n.user)}</span>${n.unread ? '<span class="n-dot"></span>' : ""}<span class="n-time">${esc(n.time)}</span></span>
                <span class="n-msg" style="display:block">${esc(n.msg)}</span>
                ${n.target ? `<span class="n-target">${esc(n.target)}</span>` : ""}
              </span>
              ${n.target ? ic("chevron_right", "chev") : ""}
            </button><div class="divider-inset"></div>`).join("")}
          </div>`}`,
  };
};

/* 16 官方网页登录容器 */
SCREENS.officialLogin = () => ({
  nav: null,
  appbar: appbar({ title: "酷安官方登录", back: true, actions: [act.refresh()] }),
  body: `
    <div class="progress-line"><i></i></div>
    <div class="webview-frame">
      <div class="wv-card">
        ${ic("lock", "")}
        <div style="font-weight:700;color:#102019;margin-top:8px;font-size:15px">酷安账号登录</div>
        <div style="font-size:12px;color:#7C8A81;margin-top:6px">这里加载酷安官方网页，<br>请在网页中完成账号登录</div>
        <div style="margin-top:14px;display:flex;flex-direction:column;gap:8px">
          <div style="height:40px;border-radius:10px;background:#EEF2EF"></div>
          <div style="height:40px;border-radius:10px;background:#EEF2EF"></div>
          <div style="height:40px;border-radius:10px;background:#05844B"></div>
        </div>
      </div>
      <div class="wv-host">account.coolapk.com</div>
    </div>`,
  bottom: `<div class="pad-x" style="display:flex;flex-direction:column;gap:6px;padding-top:10px;padding-bottom:12px;border-top:1px solid var(--outline-variant)">
    <button class="btn btn-filled btn-block" data-toast="校验登录会话中…">${ic("check_circle")}完成登录</button>
    <button class="btn btn-text btn-block" data-go="cookieImport">改用 Cookie 导入</button>
  </div>`,
});

/* 17 Cookie 导入弹层（作为整页状态展示） */
SCREENS.cookieImport = () => ({
  nav: null,
  appbar: appbar({ title: "导入酷安 Cookie", back: true }),
  body: `
    <div class="pad-x" style="padding-top:16px">
      <div class="card outlined" style="padding:14px">
        <div class="row" style="color:var(--tertiary);font-weight:700">${ic("warning")}仅在网页登录不可用时使用</div>
        <div class="muted small mt6" style="line-height:1.7">从酷安网页会话复制 Cookie 字符串后粘贴到下方。内容包含账号凭证，请勿分享给他人。</div>
      </div>
      <div class="mt16">
        <div class="small muted" style="margin-bottom:6px">Cookie</div>
        <div class="dialog d-field" style="margin:0;min-height:130px;color:var(--on-surface-variant)">&#8203;<span class="muted">粘贴形如 uid=…; username=…; token=… 的会话字符串</span></div>
        <div class="small" style="color:var(--error);margin-top:8px">示例凭证无效，请重新导入（演示错误反馈）</div>
      </div>
    </div>`,
  bottom: `<div class="pad-x" style="display:flex;gap:10px;padding-top:10px;padding-bottom:12px;border-top:1px solid var(--outline-variant)">
    <button class="btn btn-text grow" data-back>取消</button>
    <button class="btn btn-filled grow" data-toast="校验并保存会话（演示）">登录</button>
  </div>`,
});

/* 18 全屏图片查看 */
SCREENS.imageViewer = () => ({
  nav: null,
  fullscreen: true,
  hideStatusbar: true,
  body: `
    <div class="viewer">
      <div class="v-top">
        <button class="icon-btn" data-back>${ic("close")}</button>
        <span class="v-title">图片</span>
      </div>
      <div class="v-stage"><img src="${photo("夜景 · 江边", "#0E3B2E", "#1E8F63")}" alt=""></div>
      <div class="v-bottom">
        <button class="icon-btn" data-toast="上一张">${ic("chevron_left")}</button>
        <span class="v-counter">2 / 3</span>
        <button class="icon-btn" data-toast="下一张">${ic("chevron_right")}</button>
      </div>
      <div class="center-box" style="color:#8A9A90;font-size:12px;padding:0 0 22px">双指缩放查看细节 · 左右滑动切换</div>
    </div>`,
});

/* 19 应用列表（辅助入口） */
SCREENS.appsList = () => ({
  nav: null,
  appbar: appbar({ title: "应用", back: true, actions: [act.search] }),
  body: `
    <div class="chips mt6">${["推荐", "最新", "工具", "社交"].map((c) => chip(c, c === "推荐", `data-toast="切换到「${c}」"`)).join("")}</div>
    <div class="pad-x mt10" style="display:flex;flex-direction:column;gap:10px;padding-bottom:24px">
      ${APPS.map((a) => `<button class="card tonal press" data-go="appDetail" data-name="${esc(a.name)}" style="display:flex;gap:14px;padding:16px;align-items:center;text-align:left">
        <span class="avatar squircle" style="width:58px;height:58px;background:var(--primary-container);color:var(--on-primary-container);font-size:24px">${esc(a.name.slice(0, 1))}</span>
        <span class="grow">
          <span class="lr-title">${esc(a.name)}</span>
          <span class="lr-sub" style="display:block">${esc(a.desc.slice(0, 22))}…</span>
          <span class="lr-meta" style="display:block">${esc(a.version)} · 评分 ${a.score} · ${esc(a.downloads)}</span>
        </span>
        ${ic("chevron_right", "chev")}
      </button>`).join("")}
    </div>`,
});

/* 20 通用状态展示 */
SCREENS.states = () => ({
  nav: null,
  appbar: appbar({ title: "通用状态", back: true }),
  body: `
    <div class="pad-x" style="padding-bottom:32px">
      <div class="section plain"><span class="s-title">首次加载</span></div>
      <div class="card" style="padding:16px;display:flex;flex-direction:column;gap:10px">
        <div class="skeleton" style="height:46px;width:60%"></div>
        <div class="skeleton" style="height:14px;width:100%"></div>
        <div class="skeleton" style="height:14px;width:88%"></div>
        <div class="skeleton" style="height:150px;width:100%;border-radius:18px"></div>
      </div>

      <div class="section plain"><span class="s-title">空列表</span></div>
      <div class="card" style="padding:0">
        <div class="state" style="padding:34px 24px">
          <div class="st-icon">${ic("inbox")}</div>
          <div class="st-title" style="font-size:18px">暂无动态</div>
          <div class="st-detail">下拉刷新试试</div>
          <div class="st-action"><button class="btn btn-text" data-toast="已刷新">刷新</button></div>
        </div>
      </div>

      <div class="section plain"><span class="s-title">搜索无结果</span></div>
      <div class="card" style="padding:0">
        <div class="state" style="padding:34px 24px">
          <div class="st-icon">${ic("search_off")}</div>
          <div class="st-title" style="font-size:18px">未找到相关结果</div>
          <div class="st-detail">试试其他关键词或切换分类</div>
        </div>
      </div>

      <div class="section plain"><span class="s-title">网络失败重试</span></div>
      <div class="card" style="padding:0">
        <div class="state" style="padding:34px 24px">
          <div class="st-icon">${ic("cloud_off")}</div>
          <div class="st-title" style="font-size:18px">加载失败</div>
          <div class="st-detail">无法连接到服务器，请检查网络后重试</div>
          <div class="st-action"><button class="btn btn-filled" data-toast="重试中…">重试</button></div>
        </div>
      </div>

      <div class="section plain"><span class="s-title">游客权限提示</span></div>
      <div class="card" style="padding:0">
        <div class="state primary" style="padding:34px 24px">
          <div class="st-icon">${ic("lock")}</div>
          <div class="st-title" style="font-size:18px">登录后查看</div>
          <div class="st-detail">该内容需要登录后访问</div>
          <div class="st-action"><button class="btn btn-filled" data-go="officialLogin">网页登录</button></div>
        </div>
      </div>

      <div class="section plain"><span class="s-title">提交中 / 骨架按钮</span></div>
      <div class="card" style="padding:16px;display:flex;gap:10px;flex-wrap:wrap">
        <button class="btn btn-filled" disabled><span class="spinner sm"></span>发布中</button>
        <button class="btn btn-tonal" disabled>发送中</button>
        <button class="btn btn-outlined" data-toast="Snackbar 演示">触发 Snackbar</button>
        <button class="btn btn-text" data-overlay="confirm">确认对话框</button>
      </div>

      <div class="section plain"><span class="s-title">刷新保留原内容</span></div>
      <div class="card" style="padding:0;overflow:hidden">
        <div class="progress-line" style="position:relative;z-index:2"><i></i></div>
        <div style="padding:12px;display:flex;flex-direction:column;gap:10px;opacity:.55">
          <div class="skeleton" style="height:44px;width:50%"></div>
          <div class="skeleton" style="height:12px;width:100%"></div>
          <div class="skeleton" style="height:12px;width:76%"></div>
        </div>
        <div class="center-box muted small" style="padding:12px">正在刷新，旧内容保持显示</div>
      </div>
    </div>`,
});

/* 22 话题详情 */
SCREENS.topicDetail = () => ({
  nav: null,
  appbar: appbar({ title: "数码好物", back: true, actions: [act.refresh()] }),
  body: `
    <div class="pad-x" style="padding-top:16px">
      <div class="row" style="gap:14px;align-items:flex-start">
        <span class="avatar a68" style="background:var(--primary-container);color:var(--on-primary-container)">#</span>
        <div class="grow">
          <div style="font:var(--t-title-lg);font-weight:800">数码好物</div>
          <div class="muted small mt6">12.6万 热度 &nbsp;·&nbsp; 3.4万 讨论 &nbsp;·&nbsp; 8.1千 关注</div>
        </div>
      </div>
      <div class="feed-body mt16">分享你最近入手的数码好物，聊聊真实的使用感受。理性种草，拒绝云评测。</div>
    </div>
    <div class="hr mt16"></div>
    <div class="chips mt16">${["默认", "最新", "热度"].map((c) => chip(c, c === "最新", `data-toast="按「${c}」排序（演示）"`)).join("")}</div>
    <div class="pad-x feed-list mt10" style="padding-bottom:24px">
      ${FEEDS.slice(0, 4).map((f) => feedCard(f)).join("")}
      <div class="center-box muted small" style="padding:12px">没有更多动态了</div>
    </div>`,
});

/* 23 酷友主页 */
SCREENS.userProfile = () => ({
  nav: null,
  appbar: appbar({ title: "极客小张", back: true, actions: [act.refresh()] }),
  body: profileBody(false),
});

/* 21 设计系统面板 */
SCREENS.designSystem = () => {
  const swatch = (name, v, darkText = false) =>
    `<div class="swatch"><div class="sw-color" style="background:${v};${darkText ? "color:#102019" : ""}"></div><div class="sw-meta"><div class="sw-name">${name}</div><div class="sw-hex">${v}</div></div></div>`;
  const cs = getComputedStyle(document.documentElement);
  const val = (n) => cs.getPropertyValue(n).trim();
  return {
    nav: null,
    appbar: appbar({ title: "设计系统", back: true, actions: [`<span class="small muted" style="margin-right:12px">${state.theme === "dark" ? "暗色" : "亮色"}</span>`] }),
    body: `
      <div class="ds-row">
        <div class="ds-label">语义色 · 品牌</div>
        <div class="swatches">
          ${swatch("primary", val("--primary"))}
          ${swatch("primary container", val("--primary-container"))}
          ${swatch("secondary", val("--secondary"))}
          ${swatch("secondary container", val("--secondary-container"))}
          ${swatch("tertiary", val("--tertiary"))}
          ${swatch("tertiary container", val("--tertiary-container"))}
        </div>
      </div>
      <div class="ds-row">
        <div class="ds-label">语义色 · 表面</div>
        <div class="swatches">
          ${swatch("surface", val("--surface"))}
          ${swatch("surface low", val("--surface-low"))}
          ${swatch("surface container", val("--surface-container"))}
          ${swatch("surface high", val("--surface-high"))}
          ${swatch("surface highest", val("--surface-highest"))}
          ${swatch("outline variant", val("--outline-variant"))}
        </div>
      </div>
      <div class="ds-row">
        <div class="ds-label">字体层级</div>
        <div class="type-sample" style="font:var(--t-display)">重要数字 40</div>
        <div class="type-sample" style="font:var(--t-headline-sm)">页面标题 24</div>
        <div class="type-sample" style="font:var(--t-title-lg)">模块标题 22</div>
        <div class="type-sample" style="font:var(--t-body-lg)">正文 15／1.6，阅读舒适</div>
        <div class="type-sample" style="font:var(--t-label-md)">辅助文字 12</div>
      </div>
      <div class="ds-row">
        <div class="ds-label">形状层级</div>
        <div class="shape-row">
          <div class="shape-box" style="border-radius:8px"></div>
          <div class="shape-box" style="border-radius:12px"></div>
          <div class="shape-box" style="border-radius:18px"></div>
          <div class="shape-box" style="border-radius:26px"></div>
          <div class="shape-box" style="border-radius:34px"></div>
          <div class="shape-box" style="border-radius:999px"></div>
        </div>
      </div>
      <div class="ds-row">
        <div class="ds-label">按钮</div>
        <div style="display:flex;gap:10px;flex-wrap:wrap">
          <button class="btn btn-filled">${ic("check")}填充</button>
          <button class="btn btn-tonal">${ic("edit")}次级</button>
          <button class="btn btn-outlined">描边</button>
          <button class="btn btn-text">文字</button>
          <button class="btn btn-filled" disabled>禁用</button>
        </div>
      </div>
      <div class="ds-row">
        <div class="ds-label">筛选胶囊</div>
        <div class="chips plain">${["全部", "推荐", "热榜"].map((c, i) => chip(c, i === 1)).join("")}</div>
      </div>
      <div class="ds-row">
        <div class="ds-label">分段控件</div>
        <div class="segmented"><button class="active">编辑</button><button>预览</button></div>
      </div>
      <div class="ds-row">
        <div class="ds-label">图标（Material Symbols Rounded）</div>
        <div class="icon-cloud">
          ${["home", "apps", "add", "explore", "person", "search", "notifications", "favorite", "chat_bubble", "share", "image", "star", "download", "verified", "schedule", "tag"].map((n) => ic(n)).join("")}
        </div>
      </div>
      <div class="ds-row">
        <div class="ds-label">分组列表容器</div>
        <div class="group">
          <div class="list-row"><div class="grow"><div class="lr-title">首尾大圆角</div></div>${ic("chevron_right", "chev")}</div>
          <div class="list-row"><div class="grow"><div class="lr-title">中间共享分隔线</div></div>${ic("chevron_right", "chev")}</div>
          <div class="list-row"><div class="grow"><div class="lr-title">整组作为一个表面</div></div>${ic("chevron_right", "chev")}</div>
        </div>
      </div>
      <div style="height:32px"></div>`,
  };
};

/* ---------------------------- 渲染 ---------------------------- */
function render(appbarOnly = false) {
  const device = $("#device");
  device.dataset.theme = "";
  document.documentElement.dataset.theme = state.theme;
  device.dataset.width = state.width;
  document.querySelectorAll(".cat-item").forEach((el) => el.classList.toggle("active", el.dataset.cat === state.screen));
  document.querySelectorAll("[data-vwidth]").forEach((el) => el.classList.toggle("active", el.dataset.vwidth === state.width));
  document.querySelectorAll("[data-vtheme]").forEach((el) => el.classList.toggle("active", el.dataset.vtheme === state.theme));
  document.querySelectorAll("[data-vview]").forEach((el) => el.classList.toggle("active", el.dataset.vview === state.view));
  renderOverview();

  const s = SCREENS[state.screen];
  if (!s) return;
  const out = s() || {};
  const navIndex = out.nav;

  device.innerHTML = `
    ${out.hideStatusbar ? "" : statusbar(state.theme)}
    <div class="app">
      ${state.width === "840" && navIndex != null ? rail(navIndex) : ""}
      <div class="app-main">
        ${out.appbar || ""}
        <div class="screen ${appbarOnly ? "back" : "enter"}">${out.body || ""}</div>
        ${out.bottom || ""}
        ${navIndex != null && state.width !== "840" ? navBar(navIndex) : ""}
      </div>
    </div>
    ${overlayHtml()}
    ${state.snack ? `<div class="snackbar">${ic("check_circle")}${esc(state.snack)}</div>` : ""}
  `;
  bindEditor();
}

function statusbar(theme) {
  return `<div class="statusbar">
    <span>9:41</span>
    <span class="sb-right">${ic("signal_cellular_alt")}${ic("wifi")}${ic("battery_full")}</span>
  </div>`;
}

function renderOverview() {
  const ov = $("#overview");
  ov.innerHTML = CATALOG.map(([num, name, screen, desc]) => `
    <button class="ov-card" data-cat="${screen}">
      <div class="n">${num}</div>
      <div class="t">${name}</div>
      <div class="d">${desc}</div>
    </button>`).join("");
}

function overlayHtml() {
  const o = state.overlay;
  if (!o) return "";
  if (o === "reply")
    return `<div class="scrim" data-overlay-close><div class="dialog" onclick="event.stopPropagation()">
      <div class="d-title">写评论</div>
      <div class="d-field" contenteditable="true" data-reply-field style="color:var(--on-surface)"></div>
      <div class="d-support">评论内容 · 支持纯文本</div>
      <div class="d-actions"><button class="btn btn-text" data-overlay-close>取消</button><button class="btn btn-filled" data-send-reply>发送</button></div>
    </div></div>`;
  if (o === "logout")
    return `<div class="scrim" data-overlay-close><div class="dialog" onclick="event.stopPropagation()">
      <div class="d-title">退出登录？</div>
      <div class="d-text">退出后将清除本机保存的登录会话，仍可继续以游客身份浏览内容。</div>
      <div class="d-actions"><button class="btn btn-text" data-overlay-close>取消</button><button class="btn btn-filled" data-logout>退出</button></div>
    </div></div>`;
  if (o === "confirm")
    return `<div class="scrim" data-overlay-close><div class="dialog" onclick="event.stopPropagation()">
      <div class="d-title">保留这份草稿？</div>
      <div class="d-text">返回后内容会自动保存在当前账号的草稿中。</div>
      <div class="d-actions"><button class="btn btn-text" data-overlay-close>返回</button><button class="btn btn-tonal" data-overlay-close>继续编辑</button></div>
    </div></div>`;
  if (o === "clear")
    return `<div class="scrim" data-overlay-close><div class="dialog" onclick="event.stopPropagation()">
      <div class="d-title">清除草稿？</div>
      <div class="d-text">当前账号保存的发布内容会被删除，且无法恢复。</div>
      <div class="d-actions"><button class="btn btn-text" data-overlay-close>取消</button><button class="btn btn-filled" data-overlay-close data-toast="草稿已清除">清除</button></div>
    </div></div>`;
  if (o === "published")
    return `<div class="scrim"><div class="dialog" onclick="event.stopPropagation()" style="text-align:center">
      <div class="state primary" style="padding:0 0 6px"><div class="st-icon">${ic("check_circle")}</div></div>
      <div class="d-title">发布成功</div>
      <div class="d-text">你的动态已发布到酷安。</div>
      <div class="d-actions" style="justify-content:center"><button class="btn btn-filled" data-publish-done-ok>完成</button></div>
    </div></div>`;
  return "";
}

function bindEditor() {
  const ed = $("#publish-editor");
  if (ed) {
    if (!ed.dataset.bound) {
      ed.dataset.bound = "1";
      ed.addEventListener("input", () => {
        const raw = ed.innerText;
        if (raw.length > 1000) ed.innerText = raw.slice(0, 1000);
        const len = ed.innerText.length;
        const counter = document.querySelector(".counter");
        if (counter) {
          counter.textContent = `${len}/1000`;
          counter.classList.toggle("over", len > 1000);
        }
        const btn = document.querySelector("[data-go='publishPreview']");
        if (btn) btn.disabled = len === 0;
      });
    }
  }
}

/* ---------------------------- 目录 ---------------------------- */
const CATALOG = [
  ["01", "首页动态流", "home", "信息流、九宫格、点赞评论与分页"],
  ["02", "数码内容首页", "digital", "栏目切换、精选轮播、品牌快选"],
  ["03", "数码品牌/分类", "digitalLibrary", "左右分栏、筛选与产品列表"],
  ["04", "发布编辑器", "publish", "编辑/预览、字数、草稿"],
  ["05", "发布预览确认", "publishPreview", "保真预览与发布反馈"],
  ["06", "发现", "discovery", "栏目页签、筛选与站内/网页卡片"],
  ["07", "我的（已登录）", "profileOwn", "资料、统计与个人动态"],
  ["08", "我的（游客）", "profileGuest", "登录说明与网页登录入口"],
  ["09", "搜索入口", "searchHome", "胶囊搜索栏与热门搜索"],
  ["10", "搜索结果", "searchResults", "八类结果与问答子筛选"],
  ["11", "动态详情与评论", "feedDetail", "评论排序、只看楼主、点赞"],
  ["12", "楼中楼与回复", "feedThread", "父级上下文与回复编辑"],
  ["13", "数码产品详情", "productDetail", "大图、评分热度、规格表"],
  ["14", "应用详情", "appDetail", "图标、截图、介绍与更新日志"],
  ["15", "通知中心", "notifications", "五类只读通知与动态跳转"],
  ["16", "官方网页登录", "officialLogin", "原生容器与完成登录"],
  ["17", "Cookie 导入", "cookieImport", "敏感输入与错误反馈"],
  ["18", "全屏图片查看", "imageViewer", "暗色背景、页码与切换"],
  ["19", "应用列表（辅助）", "appsList", "保留的列表样式入口"],
  ["20", "通用状态展示", "states", "加载/空/错误/提交/对话框"],
  ["21", "话题详情", "topicDetail", "话题头图、统计与动态流"],
  ["22", "酷友主页", "userProfile", "他人主页与关注入口"],
  ["23", "设计系统面板", "designSystem", "色彩/字体/形状/组件总览"],
];

function renderCatalog() {
  const groups = [
    ["主入口", ["home", "digital", "digitalLibrary", "discovery", "profileOwn", "profileGuest"]],
    ["内容与详情", ["feedDetail", "feedThread", "productDetail", "appDetail", "appsList", "topicDetail", "userProfile"]],
    ["搜索", ["searchHome", "searchResults"]],
    ["发布", ["publish", "publishPreview"]],
    ["账号", ["officialLogin", "cookieImport"]],
    ["消息与媒体", ["notifications", "imageViewer"]],
    ["状态与规范", ["states", "designSystem"]],
  ];
  $("#catalog-list").innerHTML = groups.map(([label, keys]) => `
    <div class="group-label">${label}</div>
    ${keys.map((k) => {
      const item = CATALOG.find((c) => c[2] === k);
      if (!item) return "";
      return `<button class="cat-item" data-cat="${k}"><span class="idx">${item[0]}</span><span>${item[1]}</span></button>`;
    }).join("")}
  `).join("");
}

/* ---------------------------- 事件 ---------------------------- */
function bind() {
  document.addEventListener("click", (e) => {
    const t = e.target.closest("[data-cat],[data-go],[data-back],[data-tab],[data-like],[data-overlay],[data-overlay-close],[data-toast],[data-digital-tab],[data-digital-mode],[data-digital-pick],[data-discovery-tab],[data-discovery-filter],[data-comment-sort],[data-search-cat],[data-search-ask],[data-notif-tab],[data-publish-mode],[data-publish-done],[data-publish-done-ok],[data-clear-draft],[data-send-reply],[data-logout],[data-comment-like],[data-vwidth],[data-vtheme],[data-vview],.tab,.chip");
    if (!t) return;

    if (t.dataset.vwidth !== undefined) { state.width = t.dataset.vwidth; render(); return; }
    if (t.dataset.vtheme !== undefined) { state.theme = t.dataset.vtheme; render(); return; }
    if (t.dataset.vview !== undefined) { state.view = t.dataset.vview; render(); return; }

    if (t.dataset.cat) { go(t.dataset.cat); return; }
    if (t.dataset.back !== undefined) { back(); return; }
    if (t.dataset.tab !== undefined) {
      const i = +t.dataset.tab;
      go(["home", "digital", "", "discovery", "profileOwn"][i]);
      return;
    }
    if (t.dataset.like !== undefined) {
      const id = t.dataset.like;
      const base = FEEDS.find((f) => f.id === id);
      const cur = state.liked[id] ?? (base ? base.liked : false);
      state.liked[id] = !cur;
      render();
      toast(!cur ? "已点赞" : "已取消点赞");
      return;
    }
    if (t.dataset.commentLike !== undefined) { t.classList.toggle("liked"); return; }

    if (t.dataset.overlay) { state.overlay = t.dataset.overlay; render(); return; }
    if (t.dataset.overlayClose !== undefined) {
      if (t.classList.contains("scrim") && e.target !== t) return;
      state.overlay = null;
      render();
      return;
    }

    if (t.dataset.digitalTab) { state.digitalTab = t.dataset.digitalTab; render(); return; }
    if (t.dataset.digitalMode) { state.digitalMode = t.dataset.digitalMode; state.digitalBrand = t.dataset.digitalMode === "品牌" ? "Xiaomi" : "旗舰手机"; render(); return; }
    if (t.dataset.digitalPick) { state.digitalBrand = t.dataset.digitalPick; render(); return; }
    if (t.dataset.discoveryTab) { state.discoveryTab = t.dataset.discoveryTab; render(); return; }
    if (t.dataset.discoveryFilter) { state.discoveryFilter = t.dataset.discoveryFilter; render(); return; }
    if (t.dataset.commentSort) { state.commentSort = t.dataset.commentSort; render(); return; }
    if (t.dataset.searchCat) { state.searchCat = t.dataset.searchCat; render(); return; }
    if (t.dataset.searchAsk) { state.searchAsk = t.dataset.searchAsk; render(); return; }
    if (t.dataset.notifTab) { state.notifTab = t.dataset.notifTab; render(); return; }
    if (t.dataset.publishMode) {
      state.publishMode = t.dataset.publishMode;
      if (t.dataset.go) { go(t.dataset.go); return; }
      render(); return;
    }
    if (t.dataset.clearDraft) { state.overlay = "clear"; render(); return; }
    if (t.dataset.sendReply !== undefined) { state.overlay = null; render(); toast("评论已发送"); return; }
    if (t.dataset.logout !== undefined) { state.overlay = null; render(); toast("已退出登录"); go("profileGuest"); return; }
    if (t.dataset.publishDone !== undefined) { state.overlay = "published"; render(); return; }
    if (t.dataset.publishDoneOk !== undefined) { state.overlay = null; go("home"); toast("发布成功"); return; }

    if (t.dataset.go) {
      // capture publish text from editor before leaving
      const ed = $("#publish-editor");
      if (ed) state.publishText = ed.innerText.trim();
      go(t.dataset.go, { id: t.dataset.id, user: t.dataset.user, name: t.dataset.name });
      return;
    }
    if (t.dataset.toast) { toast(t.dataset.toast); return; }
  }, true);

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && state.overlay) { state.overlay = null; render(); }
  });
}

/* ---------------------------- 启动 ---------------------------- */
renderCatalog();
bind();
render();
