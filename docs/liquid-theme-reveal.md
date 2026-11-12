# Liquid Theme Reveal — Nodyssey 分支

本改动基于 `weishiair/nodyssey` 的 `29e60a79`，保留 Kotlin Multiplatform / Compose、原有导航、设置仓库和业务状态。**并未把 Android 项目重写成网页。**

- Android：首页账号左侧新增太阳/月亮按钮，帖子详情、通知、个人页、设置页顶栏也可一键切换。点击从该按钮底缘启动同一液体动画；沿用设置仓库记住深浅偏好，墨水屏模式下禁用。`设置 → 明暗` 原有分段按钮继续保留，下方为动画、音效、音量与短试听。音效默认关闭。
- 网页预览：[`liquid-preview/`](../liquid-preview/README.md)，React + TypeScript + Vite + CSS + 动态 SVG + Web Audio，独立演示，不连接论坛 API。在线入口：https://justice-ocr.github.io/nodyssey-liquid/ 。
- 短试听：[进入深色](../liquid-preview/public/audio/into-dark.wav)、[进入浅色](../liquid-preview/public/audio/into-light.wav)，默认不自动播放。
- 独立发行包：经用户确认，`io.github.nodyssey.liquid` / **Nodyssey Liquid**；不覆盖原作者版本、不共享其签名、不自动迁移其登录态。

## Android 本地运行

要求沿用项目的 JDK 21、Android SDK 37、Gradle wrapper。Android Studio 打开仓库即可：

```sh
./gradlew :app:assembleDebug
./gradlew :ui:testAndroidHostTest --tests '*LiquidThemeRevealTest'
./gradlew :ui:compileKotlinJvm
./gradlew spotlessCheck
```

优化独立发行版（两种 ARM ABI，R8/resource shrinking，不含 Debug 工具）：

```sh
# 签名设置全部通过环境变量传入，私钥不进仓库。
# NODYSSEY_KEYSTORE_FILE / NODYSSEY_KEYSTORE_PASSWORD
# NODYSSEY_KEY_ALIAS / NODYSSEY_KEY_PASSWORD
./gradlew :app:assembleRelease -Pnodyssey.liquidRelease=true
```

没有签名环境时，Release 产物是 **unsigned**，不能把它当作可安装交付。不要使用原分支的 `-Pnodyssey.echRelease=true` 冒充可覆盖原版的版本。当前 Fork 的更新源已经转为本 Fork，避免安装到签名不同的原作者构建。

当前独立发行文件名为 `nodyssey-v1.3.10.2.apk`，内部版本 `1.3.10.2 (149)`；第四段为 Liquid 独立修订号，高于第一版的 `1.3.10 (148)`，可以原位升级。`liquid-preview/public/{stable,dev,changelog}.json` 与预览一起由 Pages workflow 部署，且只指向本 Fork APK。后续发行须一起更新这三个文件的版本、大小和 SHA-256，不能直接提供上游不同签名的构建。

## 原生实现

模块：`ui/src/commonMain/kotlin/io/github/nodyssey/ui/theme/liquid/`

- `LiquidThemeReveal`：受控 `darkTheme`，可配置时长、启用/减弱动效、音效适配器及音量。
- `LiquidRevealController.request(target, sourceInRoot, onThemeChange)`：起点取实际按钮底缘；目标主题完成或中断时仅提交一次。重复请求不排队。
- `ThemeToggleButton`：可放入任意原生顶栏 action slot；读取 `LocalPlazaDarkTheme` 的实际明暗（含跟随系统的结果），通过根部提供的 `LocalQuickThemeChange` 写入 `ThemeMode.DARK/LIGHT`，无须每个页面再注入设置 ViewModel。按钮含三语言无障碍名称和长按提示，动画期间禁用。
- `LiquidGeometry`：复用单一非零绕组 `Path`，液柱/液滴/水花/波面同向子轮廓，重叠区域是联合而不是挖洞。
- `LiquidFrame`：同一真实帧进度驱动绘制、落水和流水阶段，没有定时器堆叠音效。
- `LiquidAudio`：可自行实现；Android 使用 `AudioTrack` 播放本地生成 PCM；设备异常被隔离，不阻断切换。没有现成音效系统需要复用。
- `SettingsRepository`：复用现有 DataStore，存储动画开关、音效开关、音量；启动仍沿用 shell 的首屏设置恢复。

```kotlin
LiquidThemeReveal(
    darkTheme = storedDark,
    reducedMotion = reducedMotion,
    durationMillis = 2600,
    soundEnabled = soundEnabled,
    volume = volume,
    audio = customAudioOrNull,
) { renderedDark ->
    PlazaTheme(darkTheme = renderedDark) {
        val reveal = LocalLiquidReveal.current!!
        // 在实际按钮的 onClick 中：
        // reveal.request(!storedDark, buttonBottomInRoot) { persistNewTheme() }
        ExistingScreen()
    }
}
```

### 两层与状态

Compose 没有 CSS/SVG DOM，采用其原生等价物：**同一页面的旧主题 `ImageBitmap` 位于底层，真正重绘的新主题页面经 `clipPath(Path)` 揭示**。这不是用旧截图自动生成新主题。旧图在变更颜色前捕获；新主题由原有 `PlazaTheme` 在同一 composition 中真正重绘。

不复制 ViewModel、导航、事件、语义树或表单。每帧只读取绘制阶段进度并更新路径，不逐帧重组组件。手势/键盘输入在约 2.6 秒捕获区间被冻结，防止新旧层滚动错位；窗口尺寸变化、生命周期暂停、卸载均取消动画、停止音频并提交目标主题。临时本地主题在持久化反馈后清除；受控调用方拒绝提交时会回到其受控值。

### 范围与限制（重要）

- Android 原生 Compose 卡片、文本、边框、矢量/Canvas 绘制走正常主题重新绘制。位图图片不被自动“重新上色”。
- `SurfaceView`、`TextureView`、`WebView` 等外部绘制表面检测到可见时直接切换，防止截图内容丢失。IME、系统栏、Popup/独立窗口不属于主 Compose 层；不宣称覆盖系统 UI。
- 视频、实时图表、异步图片等动态内容在旧主题底图中冻结约 2.6 秒。该效果面向主题控件所在的普通页面，不是任意外部渲染器的实时重主题引擎。
- iOS 保持可编译接口，**目前直接切换且无合成音效**；此次重点交付 Android。Windows 上无法验证 iOS/Xcode 构建。
- JVM 音效适配器静音，便于现有桌面编译；可自行传入音效实现。
- 音量修改/静音/后台/卸载停止所有音源；中断不播放完成音。减少动态效果只允许短反馈。新请求只在用户按钮操作中创建音源。
- 未提供原作者签名，不可覆盖升级原版；新签名位于维护机器的仓库外目录。

## 验证

详见 [验证记录](liquid-validation.md)。网页和原生结果分开记录，不以网页测试替代真机测试。当前机器没有连接 Android 设备/模拟器，因此不声称做过 Android 真机试听、帧率或机型兼容性测试。
