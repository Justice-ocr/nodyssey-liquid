# Liquid Reveal 验证记录

基于 `weishiair/nodyssey@29e60a79`，在 Windows / JDK 21 / Android SDK 37 / Chrome 上执行。结果按平台区分。

## Liquid 2 增量验证（2026-11-12）

- 首页、帖子详情、通知、个人页、设置页顶栏接入 `ThemeToggleButton`；复用既有液体揭示和设置仓库。
- `:ui:compileKotlinJvm`、Android 编译、`spotlessCheck`、优化签名 `:app:assembleRelease -Pnodyssey.liquidRelease=true`（含 vital lint）通过。
- 宿主针对性测试 **8 项，0 失败**：原有 7 项 + 新的顶栏双向切换测试，验证以当前应用明暗而非系统明暗决定方向，两次提交的值为 false / true，草稿不重建。另运行首页行为 23 项、架构导入边界 1 项、硬编码语言检查 1 项，合计 **33 项，0 失败**。
- 运行现有 `PostListScreenRenderTest` 的 light/dark 两项，检查 360dp 宽原生首页截图（[浅色](liquid-screenshots/android-home-light.png)、[深色](liquid-screenshots/android-home-dark.png)），按钮位于账号左侧，保留原有站点入口和下一行搜索栏。渲染 fixture 没有注入设置写入回调，因此图中按钮呈禁用色，仅用于布局检查，不代表真机操作验证。
- 新 APK：`nodyssey-v1.3.10.2.apk`，内部版本 `1.3.10.2 (149)`，包名和证书同 Liquid 1，可升级安装；非 debuggable、两种 ARM ABI。
- 大小：12,172,538 bytes；APK SHA-256：`aec0c1ce0b3c22161d1a8e9c26ade40ec5a19cbe24e0198ee074f60cae89daa7`；v2/v3 签名校验成功。
- 仍未连接 Android 真机或模拟器，不宣称已完成实机音质/视觉/性能验收。

以下是第一版完整验证与已知范围。

## 已通过

### Android / Kotlin

- `:ui:compileKotlinJvm`：成功，确保公共 UI 的 JVM 目标兼容。
- `:app:assembleDebug`：成功，仅用于编译验证，不作为最终安装包交付。
- `:app:assembleRelease -Pnodyssey.liquidRelease=true`：成功，R8 与资源压缩开启，`lintVitalRelease` 通过。
- `spotlessCheck`、`git diff --check`：通过。
- `:ui:testAndroidHostTest --tests '*LiquidThemeRevealTest' --tests '*SettingsScreenTest'`：**7 项，0 失败**（液体模块 2、设置页 2、主题页 3）。
- 原生测试覆盖：减少动态效果不启动流水、切换不重建草稿、提交一次、重复请求忽略、中断解锁且只提交一次；现有设置/主题页操作回归。旧请求清理以 generation 标记隔离，避免影响紧随的新请求。
- 本地首次运行缺 Robolectric 运行时，已下载官方 Maven 的 SDK 35 instrumented JAR，核对 SHA-512 后通过临时本地 init script 指定离线依赖。该机器路径、镜像配置和下载缓存都不提交仓库。

### 网页预览

- TypeScript 严格类型检查 + `vite build`：成功。
- Vitest：**6 项，0 失败**。进度分阶段、几何边界/完全覆盖、音频不可用、阶段只触发一次、连续音源中断断连、不误播收尾、零音量无音源。
- Playwright/Chrome：**6 项，0 失败**。390×844、820×1180、1440×1000 三种视口，双向完整动画；滚动后 sticky/fixed 的 X/Y/宽度误差小于 1px；无重复 ID；揭示层 inert/aria-hidden；草稿、筛选与滚动保留；主题重载恢复；重复点击；resize 与后台中断；减少动态效果；Canvas 降级；音效默认关闭与设置记忆；AudioContext 构造失败不阻断主题。
- 已检查手机与桌面动画中途截图，液柱/液滴与底部起伏水面均非空；图表和文字随裁剪区域改变主题。
- 原创 PCM 样本由 `liquid-preview/scripts/render-audio.mjs` 生成，分别为进入深色/浅色，2.9 秒，25% 音量。用于试听，不是外部采样音频。

## 独立发行安装包

- 文件：`nodyssey-v1.3.10-liquid.1.apk`（基于上游 1.3.10 / code 148，独立发行标记）。
- 包名：`io.github.nodyssey.liquid`，应用名称 `Nodyssey Liquid`。
- 非 debuggable；ABI：`arm64-v8a`、`armeabi-v7a`；minSdk 26 / targetSdk 36。
- 实际大小：12,172,414 bytes（约 11.6 MiB）。
- APK v2 / v3 签名校验通过，独立 RSA 3072 证书。
- 证书 SHA-256：`c6658a9bc6e302cb7e47e6e513413aa2ff0f20a3e7aad6f61a077a94f2811ce0`。
- 文件 SHA-256：`13163e35db9ac7757b644eaae214257843117e5709bc2f7967da8586290897e6`。
- 该证书**不是原作者证书**，只能独立安装或升级本 Fork。私钥留在本机仓库外，不发布。

## 尚未验证 / 已知范围

- 当前没有 Android 真机或模拟器连接，因此**没有做 Android 实机视觉、真实声卡试听、性能/内存峰值与不同厂商 ROM 测试**。Robolectric 是宿主测试，不能替代这些验证。
- 本次未运行全仓库数千条测试或完整 `lintDebug`；执行了上述针对性测试和 Release 的 vital lint。构建中已有 Coil opt-in、datetime 旧属性、FlowPreview 等上游警告，未顺带改动。
- iOS 在本次改动中直接切换、默认静音；Windows 无法执行 Xcode 构建或 iOS 测试。
- Android 外部 WebView/SurfaceView/TextureView 降级；网页 Canvas/WebGL/video/iframe/open Shadow DOM 降级；关闭的 Shadow DOM、portal 和外部主题结构需要调用方主动禁用动画。
- DOM 快照不重新运行 Canvas/WebGL，也不保证实时视频或异步外部内容两层同步。Android 旧主题底图与网页克隆在短动画期间可能冻结动态内容。
- 音频测试验证事件/节点生命周期，不代表人耳音质或具体设备响度验收。
