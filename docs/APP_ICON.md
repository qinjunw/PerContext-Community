# 应用图标

PerContext Community 使用天蓝色背景的团子皮蛋图标。桌面入口和圆形图标入口均引用 `@mipmap/ic_launcher`。

| 资源 | 用途 |
| --- | --- |
| `app/src/main/res/mipmap-nodpi/ic_launcher.png` | 用户选定的完整图稿，也是 Android 6.0–7.1 的图标资源 |
| `app/src/main/res/drawable-nodpi/pidan_foreground.png` | 透明背景的猫咪前景，保留白色身体、脸部花纹和尾巴环纹 |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | 为前景设置 18% 内缩与位图缩放过滤 |
| `app/src/main/res/values/ic_launcher_background.xml` | 天蓝色底层，颜色为 `#78D8F5` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Android 8.0 及以上使用的自适应图标 |

自适应图标由系统裁切为圆形、圆角矩形等形状。前景按 108 dp 图层内缩后，以 alpha 至少为 128 的像素计算，图形距中心最远约 32.1 dp，位于直径 66 dp 的安全区内。尺寸依据见 [Android 自适应图标说明](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)。

替换图稿后，检查旧版资源与自适应资源的视觉一致性，并执行 `:app:testDebugUnitTest :app:lintRelease :app:assembleRelease`。发布前核对最终 APK 的图标资源与签名；桌面实际显示效果由设备启动器决定。
