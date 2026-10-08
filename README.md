# 小蚕去广告 v0.4

[下载 v0.4 APK](https://github.com/xiguadoudou/XiaoCanNoAds/releases/download/4-0.4/XiaoCan-NoAds-v0.4.apk) · [发布页面](https://github.com/xiguadoudou/XiaoCanNoAds/releases/tag/4-0.4)

适配本次上传的 **小蚕惠生活 3.21.2 / versionCode 2985**，包名 `com.realtech.xiaocan`。
模块包名 `io.github.xiguadoudou.xiaocan.noads`，使用传统 Xposed API 82。

## v0.4 变更

- 使用与 GitHub 账号对应的 Application ID，便于申请 LSPosed 仓库收录。
- 拦截规则与 v0.3 相同。
- 这是新包名：安装后停用旧模块，再在 LSPosed 启用新版，仅勾选小蚕惠生活并强停重启。可卸载旧模块，无需卸载小蚕。

## v0.3 新增

- 按首页自动事件过滤会员开通、抽奖/免费抽奖、MA 活动及红包到期推广；保留其他事件、订单信息及手动打开功能。
- 关闭活动轮播浮层和红包雨浮窗，保留客服和订单进度浮窗。
- 禁用竞品挑战首页自动弹窗检查；保留主动挑战操作。
- 补充 BQT、HX、ToBid 原生广告容器加载拦截，回传加载失败/false。
- 继续保留此前开屏、插屏、首页推广图片、商城推广及下拉二楼规则。

调查记录见 AUDIT.md。

## 下拉二楼规则

关闭首页“继续下拉进二楼”的推广入口，避免深拉触发推广页面及其外部跳转。通过 HomeViewModel.canUseHomeSecondFloor() 返回 false，让应用选择原有 MaterialHeader 普通刷新头，同时跳过二楼引导动画。原有刷新监听器和首页数据刷新保留，不拦截手动点击美团红包等外部链接。该行为来自静态代码核对，实际效果仍需手机测试。

此版本与本次提供的 v0.1、v0.2 使用相同签名，可以直接覆盖安装。安装后强停小蚕，再进入首页测试。

## 安装

1. 在手机上安装 `XiaoCan-NoAds-v0.4.apk`。这是普通模块 APK，不是刷机 ZIP，不需要替换原软件。
2. 打开 LSPosed → 模块 → **小蚕去广告**，开启模块。
3. 作用域只勾选 **小蚕惠生活（com.realtech.xiaocan）**。
4. 强行停止小蚕惠生活并重新启动。若未生效，重启手机再测试。

模块没有桌面图标或设置界面，在 LSPosed 中管理。需要已经正常工作的 LSPosed 和 root 环境。

## 原有规则（v0.3 同时包含上述新增规则）

| 项目 | 处理入口 |
| --- | --- |
| 下拉二楼推广和自动引导 | `HomeViewModel.canUseHomeSecondFloor()`、`HomeSecondFloorKt.checkSceondDFloorGuide()` |
| 冷启动和共用开屏流程 | `BaseAdActivity.loadAd()` → 应用原有 `requestExit()` |
| 原生插屏弹窗 | `SigMobAdServiceImpl.loadInterstitialAd()`、`SigMobUtils.load()` |
| 原生插屏预加载及兜底展示入口 | `SigMobUtils.preload()`、`showInterstitialHost()` |
| Flutter AM 插屏 | `AmInterstitialAdHandler.onMethodCall()` 的 load/show |
| Flutter ToBid 插屏 | `InterstitialAd.load()`、`showAd()`、`isReady()` |
| 首页推广图片弹窗 | `HomeFragment.showSharerHomePopup()` |
| 抖音商城首次推广引导 | `SecondTabPlacementFragment.enqueueFirstGuideDialogIfNeeded()` |
| 抖音商城奖励推广提示 | `SecondTabPlacementFragment.enqueueBonusDialogIfNeeded()` |

加固 APK 的业务 DEX 已静态提取并检查这些入口。监听指定业务类加载，同时在 Application.attach 后补装 Hook，适应加固后的类加载时机。开屏沿用原应用的退出、生命周期和启动预加载清理逻辑，不直接改写欢迎页面或隐私同意状态。推广弹窗在进入显示队列前阻止。

本版不全面禁用 DialogUtils，仅过滤已核实的首页推广事件。本版不拦截通用 Dialog，不改登录、付款、权限、验证码弹窗。不拦截主动观看的激励视频，不伪造奖励回调。插屏通过失败事件通知调用方。

## 验证状态与范围

- 已通过 Java 编译、DEX 生成、APK 打包和 v2 签名验证。
- 已静态核对所选类、方法参数数量、返回类型及反射字段存在。
- 用户已于 2026-10-08 在自己的手机上实测 v0.3 并反馈运行正常。v0.4 仅调整模块 Application ID 和版本信息，广告拦截规则不变；新包名 APK 尚待真机复测，其他设备与应用版本尚未验证。
- 网页/H5/Flutter Dart 内自建的其他营销弹窗、服务器更换的推广样式，以及其他版本可能需要新增规则。更新小蚕软件后需重新核对。

## 你在手机上检查

测试冷启动、退到后台再返回、首页及商城入口；检查是否仍有广告，能否正常进入主页。无需卸载小蚕、无需清除它的数据。

若仍有广告：截图或录屏保留触发步骤；在 LSPosed 中导出模块日志，寻找 `[XiaoCanNoAds]`。`loaded v0.4` 表示进入了模块入口，`hooked ...` 表示安装了对应 Hook，`blocked ...` 表示触发了拦截。若只有 loaded 没有业务类 hooked，需结合日志调整加固加载时机。日志可能包含设备及应用信息，分享前可删去无关个人信息。

若出现闪退、启动卡住或功能异常：关闭 **小蚕去广告**，强停小蚕后再启动，必要时重启。模块关闭后不再安装这些 Hook。

## 源码编译

方式一：Android Studio 打开此目录，使用 JDK 17、Android SDK 35 构建 app 的 debug APK。传统 Xposed API 是 compileOnly，不会打包进成品。AGP 8.6.1；可使用匹配的 Gradle 8.7。

方式二：Linux x86_64 上，安装 Python 3、JRE 17 和 keytool，运行：

```bash
python3 build.py
```

此脚本自动下载并校验固定版本编译依赖，无需完整 Android SDK。输出 `app/build/XiaoCan-NoAds-v0.4.apk`，保留 `app/build/module.jks` 可继续使用同一签名。重新生成签名后，需要先卸载旧模块，再安装新模块；不需要卸载小蚕。

上传目标 APK SHA-256：

```text
831b388c107c2671d242b65edee14cfd6bbd097efb9b29c68aaf4cabfb7d1d05
```

源码不包含原应用 APK、反编译代码或编译依赖。成品仅包含模块自己的 Hook 代码。
