# 签名密钥（不入库，本地持有）

- `starlr-release.jks`：Starlr Android 发布签名密钥（RSA 2048，有效期 10000 天，别名 `starlr`）
- 口令在同目录 `keystore.properties`（构建脚本自动读取，`storePassword`/`keyPassword` 相同）
- ⚠️ 本目录与 `keystore.properties` 已从 git 历史移除并加入 `.gitignore`，仓库转公开后也不会入库
- 密钥丢失将无法覆盖安装升级（需卸载重装），请自行另行备份 `starlr-release.jks` 与口令

## 构建已签名 release 包

```bash
export JAVA_HOME=/opt/jdk17
export ANDROID_HOME=/opt/android-sdk
./gradlew :app:assembleRelease     # 产物：app/build/outputs/apk/release/app-release.apk
```

## 校验签名

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

> 密钥丢失将导致无法覆盖安装升级（需卸载重装）与无法上架更新，请另行备份 `.jks` 与口令。
