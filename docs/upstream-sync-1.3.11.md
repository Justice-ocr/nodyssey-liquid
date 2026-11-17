# Upstream 1.3.11 sync / Liquid 1.3.11.1

同步源：`weishiair/nodyssey` 的稳定 `main`，提交 `35bd717d434abcf3346f139cb9ebd6ff581488c7`（含 1.3.11 后的 iOS 启动兼容修复）。没有合入上游 `dev` 的未发布改动。

## 用户可见更新

- 可选玻璃主题，默认关闭；Android 使用 Compose backdrop，iOS 使用上游对应平台实现。
- 邮箱默认汇总全部邮箱的邮件，显示收件地址与已读状态。
- 设置增加首页置顶帖开关，控制首页和版块，不影响搜索结果。
- 用户名/UID 复制、邮箱验证授权、代理/VPN 下的加密 DNS 行为等上游修复。
- 同步上游 iOS 兼容改动的源码；本次仍只交付 Android，没有在 Windows 上验证 iOS。

## 保留的 Fork 功能

- 液体主题揭示、可选本地音效及音量记忆。
- 首页、帖子详情、通知、个人页和设置页顶栏太阳/月亮按钮。
- 墨水屏和减少动态效果降级；设置页明暗分段按钮。
- 独立包名 `io.github.nodyssey.liquid`、专用签名和本 Fork 的更新源，不自动迁移原版账号。

## 合并处理

- 根部 `PlazaTheme` 增加上游的 `frostedGlass` 参数，继续置于既有 `LiquidThemeReveal` 内。
- 帖子顶栏采用上游新玻璃结构，在 Android Compose actions 中保留 `ThemeToggleButton`。
- 设置仓库同时保留上游的玻璃/置顶偏好和 Fork 的液体/音效偏好。
- 版本 `1.3.11.1` / code `166`，高于之前 Liquid 2 的 `1.3.10.2` / code `149`；安装身份保持一致。

## 当前上游同步验证（2026-11-17）

- 上游稳定 `main@35bd717d434abcf3346f139cb9ebd6ff581488c7`，版本 1.3.11；合并三处冲突后保留 Liquid 定制。
- `:ui:compileKotlinJvm`、Android 主源码编译、`spotlessCheck` 通过。
- Liquid/主题/设置/首页/帖子详情/个人页及架构边界针对性宿主测试：**117 项，0 失败**。
- 独立对照工作树（原样上游 `main@35bd717d`）与同步后工作树运行同一 Windows/JDK/Robolectric 测试，均复现 **21 项 Room MigrationTest 失败、11 项 DataStore 文件重命名失败**。错误为 Windows 临时目录路径被 SQLite 驱动拒绝，以及 DataStore 临时文件 `Unable to rename ...preferences_pb.tmp`。不是 Liquid 改动引入；上游源码的对照结果一致。
- 完整测试任务报告：2,487 通过、34 失败、253 跳过；另 27 项上游发布脚本测试通过。失败数与基线分类相符。
- `:app:lintDebug` 首次被机器的未跟踪 `local.properties` 路径格式挡住；修正本机未跟踪属性、不提交 SDK 路径后，`:app:lintDebug --rerun-tasks` 成功。
- `:app:assembleDebug`、`:app:bundleRelease`、独立签名 `:app:assembleRelease -Pnodyssey.liquidRelease=true` 通过，Release `lintVital` 通过。Gradle 全任务客户端在上游测试任务返回失败后断开，完整 aggregate 命令非零；以上独立任务结果分别记录。
- 新 APK：`nodyssey-v1.3.11.1.apk`，code 166，size 12,236,230 bytes；SHA-256 `ab2aea65291c055576dbf0bb130a8ae26c150014e5f09df6075fa433c351261a`。包名 `io.github.nodyssey.liquid`，同 Liquid 2 的 RSA 3072 证书；APK v2/v3 验签通过，ABI `arm64-v8a` / `armeabi-v7a`。
- 资源名称重复检查通过，Room schemas 没有生成差异。
- `Stale PRs` 定时工作流已在 GitHub 禁用；网页预览及其自动构建已删除。GitHub Pages 仅部署 `updates-feed/` 中的 stable/dev/changelog JSON，手动运行 `Publish update feed` 工作流即可发布更新清单。
- 无 Android 真机/模拟器，尚无实机视觉、真实声卡或设备性能测试；iOS 同步源码未在 Windows 上构建。
