小蚕去广告 v0.4

适配小蚕惠生活 3.21.2（versionCode 2985）。

本版将模块 Application ID 改为 io.github.xiguadoudou.xiaocan.noads，以满足 LSPosed 仓库申请的命名要求；广告拦截规则与 v0.3 相同。

用户于 2026-10-08 在自己的手机上实测 v0.3 并反馈运行正常。v0.4 已通过编译、签名和包信息核对，新包名 APK 尚待用户真机复测。

升级方式：安装 v0.4 后停用旧模块，在 LSPosed 启用新版，作用域只选择 com.realtech.xiaocan，强停小蚕再启动。新旧模块包名不同，不是覆盖安装；可以卸载旧模块，无需卸载小蚕。

已处理开屏、插屏、首页自动推广、指定原生广告容器及下拉二楼推广；详细范围和已知遗漏见 AUDIT.md。

SHA-256
b8c1a8f546b2fa7ad5eb9d6143385947d69b94b90ebd9d662975181f7ff6a26d  XiaoCan-NoAds-v0.4.apk
1be0745955226bd8d865400f9b0e6ed14ee4ffbae7e28260980d93e28534473b  XiaoCan-NoAds-v0.4-source.zip
