# PerContext Community

一个用于个人随记上下文的小工具，也是一个 AI 时代的日记小工具。

目前基于 Android 开发。你可以随时记录每天的见闻、想法和感悟，保存在自己的手机里；需要时，使用自己配置的云端模型整理归纳，留下每天的结构化上下文。

如果喜欢这个项目，欢迎点一个 Star ⭐。如果有好的意见和建议，欢迎通过 [GitHub Issues](https://github.com/qinjunw/PerContext-Community/issues) 联系交流。谢谢！

## 当前功能

- 手动录音、播放、按日查看和删除录音。
- APK 内置 SenseVoiceSmall INT8 模型，语音转文字在手机离线完成。
- 使用 DeepSeek 预设，或自填 HTTPS API 地址、模型名称和 API Key，通过 OpenAI 兼容 Chat Completions 接口生成每日回顾。
- 回顾包含标题、摘要、主题、想法、问题、决定和待办，并保留与当天转写的来源关系。
- API Key 经 Android Keystore 加密后保存在应用私有存储；离开设置页会清除明文输入草稿，已保存的 Key 仍可供回顾任务使用。
- 晴空奶白、奶油海盐、雾紫蓝三组主题，可跟随系统或固定日间、夜间，选择自动保存在本机。
- 记录页和回顾页加入趴睡皮蛋，身体呼吸起伏、轻微点头并间歇摆尾。记录页录音期间暂停皮蛋动画；页面进入后台或系统关闭动画时保持静止。

当前只支持 `arm64-v8a` 设备，最低 Android 6.0（API 23）。这是早期社区版本，自动化检查与设备验证范围见 [验证记录](docs/VERIFICATION.md)。

## 使用

当前版本为 [0.1.2-community（预发布）](https://github.com/qinjunw/PerContext-Community/releases/tag/v0.1.2-community)：下载 [ARM64 APK](https://github.com/qinjunw/PerContext-Community/releases/download/v0.1.2-community/PerContext-Community-0.1.2-arm64.apk)，校验值见 [SHA256SUMS.txt](https://github.com/qinjunw/PerContext-Community/releases/download/v0.1.2-community/SHA256SUMS.txt)。安装包已包含离线转写模型，无需另行下载模型文件。

已安装本仓库发布的同一签名社区版 0.1.0、0.1.1 或主题/动效预览包的用户，可直接覆盖安装以保留应用数据。升级前无需卸载；卸载会清除时间线、转写、回顾和设置。

1. 打开 App，点击「记录」并授予麦克风权限；Android 6–9 还需授予存储权限。Android 13 及以上会申请通知权限，用于显示录音状态；拒绝通知权限仍可录音。停止后，录音出现在时间线。
2. 点击录音的转写按钮。首次转写会把 APK 内的模型校验并安装到应用私有目录，随后离线识别。
3. 点击右上角齿轮进入设置。选择 DeepSeek 预设，或选择「自定义 OpenAI 兼容服务」。
4. 填写所选服务的 API Key。自定义服务还需填写 API Base URL 和模型名称：地址示例为 `https://api.example.com/v1`，模型名称必须使用服务提供方给出的模型 ID。
5. 保存后打开「回顾」，选择有转写的日期，点击生成当天回顾。

在「设置 → 外观」选择主题与显示模式，选择后自动生效，无需点击模型服务的「保存设置」。默认使用晴空奶白并跟随系统；外观设置与模型服务配置分开保存。

地址会自动补全 `/chat/completions`；也接受以该路径结尾的完整接口地址。当前要求 HTTPS，地址中不能包含账号、查询参数或片段。自定义服务需支持非流式 Chat Completions、`response_format: {"type":"json_object"}`，并返回 `choices[].message.content` 及 `finish_reason: "stop"`。

更换 API 地址时需重新输入 Key；仅修改模型名称可沿用该地址的已保存 Key。接口重定向会被拒绝。模型服务的费用和使用额度由你所选的提供方决定。

「清除本地 Key」会立即删除当前服务在本机保存的 Key，不会撤销服务提供方的 Key。

## 数据保存在什么地方

原始音频位于手机公共 `Download/PerContext/Recordings` 目录，录音索引、转写和每日回顾保存在应用本地数据库中。语音转文字不上传音频。

主动生成或刷新回顾会创建联网任务；任务执行时，将所选日期的转写正文、日期、时区、各段录音时刻及来源编号发送到你配置的模型服务，不发送原始音频。离线时发起的任务会等待联网，并可在后台执行。对方如何处理这些内容，取决于该服务的政策。App 不会自行创建回顾任务，也没有云端同步。

删除录音会移除时间线中的录音、对应转写和回顾来源关联，已生成的回顾正文仍保留。刷新回顾成功后会替换旧内容，失败时保留上次成功的回顾。

卸载 App 会移除应用数据库和设置，公共目录中的原始录音仍保留；重新安装不会自动重建旧时间线。社区版包名为 `com.percontext.community`，使用独立应用数据。

## 从源码构建

需要 JDK 17 或更高版本、Android SDK Platform 37、Python 3.10 或更高版本。通过 `ANDROID_HOME` 或本地 `local.properties` 指定 Android SDK。

先获取固定模型包：

```sh
python tools/fetch_sensevoice.py
```

脚本从 sherpa-onnx 官方 Release 下载模型，验证文件长度与 SHA-256 后写入 Git 忽略的 `local-models/models/`。模型权重不存入 Git，但会进入构建出的每个 APK。下载约 163 MB，解压后的模型和词表约 240 MB。

Windows：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

macOS/Linux：

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。也可以使用 `-Ppercontext.sensevoiceModelDir=<model-directory>` 指向已有的相同模型包，构建仍会校验模型和许可文件。

发布构建使用 `:app:assembleRelease :app:lintRelease`，启用 R8 与资源收缩。发布签名通过本地 `signing.local.properties` 配置，格式见 [签名模板](signing.properties.example)；未配置时生成未签名 Release APK。签名材料和本地配置不进入 Git。

## 开发与验证

应用代码使用 Kotlin、Jetpack Compose、Room、WorkManager、Media3、Ktor 与 sherpa-onnx。语音处理、模型调用、存储和页面通过领域接口连接。详见 [架构说明](docs/ARCHITECTURE.md) 和 [贡献说明](CONTRIBUTING.md)。

## 许可证

本项目原创源码使用 [Apache License 2.0](LICENSE)。第三方代码与模型保留各自的许可，见 [NOTICE](NOTICE)。

SenseVoiceSmall 模型权重使用随包保留的 [FunASR Model Open Source License Agreement 1.1](app/src/main/sensevoice-legal/MODEL_LICENSE)，并附有[模型出处与作者声明](app/src/main/sensevoice-legal/NOTICE)。项目的 Apache-2.0 许可不替代模型许可。
