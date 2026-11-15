# 交互接入 · 教程索引

交互接口负责触控接管、玻璃融合和返回历史。作用域通过 Compose 树向子组件提供策略或宿主接口，不负责保存应用设置。

## 推荐接入顺序

1. 用 CMS.Appearance.Theme 提供材质、主题和触觉。
2. 完整分页页面使用 CMS.Content.Pager，内部直接放 Page 或一个主 ScrollView；库管理每页滚动桥和统一采样。
3. 用 CMS.Navigation.CapsuleNavigation 切换页面内分类，并绑定同一 PagerState 的真实 pageProgress。
4. 默认保持 respectConsumedGestures = false。表格、长代码使用 HorizontalScrollView 或 cmsHorizontalScrollTouchPriority 自动登记区域；滑杆、选区手柄等专用区域使用 canStart 排除。已有复杂原生 Pager 才使用底层 Modifier 手动适配。
5. 为需要融合的普通玻璃提供 CMS.Interaction.GlassFusion 独立作用域，按距离融合时设 proximityOnly = true。
6. 用 CMS.Interaction.BackHistory 接入真实页面栈；在页面外壳同步标题，让长按返回能够跨级选择。
7. 在设备上验收点击、动画中接管、横竖换向、多指取消、返回选择与材质降级。

## 接口选择

| 能力 | 典型场景 | 关注点 | 文档 |
| --- | --- | --- | --- |
| 触控优先级 | 双页全屏、宽表格、长代码与原生玻璃 | 连续换向、带符号抖动判断、局部横滑与页面纵滑交接 | [接入教程](TouchPriority.md) |
| 玻璃融合 | 两块普通玻璃，点按分开或靠近 | 距离融合、独立融合组、各自点击范围 | [接入教程](GlassFusion.md) |
| 返回历史 | 连续进入多页，长按返回后滑动选择 | 真实访问栈、跨级返回、取消与标题同步 | [接入教程](BackHistory.md) |

胶囊导航、标签导航和返回按钮属于导航组件，文档分别见 [CapsuleNavigation](CapsuleNavigation.md)、[TabNavigation](TabNavigation.md) 与 [BackButton](BackButton.md)。返回按钮通过 BackHistory 获取宿主历史。

## 通用接入边界

| 情况 | 处理方式 |
| --- | --- |
| 新建两页或多页页面 | 推荐 [CMS Pager + Page](Page.md) |
| 已有纵向页面外壳 | Pager 内使用一个主 ScrollView，自动继承桥 |
| 保留原生 Pager 的复杂配置 | 用 cmsPagerTouchPriority，原生 userScrollEnabled = false |
| 抬手后改方向 | 创建新手势会话；allowAxisChange 只控制按住时换向 |
| 专用拖动控件 | 排除触控区域，不靠提高方向权重解决 |
| 表格 / 代码横滑与页面冲突 | HorizontalScrollView 或 cmsHorizontalScrollTouchPriority，共享区域登记与纵向桥 |
| 原生玻璃阻断纵滑 | 使用 CMS Pager / ScrollView 的统一方向接管 |
| 自定义玻璃工具栏 | Source 只采样背景，融合控件放在兄弟层 |
| 非光学材质 / 旧系统 | 保留普通玻璃与点击，视觉按材质能力降级 |
| 多级页面 | 共用真实历史；previous 不包含当前页，returnTo 关闭中间页面 |

## 组合使用

分页导航将 `selectedIndex` 和 `pageProgress` 绑定同一个 PagerState；内容滚动与专用拖动区域共用 TouchPriority 作用域。

玻璃融合组独立于页面选择与返回历史。同一窗口中多个工具栏应使用独立 GlassFusion 作用域，采样源只包含背景内容，玻璃控件放在兄弟层。搜索与取消按钮的宽度转移可使用 GlassSearch，详见[玻璃融合](GlassFusion.md)。

返回历史从真实导航栈生成；标题更新、跨级返回与系统返回应同步修改同一份宿主状态，详见[返回历史](BackHistory.md)。
