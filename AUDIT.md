# 小蚕惠生活 3.21.2 广告入口检查记录

本记录基于本次上传 APK 的静态分析，不是手机动态测试结果。没有连接用户手机或登录其账号，也没有对账号、订单、优惠券或奖励数据进行修改。

## 本轮确认并处理

| 区域 | 代码证据 | v0.3 处理 |
| --- | --- | --- |
| 首页自动会员推广 | HomeEffect.ShowActiveVipDialog → showActivateVipDialog | 过滤该自动事件 |
| 自动转盘推广 | ShowFullRefundWheelDialog | 过滤事件并完成 postLoginLotteryDecision(false)，不让登录后等待悬挂 |
| 自动免费抽奖引导 | ShowFreeLotteryDialog → showNewUserFreeDialog/navToLoginNewUserFree 或转盘 | 过滤自动事件，保留正常登录 |
| MA 活动推广 | ShowMAChallengeDialog → MaChallengeService.showDialog | 过滤自动事件，不禁用整个活动服务 |
| 红包即将过期推广 | ShowFullRefundSecRedPackExpiringDialog | 过滤自动事件，不改变红包状态 |
| 首页活动浮层 | renderHomeActivityBanner → onHomeActivityBannerClick | 将活动列表传为空，沿用应用的隐藏、轮播取消和延迟恢复清理分支 |
| 红包雨浮窗 | HomeFabExtKt 的首页底部状态更新 → layoutFab.rainFab | 原状态更新后隐藏 rainFab，仅影响此浮窗 |
| 挑战活动自动检查 | CompetitorChallengeServiceImpl.shouldCheckHomeActivityPop | 返回 false，不禁用规则查看、主动挑战或奖励请求 |
| BQT 原生广告 | BqtAdServiceImpl.loadAndShow → NaviteAndroidBqtAdHelper | 阻止加载，调用 onAdLoadedFailure |
| HX 原生广告 | HxAdServiceImpl.preload/loadAndShow/show → HxAdView | 阻止加载和显示，通知加载失败/返回 false |
| ToBid 原生广告容器 | TobidNativeAdServiceImpl.loadAndShowIntoContainer → TobidNativeAdUtils | 阻止加载，OnAdLoadResultListener.onResult(false) |

## 继续保留的规则

开屏共用 loadAd → requestExit；原生 SigMob 插屏；AM/ToBid Flutter 插屏；首页推广图片弹窗；商城首次引导/奖励推广弹窗；二楼可用性开关和二楼引导动画。

## 已检查但未直接禁用

- HomeFabExtKt 的同一浮窗区域包含 cvOrder 订单进度，以及联系客服、微信客服等功能。没有把整个 layoutFab 隐藏。
- MainActivity 首页可见性、未读消息、定位、权限及剪贴板口令检查具有业务功能；没有禁用 onResume 或通用路由。
- 首页首单/二单/三单完成提示与订单状态相关，没有作为纯广告统一屏蔽。
- GDT/BQT 的另一套 Feed 接口包含 Kotlin Flow/协程及交互绑定；本版未在尚未核实调用端退出行为的情况下直接返回 null。BQT 的传统原生容器入口已单独拦截。
- Vlion 广告豆、任务广告和主动激励视频涉及用户主动完成任务，本版未禁用或伪造奖励。
- 付款、登录、权限、订单详情和用户手动点击的红包/购物入口没有通用外部跳转封锁。

## 验证

新增九组 Hook 方法（emit 包含真实和桥接重载）的参数和返回类型已与目标 DEX 核对。首页五种推广事件类、this$0、layoutFab、rainFab 字段存在已核对。构建与 APK 签名验证通过，安装包沿用 v0.1/v0.2 的签名。

实际测试应包括：冷启动、后台返回、首页深拉、登录前后、刷新后/滚动后浮层是否复现，以及订单、客服、手动领券操作。若有广告遗漏、界面留白、跳转仍存在或闪退，需要手机日志与触发步骤继续定位。网页、Dart 内部的其他推广和服务器下发的新样式仍可能未覆盖。
