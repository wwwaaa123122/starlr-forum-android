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

- 仓库内置最新签名包：`apk/starlr-x-v0.1.22-release.apk`（约 2MB，minSdk 26 / targetSdk 35，已用 `keystore/` 密钥签名）
- v0.1.22：帖子详情页顶栏新增「分享」按钮（系统分享面板，标题+网页链接）
- v0.1.21：新增举报功能——帖子与回复可举报（原因单选：垃圾广告/人身攻击/色情低俗/违法信息/其他，可附补充说明），提交进入管理后台举报队列
- v0.1.20：新增编辑功能——帖子作者/版主可编辑帖子（标题+正文），回复作者可编辑自己的回复；被编辑过的帖子与回复显示「已修改」小字
- v0.1.19：详情页回复输入栏优化——定向回复显示可取消的「回复 @xxx」标签条；输入栏避开系统手势条
- v0.1.18：修复打开含图片文章闪退（正则捕获组越界）
- v0.1.17：Markdown 渲染支持图片（独立成行的 ![]() 显示为图片，相对路径自动拼接服务器地址）；发帖/回复插入图片改为相对路径，不再产生双斜杠 URL
- v0.1.16：登录失败提示统一为「用户名（邮箱）或密码错误」（App 端 401 兜底 + 后端文案同步）
- v0.1.15：修复会话过期后界面静默失败的矛盾态；个人主页/通知页显示具体错误信息与重试按钮；刷新失败记录详细日志
- v0.1.14：发帖/回复支持图片上传；设置页支持更换头像与主页背景图；回复作者行显示昵称（与 web 端一致）；站长称号仅 UID 1，其他管理员显示「管理员」
- v0.1.13：个人主页头像恢复圆形叠压样式
- v0.1.12：个人主页并行加载提速；背景图沉浸式头部、头像嵌入图下缘；设置页账号卡片同步为背景图预览
- v0.1.11：人机验证整段收进应用内（内嵌 WebView 完成确认并自动兑换会话），修复 Custom Tabs 下「人机校验失败」；顶部带返回可随时取消
- v0.1.10：登录/注册的网页在应用内打开（Custom Tabs，完成验证自动回应用）；新增启动静默更新检测，有新版本弹窗提示，可跳过
- v0.1.9：登录/注册密码只需在应用内输一遍——应用先本地校验凭据换取一次性预授权，跳浏览器仅完成人机验证即自动返回（无需在网页重输密码）；错误密码直接在应用内提示
- v0.1.8：修复登录页闪退（启动图标 adaptive icon XML 不被 painterResource 支持，改用站点 logo 位图资源）
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
