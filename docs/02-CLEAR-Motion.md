# 02 · CLEAR Motion（v1.1.0）

> 1.0 版说明见 [`docs/v1.0/02-CLEAR-Motion.md`](v1.0/02-CLEAR-Motion.md)。

## 1. 组成

| 层 | 位置 | 说明 |
| --- | --- | --- |
| 引擎二进制 | `library/native/jniLibs/<abi>/libCLEARMotionPro.so` | 弹簧、滚动、物理步进；源码在独立的 CLEAR Motion 仓库 |
| JNI 桥 | `library/native/clearmotion_jni_bridge.cpp` | 导出 `native*` 方法 |
| Kotlin 封装 | `library/cms/CLEARMotion.kt` | `external` 声明 + 加载 `clearmotion_jni` |
| Kotlin 驱动 | `library/cms/CLEARMotion.kt` → `SpringDriver` | 独立纯 Kotlin 弹簧，不会自动替换原生调用 |

引擎本体已独立开源，本仓库只分发预编译库与 JNI 桥，不包含引擎源码与头文件。

## 2. JNI 接口

当前 JNI 桥实现滚动与弹簧两组接口。`nativeEngine*` 是保留声明，
尚未在桥接文件中实现，请使用下方 ScrollSimulator / SpringAnimator 接口。

```kotlin
object CLEARMotion {
    // PhysicsEngine
    external fun nativeEngineCreate(): Long
    external fun nativeEngineDestroy(engine: Long)
    external fun nativeEngineUpdate(engine: Long, dt: Double)
    external fun nativeEngineStart(engine: Long)
    external fun nativeEngineStop(engine: Long)

    // ScrollSimulator
    external fun nativeScrollCreate(): Long
    external fun nativeScrollDestroy(ptr: Long)
    external fun nativeScrollSetBounds(ptr: Long, minPos: Double, maxPos: Double)
    external fun nativeScrollStart(ptr: Long, position: Double)
    external fun nativeScrollMove(ptr: Long, delta: Double)
    external fun nativeScrollEnd(ptr: Long, velocity: Double)
    external fun nativeScrollUpdate(ptr: Long, dt: Double)
    external fun nativeScrollGetPosition(ptr: Long): Double
    external fun nativeScrollGetVelocity(ptr: Long): Double
    external fun nativeScrollIsDragging(ptr: Long): Boolean

    // SpringAnimator
    external fun nativeSpringCreate(
        initialValue: Double, stiffness: Double, damping: Double,
        mass: Double, gravity: Double, roughness: Double, airResistance: Double
    ): Long
    external fun nativeSpringDestroy(ptr: Long)
    external fun nativeSpringSetTarget(ptr: Long, target: Double)
    external fun nativeSpringUpdate(ptr: Long, dt: Double)
    external fun nativeSpringGetValue(ptr: Long): Double
    external fun nativeSpringGetVelocity(ptr: Long): Double
    external fun nativeSpringIsRunning(ptr: Long): Boolean
}
```

弹簧参数语义：

| 参数 | 含义 |
| --- | --- |
| `stiffness` | 刚度，越大回弹越快 |
| `damping` | 阻尼，越大越不振荡 |
| `mass` | 质量，越大越“重” |
| `gravity` | 附加重力（下拉/吸附类动效） |
| `roughness` | 粗糙度，影响细微抖动衰减 |
| `airResistance` | 空气阻力，影响长距离运动尾部 |

## 3. 使用方式

菜单、轮播、底栏等组件的标准驱动循环：

```kotlin
val spring = remember { CLEARMotion.nativeSpringCreate(0.0, 380.0, 14.0, 1.0, 0.0, 0.05, 0.01) }
DisposableEffect(Unit) { onDispose { CLEARMotion.nativeSpringDestroy(spring) } }

LaunchedEffect(visible) {
    CLEARMotion.nativeSpringSetTarget(spring, if (visible) 1.0 else 0.0)
}

LaunchedEffect(Unit) {
    var last = 0L
    while (true) {
        withFrameNanos { ns ->
            val dt = if (last == 0L) 1f / 60f else ((ns - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = ns
            CLEARMotion.nativeSpringUpdate(spring, dt.toDouble())
            progress = CLEARMotion.nativeSpringGetValue(spring).toFloat()
        }
    }
}
```

## 4. 集成

使用 `cms-core` 模块时，预编译引擎、JNI 编译、16KB 链接选项和 JNI 保留规则已经配置。
无需手工复制 native 文件。以下用于源码复制方式。

1. `library/native/jniLibs/<abi>/libCLEARMotionPro.so` → 宿主 `app/src/main/jniLibs/<abi>/`；
2. `library/native/clearmotion_jni_bridge.cpp`、`CMakeLists.txt` → 宿主 `app/src/main/cpp/`；
3. `app/build.gradle.kts` 配置：

```kotlin
android {
    defaultConfig {
        externalNativeBuild {
            cmake { cppFlags += "" }
        }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

4. 运行时 `System.loadLibrary("clearmotion_jni")` 由 `CLEARMotion` 的 `init` 完成。

## 5. 回退策略

`SpringDriver` 是纯 Kotlin 弹簧，通过 `setTarget()`、`reset()`、`getValue()`、
`onUpdate` 与 `onSettled` 使用；`target`、`value` 是私有字段。
不便引入原生库的宿主可以：

- 保留 `CLEARMotion.kt` 的 `SpringDriver`，删除 `object CLEARMotion`；或
- 保留两者，在业务层按需选择。

组件本身只依赖 `CLEARMotion.native*` 系列，若使用 `SpringDriver`，需要在组件初始化处替换为对应的驱动实现。

## 6. 许可

本仓库只随包分发 CLEAR Motion Pro 的预编译库，`library/native/licenses/` 内含 `LICENSE`、
`LICENSE-PROPRIETARY` 与 `NOTICE`，分发时请一并保留。引擎源码与构建方式见独立的
CLEAR Motion 仓库（MIT）。
