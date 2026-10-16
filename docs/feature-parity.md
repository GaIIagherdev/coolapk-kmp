# Android 功能对齐清单

目标：Android 端功能对齐参考项目 `coolapk-desktop`（只读），视觉统一使用 M3E。本清单按参考项目的路由（`src/router/index.ts`）整理，状态以代码为准。

图例：✅ 已有对应界面　🟡 部分覆盖　⬜ 未开始　❓ 待核对

## 首页与发现

| 功能 | 参考路由 | Android 状态 | 备注 |
| --- | --- | --- | --- |
| 首页动态（频道） | `/` | ✅ | 推荐、热榜、快讯等频道待核对是否齐全 |
| 发现 | `/discover` | ✅ | `DiscoveryScreen` |
| 热榜 / 快讯 | `/headline` | ❓ | |
| 应用中心 | `/apps` | ⬜ | 参考页为关键词搜索列表 |
| 游戏中心 | `/games` | ⬜ | |
| 话题广场 | `/topics` | 🟡 | 有 `TopicDetailScreen`，无广场页 |
| 评测区 | `/reviews` | ⬜ | |
| 数码栏目 | `/digital` | ✅ | `DigitalScreen` |

## 动态与互动

| 功能 | 参考路由 | Android 状态 | 备注 |
| --- | --- | --- | --- |
| 动态详情与评论 | `/feed/:id` | ✅ | 含易盾验证码弹层，见 `docs/known-issues.md` |
| 问答详情 | `/question/:id` | ⬜ | |
| 直播详情 | `/live/:id` | ⬜ | |
| 发布动态 | — | ✅ | 纯文本，媒体发布未做 |
| 图片查看 | `/pictures` | ✅ | `ImageViewerDialog` |
| 相册 | `/albums`、`/album/:id`、`/my-albums` | ⬜ | |
| 活动列表与详情 | `/events`、`/event/:id` | ⬜ | |
| 自定义列表（anylist） | `/anylist`、`/anylist/:id`、`/anylist/create` | ⬜ | |
| 节点 / 论坛 | `/node/:type/:id` | ⬜ | |
| 公众号（dyh） | `/dyh/:id`、`/my-dyh` | ⬜ | |
| 页面数据列表 | `/page` | ⬜ | |
| 外部网页 | `/external` | ⬜ | |

## 数码与购物

| 功能 | 参考路由 | Android 状态 | 备注 |
| --- | --- | --- | --- |
| 产品详情 | `/product/:id` | ✅ | `DigitalProductDetailScreen` |
| 产品选择器 | `/product-selector` | ✅ | |
| 产品对比 | `/product-compare` | ✅ | |
| 我的数码吧 | `/my-products` | ❓ | 可能由 `MyHubScreen` 覆盖 |
| 二手 | `/secondhand`、`/secondhand/brands`、`/secondhand/list` | ⬜ | |
| 好物 | `/goods` | 🟡 | `MyGoodsScreen` 为我的好物，搜索、清单、排行待核对 |
| 好物清单详情 | `/goods/lists/:id` | ⬜ | |
| 好物排行详情 | `/goods/ranking/:id` | ⬜ | |

## 搜索与应用

| 功能 | 参考路由 | Android 状态 | 备注 |
| --- | --- | --- | --- |
| 综合搜索 | `/search` | ✅ | `SearchScreen` |
| 应用详情 | `/app/:packageName` | ✅ | `AppDetailScreen` |
| 下载管理 | `/downloads` | ⬜ | |

## 用户与关系

| 功能 | 参考路由 | Android 状态 | 备注 |
| --- | --- | --- | --- |
| 用户主页 | `/user/:uid` | ✅ | `UserProfileScreen` |
| 关注 / 粉丝 | `/user/:uid/relations/:follow\|fans` | ✅ | `RelationsScreen` |
| 关注列表（我的） | `/following` | ❓ | |
| 最近联系人 | `/recent-contacts` | ⬜ | |
| 私信会话与聊天 | `/messages` | ✅ | |
| 黑名单 | `/blacklist` | ⬜ | |

## 我的

| 功能 | 参考路由 | Android 状态 | 备注 |
| --- | --- | --- | --- |
| 我的页（入口汇总） | `/me`、`/my` | ✅ | `MyHubScreen` |
| 资料编辑 | — | ✅ | `ProfileEditScreen` |
| 我的赞 | `/my-likes` | ⬜ | |
| 我的评论 | `section=my_comments` | ⬜ | |
| 我的动态 | `section=my_feeds` | ⬜ | |
| 浏览历史 | `/history` | ⬜ | |
| 我的常去 | `section=my_recent` | ⬜ | |
| 收藏与收藏单 | `/favorites`、`/collection/:id` | ⬜ | |
| 关注的论坛 / 话题 / 收藏单 / 问题 / 数码吧 | `/followed-nodes` 等 | ⬜ | |
| 内容回收站 | `/recycle-bin` | ⬜ | |
| 隐藏的回复 | `/hidden-replies` | ⬜ | |
| 我的设备 | `/my-devices` | ⬜ | |
| 我的投票 | `/my-votes` | ⬜ | |
| 插件 | `/my-plugins`、`/my-plugins/store` | ⬜ | |
| 通知 | `/notifications` | ✅ | `NotificationScreen` |

## 设置

参考项目的设置分为 13 个子页：资料、账号、通知、隐私、内容、下载、网络、外观、快捷键、启动、设备、诊断、关于。

| 子页 | Android 状态 |
| --- | --- |
| 设置总页（`SettingsScreen`） | ✅ |
| 账号与登录（官方网页登录） | ✅ |
| 外观（主题） | 🟡 |
| 资料、账号、通知、隐私、内容、下载、网络、快捷键、启动、设备、诊断、关于 | ⬜ |

## 建议顺序

按用户可感知价值和依赖关系，先做以下几项，每项独立成一次提交：

1. 收藏与收藏单、我的赞、我的评论、我的动态（依赖现有 `MyHubScreen` 入口）
2. 浏览历史、黑名单、关注的论坛 / 话题（列表型，结构相似，可共用一个分页列表组件）
3. 问答详情、直播详情、相册、活动（独立详情页）
4. 二手、好物清单与排行（需先确认 Rust 接口是否已有）
5. 应用中心、游戏中心、评测区、话题广场（列表页）
6. 设置子页（先做网络、外观、关于，其余按需）

## 待补的 Rust 接口

开始每一项前，先在 `rust-core/src/coolapk/client.rs` 确认接口是否存在；不存在则按 `AGENTS.md` 的五步流程补齐。
