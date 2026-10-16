# AGENTS.md — mobile-x（Starlr Android 客户端）

Kotlin + Jetpack Compose（Material 3）论坛客户端，release 直连纯 API `https://i.182030.xyz/`。

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
- 版本：`app/build.gradle.kts` 的 `versionCode`（递增整数）/ `versionName`；当前 v0.1.10 (11)。

## API 约定

- Base URL：`BuildConfig.API_BASE_URL` — release `https://i.182030.xyz/`（纯 API Worker，头像等相对路径自动同域），debug `http://10.0.2.2:8787/`（模拟器回环）。运行时可在设置页覆盖（`ServerConfig`，OkHttp 拦截器按当前地址重写请求）。
- 人机验证：生产登录/注册走**浏览器 SSO + 凭据预交接（preauth）**——App 先调 `app-login/prepare` 在应用内校验账号密码换一次性 preauth（不发会话，复用登录 IP+单账号双重限流），再跳系统浏览器打开 `https://x.182030.xyz/app-auth?preauth=…`（frontend-x/public/app-auth.html），网页只做 Turnstile 人机验证（**无需重输密码**），后端 `app-login/confirm` 消费 preauth 签发一次性 ticket（120s、单次、哈希存储、绑定 origin+challenge），经 `starlr://auth?ticket=&challenge=` 深链回跳，App 调 `site-login/redeem` 由**当前服务器自签** JWT。浏览器在**应用内打开**（AndroidX Custom Tabs，Chrome 内核保证 Turnstile 可用，失败兜底系统浏览器；勿用裸 WebView——0.1.3–0.1.6 的验证闪退即源于此）。旧后端无 prepare（404）时 App 自动退回网页表单登录；注册的建号与验证邮件在 confirm 步完成（KV 不存明文密码）。`/api/config` 无 siteKey（本地 dev）时仍走表单直登。改动桥接页后需重新构建部署 frontend-x。
- Bearer 认证：`AuthInterceptor` 注入；401 由 `TokenAuthenticator` 单飞刷新重试；token 存 `TokenStore`（DataStore）。
- 请求模型：`data/ApiModels.kt`。**后端新增字段一律 `@JsonNames` 补充 + 可空默认值**，保持旧 APK 可用。
- 错误转换统一 `toApiError(Throwable)`（区分 DNS/超时/SSL/连接拒绝/HTTP 状态并写 logcat），UI 层只消费 message（中文，来自后端）。

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

## 更新检测

- 后端 `GET /api/app/latest`（backend/src/routes/app-update.ts）读 KV `config:app`：
  `{"versionCode":11,"versionName":"0.1.10","hash":"<git短哈希>","notes":"更新说明","url":"下载页地址"}`
- App 每次启动静默检测（接口异常静默跳过）；`versionCode` 大于本地且不等于已跳过版本时弹窗：「更新」打开 url，「跳过此版本」写入 SharedPreferences（`TokenStore.skippedUpdateVersion`）不再提醒该版本。
- 发新版本后更新 KV（改配置无需重新部署）：
  `npx wrangler kv key put "config:app" '<json>' --remote --namespace-id=10623b6e6ae9438299ff5b86a476333c`
- 弹窗标题与说明文字全部来自接口（versionName/notes），应用端不硬编码说明性文案。

## 已知坑

- Kotlin DSL 里读 properties：文件顶部 `import java.util.Properties`。
- Compose 预览/重组相关 import 容易漏（`remember`、`mutableStateOf`），以 IDE 报错为准逐个补。
- 后端 `/api/posts` 分页参数 `page`（从 1 开始）/`pageSize`；下拉刷新重置 page=1。
- 模拟器连本地后端必须用 10.0.2.2，写 localhost 不通。
