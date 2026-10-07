# AGENTS.md — mobile-x（Starlr Android 客户端）

Kotlin + Jetpack Compose（Material 3）论坛客户端，对接 x.182030.xyz 同源 API。

## 构建环境（本机固定路径）

```bash
export JAVA_HOME=/opt/jdk17
export ANDROID_HOME=/opt/android-sdk
GRADLE=/opt/gradle-8.9/bin/gradle   # 仓库里也有 ./gradlew（8.9）

cd mobile-x && ./gradlew :app:assembleRelease   # 产物 app/build/outputs/apk/release/
./gradlew :app:assembleDebug                    # 本地调试包（连 10.0.2.2:8787）
./gradlew :app:lint                             # 发布前跑一次
```

- 签名：`keystore/starlr-release.jks` + `keystore.properties`（**已提交**，仓库私有；密码别写进文档）。
- 版本：`app/build.gradle.kts` 的 `versionCode`（递增整数）/ `versionName`；当前 v0.1.1 (2)。

## API 约定

- Base URL：`BuildConfig.API_BASE_URL` — release `https://x.182030.xyz/`，debug `http://10.0.2.2:8787/`（模拟器回环）。
- Bearer 认证：`AuthInterceptor` 注入；401 由 `TokenAuthenticator` 单飞刷新重试；token 存 `TokenStore`（DataStore）。
- 请求模型：`data/ApiModels.kt`。**后端新增字段一律 `@JsonNames` 补充 + 可空默认值**，保持旧 APK 可用。
- 错误转换统一 `toApiError(Throwable)`，UI 层只消费 message（中文，来自后端）。

## 目录速览

```
app/src/main/java/com/starlr/app/
  data/        StarlrApi(Retrofit) StarlrRepository AuthInterceptor
               TokenAuthenticator TokenStore Serializers ApiModels
  ui/
    home/ explore/ detail/ compose(发帖) bookmarks notifications/
    profile/ login/ settings/ SessionViewModel.kt
  MainActivity.kt  StarlrApp.kt（导航 + 主题）
```

## 界面约定

- 文案全部中文；登录页含用户协议/隐私政策勾选（`legal.tsx` 内容同步）。
- 作者显示名：`authorNickname ?: authorName`（昵称可能为空，勿直接用）。
- 无障碍：所有 IconButton 带 contentDescription；触控目标 ≥ 48dp。
- 主题跟随系统（dark/light 都要检查）；列表用 LazyColumn + key。

## 发布流程

1. `versionCode +1`、`versionName` 按需升级；
2. `./gradlew :app:assembleRelease :app:lint`；
3. APK 复制到仓库 `apk/starlr-x-vX.Y.Z-release.apk`（覆盖旧文件，保持单 APK）；
4. 更新 `README.md` 下载段与版本说明；
5. `gh release create vX.Y.Z apk/starlr-x-vX.Y.Z-release.apk --title … --notes …`（repo：`wwwaaa123122/starlr-forum-android`，私有）；
6. 提交推送（**中文提交信息**，一行主题）。

## 已知坑

- Kotlin DSL 里读 properties：文件顶部 `import java.util.Properties`。
- Compose 预览/重组相关 import 容易漏（`remember`、`mutableStateOf`），以 IDE 报错为准逐个补。
- 后端 `/api/posts` 分页参数 `page`（从 1 开始）/`pageSize`；下拉刷新重置 page=1。
- 模拟器连本地后端必须用 10.0.2.2，写 localhost 不通。
