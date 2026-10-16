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
| 问答详情 | `/question/:id` | 🟡 | 已接入问题正文与回答列表（热门、高赞、最新）；入口为搜索结果的问答类型，但真机上搜索“问答”标签返回的都是标为“动态”的条目，问答页目前无法从界面打开，待找到能返回问答类型结果的入口后验证；接口未经真实样本验证 |
| 直播详情 | `/live/:id` | ⬜ | |
| 发布动态 | — | ✅ | 纯文本，媒体发布未做 |
| 图片查看 | `/pictures` | ✅ | `ImageViewerDialog` |
| 相册 | `/albums`、`/album/:id`、`/my-albums` | ⬜ | |
| 活动列表与详情 | `/events`、`/event/:id` | 🟡 | 已接入活动列表，入口在“我的”页，真机上标题正常、图片未加载；活动详情未做；接口未经真实样本验证 |
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
| 黑名单 | `/blacklist` | 🟡 | 已接入列表与移出（乐观更新，失败回滚并提示），入口在“我的”页；真机上验证了空态，移出操作因账号黑名单为空未验证；接口未经真实样本验证 |

## 我的

| 功能 | 参考路由 | Android 状态 | 备注 |
| --- | --- | --- | --- |
| 我的页（入口汇总） | `/me`、`/my` | ✅ | `MyHubScreen` |
| 资料编辑 | — | ✅ | `ProfileEditScreen` |
| 我的赞 | `/my-likes` | 🟡 | 复用用户主页的“赞过”标签，入口已接入；已在真机验证 |
| 我的评论 | `section=my_comments` | 🟡 | 复用用户主页的“回复”标签（酷安的回复列表即本人评论），入口已接入；已在真机验证 |
| 我的动态 | `section=my_feeds` | 🟡 | 复用用户主页的“动态”标签，入口已接入；入口在真机验证过（“我的”页菜单可见） |
| 浏览历史 | `/history` | 🟡 | 仅展示历史中的动态；用户、话题、应用等类型未解析。入口在“我的”页，真机显示空态，无法区分是账号历史无动态条目还是解析问题，待用有动态历史的账号验证；接口未经真实样本验证 |
| 我的常去 | `section=my_recent` | ⬜ | |
| 收藏与收藏单 | `/favorites`、`/collection/:id` | 🟡 | 已接入收藏的动态、收藏单列表与收藏单内容（只读）；创建、编辑、删除、移出内容的界面未做；真机截图待验证；接口未经真实账号验证 |
| 关注的论坛 / 话题 / 收藏单 / 问题 / 数码吧 | `/followed-nodes` 等 | 🟡 | 已接入关注的论坛与话题（同一页两个标签），入口在“我的”页；真机上话题列表的标题与简介正常，图标未加载（图片字段名待真实样本确认）；论坛列表在该账号为空；收藏单、关注的问题、数码吧未做 |
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

## 接口就绪度（核对于 `client.rs` 与 `ffi.rs`）

`client.rs` 中约 200 个方法，`ffi.rs` 只导出了约 80 个。下面是大多数待做功能的后端现状：

| 功能 | `client.rs` 已实现 | FFI 已导出 | 仍需 |
| --- | --- | --- | --- |
| 收藏 / 收藏单 | `get_favorite_list`、`get_collection_list`、`get_collection_detail`、`get_collection_item_list`、`create_collection`、`update_collection`、`delete_collection`、`follow_collection` 等 | 否 | FFI 导出、Gateway、解析器、界面 |
| 我的赞 / 我的评论 | `get_user_like_list`、`get_user_feeds`（已导出） | 部分 | 核对评论列表接口 |
| 浏览历史 | `get_recent_history` | 否 | FFI 导出、解析、界面 |
| 黑名单 | `get_black_list`、`add_to_black_list`、`remove_from_black_list` | 否 | 同上 |
| 关注的论坛 / 话题 / 问题 | `get_user_follow_nodes`、`get_followed_topics`、`follow_question`、`get_dyh_follow_list` 等 | 否 | 同上 |
| 隐藏的回复 / 回收站 | `get_hidden_replies`、`get_spam_feed_list` | 否 | 同上 |
| 问答详情 | `get_question_answers`、`create_answer` | 否 | 同上 |
| 直播详情 | `get_live_detail`、`follow_live` | 否 | 同上 |
| 相册 | `get_album_list`、`get_album_detail`、`create_album`、`get_user_album_list` 等 | 否 | 同上 |
| 活动 | `get_event_list`、`get_event_detail` | 否 | 同上 |
| 二手 | `get_secondhand_feeds`、`get_secondhand_brand_list`、`get_secondhand_product_list` | 否 | 同上 |
| 好物清单 / 排行 | `get_goods_list`、`get_goods_detail`、`get_goods_list_items`、`get_rank_feeds`、`get_goods_search_hot_words` 等 | 否 | 同上 |
| 应用中心 / 游戏中心 | `get_game_list`、`get_app_list`（已导出） | 部分 | 核对分类接口 |
| 节点（论坛） | `get_node_feeds` | 否 | 同上 |
| 公众号 | `get_dyh_list`、`get_dyh_feeds`、`get_dyh_detail` | 否 | 同上 |
| 评测区 | `get_board_feeds`、`get_editor_choice_feeds`、`get_digest_feeds` | 否 | 同上 |
| 热榜 / 快讯 | `get_headline_feeds`、`get_hot_feeds`（已导出） | 部分 | 核对 |
| 用户插件 | `get_user_plugins`、`save_user_plugins`、`claim_user_plugin` | 否 | 同上 |
| 设置相关 | `set_network_proxy`、`update_device_profile`、`get_update_list` 等 | 部分 | 同上 |

注意：`ffi.rs` 的名字与 `client.rs` 不一定一一对应（如 `get_user_feeds_typed`），导出前要逐个核对。

## 建议执行顺序（每项一次提交）

1. **收藏与收藏单**：导出收藏、收藏单相关 FFI，接入 Gateway 与解析器，做收藏页与收藏单详情。
2. **我的赞、我的评论、我的动态**：核对接口后接入，复用 `MyHubScreen` 入口。
3. **浏览历史、黑名单、关注的论坛 / 话题**：列表型页面，可共用一个分页列表组件。
4. **问答详情、直播详情、相册、活动**：独立详情页。
5. **二手、好物清单与排行**：导出对应接口，补全好物页的搜索、清单、排行标签。
6. **应用中心、游戏中心、评测区、话题广场**：列表页。
7. **设置子页**：先做网络、外观、关于，其余按需。

每项开始前，按 `AGENTS.md` 的五步流程处理接口：`client.rs` → `ffi.rs` 导出 → 重新生成绑定 → Gateway → 解析器与测试。
