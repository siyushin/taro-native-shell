# AGENTS.md

> 本文件供 AI 编程代理阅读，介绍本仓库的架构、构建、测试与开发约定。

## 项目概述

本仓库是 **Taro 原生 React Native 壳工程**（App 名称 `taroDemo`），基于 React Native 0.73 裸工程（bare workflow），集成了 Expo Modules 以及 Taro RN 端运行时所需的全部原生依赖库。它本身**不包含业务代码**：业务 JS 代码在独立的 Taro JS 工程中开发，通过分离模式编译出 jsbundle 后输出到本壳工程运行。

参考文档：[Taro React Native 端开发流程 - 分离模式](https://docs.taro.zone/docs/react-native#%E5%88%86%E7%A6%BB%E6%A8%A1%E5%BC%8F)

与普通 `react-native init` 工程的差异（详见 `README.md`）：

0. 集成了 Expo（bare workflow，`use_expo_modules!` / `useExpoModules()`）
1. 集成了 Taro 依赖的所有原生库（依赖明细见 `README.md` 附录）
2. debug 方式变更：在 JS 工程中 `pnpm dev:rn` 启动 metro server
3. release 方式变更：在 JS 工程中 `pnpm build:rn` 打包 jsbundle，并通过配置 `rn.output` 输出到壳工程

## 技术栈

- **语言**：TypeScript（`typescript@5.0.4`）、JavaScript、Java/Kotlin（Android）、Objective-C（iOS）、Ruby（fastlane/CocoaPods）
- **运行时**：React Native `^0.73.1` + React 18，Hermes 引擎（`android/gradle.properties` 中 `hermesEnabled=true`），新架构关闭（`newArchEnabled=false`）
- **Expo**：`~50.0.0`（bare workflow），含 expo-av、expo-camera、expo-file-system、expo-location、expo-sensors 等
- **Node**：`>=18`（CI 使用 Node 20）
- **包管理器**：**pnpm**（CI 使用 pnpm 8；仓库同时存在 `package-lock.json` 与 `pnpm-lock.yaml`，以 pnpm 为准）
- **Android**：compileSdk/targetSdk 34，minSdk 21，Kotlin 1.8.0，JDK 17，`applicationId` 由 `app_id` 属性注入（默认 `com.tarodemo`）
- **iOS**：CocoaPods `~> 1.13`，Xcode workspace `ios/taroDemo.xcworkspace`（务必打开 workspace 而非 project）

## 代码结构

业务代码极少，壳工程的结构如下：

```
├── index.js                  # 入口：AppRegistry.registerComponent(appName, () => App)
├── App.tsx                   # 示例页面（react-native init 模板，实际运行时会被 Taro 打包的 bundle 替代）
├── app.json                  # App 名称（"taroDemo"）
├── __tests__/App.test.tsx    # 唯一的 Jest 测试（渲染冒烟测试）
├── metro.config.js           # Metro 配置（@react-native/metro-config 默认配置，无定制）
├── babel.config.js           # Babel 配置（@react-native/babel-preset）
├── jest.config.js            # Jest 配置（react-native preset）
├── android/                  # Android 原生工程（Gradle，含 fastlane）
├── ios/                      # iOS 原生工程（Xcode + CocoaPods，含 fastlane）
└── .github/
    ├── workflows/            # 4 个打包工作流（android/ios × debug/release）
    └── scripts/              # iOS 签名证书与描述文件导入脚本
```

注意：壳工程中**不要**在 `App.tsx` 里堆业务代码；Taro API 的原生能力由 `package.json` 中的依赖提供（如 `@react-native-async-storage/async-storage`、`expo-*`、`react-native-webview` 等），裁剪依赖时可参考 `README.md` 附录的 Taro 原生依赖明细表。

## 常用命令

```bash
pnpm install              # 安装 JS 依赖
pnpm upgradePeerdeps      # 安装/升级 Taro RN 三件套（taro-rn、components-rn、router-rn）的 peerDependencies，并执行 pod-install
pnpm start                # 启动 Metro（注意：正常 Taro 流程中 metro 由 JS 工程的 pnpm dev:rn 启动）
pnpm android              # react-native run-android
pnpm ios                  # react-native run-ios --no-packager --simulator "iPhone 16"（不启动 Metro，复用 JS 工程 8081 端口的 Metro；模拟器构建为 x86_64/Rosetta，只能选支持 Rosetta 的机型/系统，如 iOS 18.x 的 iPhone 16）
pnpm lint                 # ESLint 全量检查
pnpm test                 # Jest 测试
cd ios && pod install     # 安装 iOS Pods（或 pnpm upgradePeerdeps 自动执行）
```

### 打包（fastlane）

- Android：`cd android && bundle install && bundle exec fastlane assemble`，通过环境变量注入 `APP_ID`、`APP_NAME`、`BUILD_TYPE`、`VERSION_CODE`、`VERSION_NAME` 及签名信息（`KEYSTORE_FILE` 等）。lane 会修改 `strings.xml` 的 `app_name` 并调用 `gradle assemble<BUILD_TYPE>`。
- iOS：`cd ios && bundle install && bundle exec fastlane build_dev`（development 导出）或 `bundle exec fastlane build_release`（App Store 导出），通过 `FL_*` 环境变量注入包名、版本号、签名身份与描述文件。
- Ruby 依赖见根目录与各平台目录下的 `Gemfile`（fastlane + cocoapods，ruby >= 2.6.10）。

## CI / 发布流程

`.github/workflows/` 下有 4 个工作流（`assemble_android_debug/release.yml`、`assemble_ios_debug/release.yml`），均在 push 到 `master`、打 `v*` tag 或 PR 到 `master` 时触发，也可手动触发：

- 统一使用 Node 20 + pnpm 8，`pnpm install` 后执行 fastlane 打包。
- Android 产物为 APK（`android/app/build/outputs/apk/...`），iOS 产物为 IPA + dSYM（`ios/taroDemo.ipa`），均上传为 artifact；打 tag 时通过 `softprops/action-gh-release` 发布 release 资产（tag 含 `beta` 时标记为 prerelease）。
- iOS release 流水线需要 secrets：`APP_ID`、`TEAM_ID`、签名证书 P12（`RELEASE_SIGNING_CERTIFICATE_P12_DATA` 等）、描述文件（`RELEASE_PROVISIONING_PROFILE_DATA` 等）、App Store Connect 账号，由 `.github/scripts/import-certificate.sh`、`import-profile.sh` 导入 keychain。
- Android release 目前使用仓库内的 `debug.keystore` 演示签名（见下方安全注意事项）。

## 代码风格与测试

- ESLint：`.eslintrc.js` 继承 `@react-native`（即 `@react-native/eslint-config`），运行 `pnpm lint`。
- Prettier：`.prettierrc.js`，规则为 `singleQuote: true`、`trailingComma: 'all'`、`bracketSpacing: false`、`bracketSameLine: true`、`arrowParens: 'avoid'`。请遵守该风格（如无花括号空格：`import {x} from 'y'`）。
- 测试：Jest + `react-test-renderer`，preset 为 `react-native`，测试文件放在 `__tests__/` 目录（`*.{ts,tsx}`）。当前仅有一个渲染冒烟测试。运行 `pnpm test`。
- TypeScript：`tsconfig.json` 直接继承 `@react-native/typescript-config`，无本地覆盖。

## 工程维护注意事项

- **升级 Taro 版本**：执行 `pnpm upgradePeerdeps`（如需指定版本，先修改 `package.json` 中该脚本）。
- **升级 RN 小版本**：使用 [upgrade-helper](https://react-native-community.github.io/upgrade-helper/)。
- **Android 定制点**：国内 maven 镜像源（阿里云，`android/build.gradle`）、fresco gif 支持（`animated-gif:2.5.0`）、`debuggableVariants = ["debug", "release"]`（即 release 也不内置打 bundle，jsbundle 由 Taro JS 工程输出）、expo-camera 自带 maven 仓库。
- **iOS 定制点**：`Info.plist` 已声明相机、相册、定位、麦克风、运动等权限描述；强制亮色主题；`ITSAppUsesNonExemptEncryption = false`；main.jsbundle 以资源引用方式加入工程。模拟器构建固定为 x86_64（Rosetta）：`project.pbxproj` 中 `EXCLUDED_ARCHS[sdk=iphonesimulator*] = arm64`，因为微信 `libWeChatSDK.a` 没有 arm64-simulator slice（真机构建不受影响）。若后续升级为带 XCFramework 的微信 SDK，可移除此限制恢复原生 arm64 模拟器构建。
- **微信 SDK（react-native-wechat-lib）**：已集成用于微信支付（JS 侧 registerApp 的 AppID/Universal Links 在 xhs 工程 `src/constants/pay.ts`，当前为占位符）。配置点：iOS `Info.plist` 的 `CFBundleURLTypes`（wx+AppID scheme，待替换真实 AppID）与 `LSApplicationQueriesSchemes`、`AppDelegate.h/.mm` 实现 `WXApiDelegate` 并转发 openURL/continueUserActivity、`taroDemo.entitlements` 的 `applinks:` 域名（待替换，需服务端托管 apple-app-site-association）；Android 不支持 autolinking，`MainApplication.kt` 手动注册 `WeChatPackage`，`wxapi/WXEntryActivity.kt` 与 `WXPayEntryActivity.kt` 为微信 SDK 约定的回调入口（包名不可改），Manifest 加了 `com.tencent.mm` 包可见性 queries。上线前还需在微信开放平台登记 Android 包名 + 签名 MD5、iOS Bundle ID。
- **不要改动**：`android/app/build.gradle` 中注释掉的 RN 默认 bundle 打包逻辑、`debuggableVariants` 配置，否则会与 Taro 分离模式的 bundle 输出流程冲突。

## 安全注意事项

- 仓库包含 `android/app/debug.keystore`（公开口令 `android`），仅用于 debug 与 CI 演示；**生产发布必须替换为自己的 keystore**，并通过 CI secrets 注入签名信息，切勿把真实 keystore 或口令提交到仓库。
- iOS 签名材料（P12、描述文件、App Store Connect 密码）只应存在于 GitHub secrets，由 `.github/scripts/` 中的脚本在 CI 内导入临时 keychain。
- `ios/taroDemo/Info.plist` 声明了大量敏感权限（定位、相册、相机、麦克风），裁剪原生依赖时需同步检查权限声明是否仍然必要。
