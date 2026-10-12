# Starlr Android（mobile-x）

> 星辰旅人论坛的 Kotlin 手机客户端 —— Jetpack Compose + Material 3，还原 Starlr X 的时间线体验。

## 功能

| 模块 | 说明 |
| --- | --- |
| 首页 | 最新/热门双 Tab · 下拉刷新 · 触底自动加载 · 乐观点赞/收藏 |
| 探索 | 防抖搜索（帖子+用户）· 热门标签「正在闪耀」· #标签过滤 |
| 通知 | 未读徽标 · 单条/全部已读 · 类型着色（回复/提及/收藏/点赞） |
| 我 | 渐变横幅 + 头像 + UID/加入时间/帖子/回复统计 · 帖子与回复 Tab |
| 帖子详情 | Markdown 轻量渲染（Compose 原生，无 WebView）· 回复树（楼中楼缩进）· 浏览计数 · 回复输入栏 |
| 发帖 | 标题字数 120 · 板块分类下拉 · 标签 ≤5 · Markdown 正文 |
| 登录/注册 | 用户名/邮箱 + 密码 · 注册成功自动登录 |
| 设置 | 昵称/简介 · 修改密码 · 退出登录 |

认证：`AuthInterceptor` 注入 Bearer；401 时 `TokenAuthenticator` 用 refreshToken 换新并重试一次（TOKEN_REPLAY/失败自动清会话回到游客态）。

## 技术栈

- Kotlin 2.0 + Jetpack Compose（BOM）+ Material 3 + Navigation Compose
- Retrofit 2.11（kotlinx-serialization 转换器）+ OkHttp 4.12
- Coil（头像异步加载，首字母渐变回退）
- 无 Hilt/Koin —— `AppContainer` 手动依赖注入；每屏独立 ViewModel（`viewModelFactory`）

## 目录

```
mobile-x/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml          # 版本目录
└── app/
    ├── build.gradle.kts               # debug 基址 http://10.0.2.2:8787/ ，release 线上域名
    └── src/main/
        ├── AndroidManifest.xml        # 网络权限；明文仅允许 10.0.2.2/localhost
        ├── res/                       # 主题/文案/网络安全配置/启动图标（矢量星形）
        └── java/com/starlr/app/
            ├── StarlrApplication.kt   # AppContainer
            ├── MainActivity.kt
            ├── data/                  # API 模型、Retrofit 接口、TokenStore、拦截器/刷新、仓库
            └── ui/
                ├── AppNav.kt          # 底部四标签 + FAB + NavHost
                ├── theme/             # X 官方色板（暗色默认/亮色跟随系统）
                ├── components/        # PostCard、Avatar、ActionCount、MarkdownText、格式化
                └── screens/           # home/explore/notifications/profile/detail/compose/login/settings
```

## 运行

1. Android Studio（Ladybug+）打开 `mobile-x/`，等待 Gradle Sync（AGP 8.5 / compileSdk 35）。
2. 启动本地后端：仓库根目录 `pnpm --filter backend dev`（Worker @ :8787）。
3. 模拟器运行 app —— 已配置访问宿主机 `http://10.0.2.2:8787/`（明文白名单仅限本机调试）。
4. 连接真机/发布：release 基址为 `https://i.182030.xyz/`（纯 API Worker 直连，见 `app/build.gradle.kts`）。

## 下载安装

- 仓库内置最新签名包：`apk/starlr-x-v0.1.7-release.apk`（约 2MB，minSdk 26 / targetSdk 35，已用 `keystore/` 密钥签名）
- v0.1.7：登录/注册改为跳系统浏览器完成人机验证后带一次性票据回跳（SSO），不再内嵌 WebView，彻底解决验证环节闪退
- 安装：传输到手机直接安装（需允许“安装未知来源应用”）
- 升级：同签名直接覆盖安装即可，无需卸载

## 签名与发布

- 发布签名密钥：`keystore/starlr-release.jks`，口令在 `keystore.properties`（随本私密仓库托管，见 `keystore/README.md`）
- 已在 `app/build.gradle.kts` 配置 `signingConfigs.release`，直接构建即签名：

```bash
./gradlew :app:assembleRelease        # app/build/outputs/apk/release/app-release.apk
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

- 仓库地址：git@github.com:wwwaaa123122/starlr-forum-android.git（**私密**，含签名密钥）；密钥丢失将无法覆盖安装升级，请自行另行备份。

## 与 Web 端（frontend-x）的一致性

- 同一套 X 官方语义色（X 蓝 #1D9BF0 / 点赞粉 #F91880 / 成功绿 #00BA7C / 纯黑底 #000000）
- 同一套交互约定：pageSize ≤ 50、乐观互动回滚、401 单飞刷新、浏览量 KV 去重
- 文案一致：「正在闪耀」「板块」「点亮」「星旅者」
