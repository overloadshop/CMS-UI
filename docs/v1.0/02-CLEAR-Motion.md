# 02 · CLEAR Motion Pro（Android）

CLEAR Motion Pro 是一套原生物理动效引擎（弹簧 / 滚动模拟），Android 侧通过 JNI 调用。

---

## 1. 组成

| 路径 | 说明 |
| --- | --- |
| `cms/CLEARMotion.kt` | Kotlin 侧 JNI 封装（`object CLEARMotion` + `SpringDriver`） |
| `native/clearmotion_jni_bridge.cpp` | JNI 桥接层，**运行时** `dlopen/dlsym` 打开引擎库 |
| `native/CLEARMotionPro.h` | JNI 桥使用的引擎符号声明 |
| `native/CpuFeatures.h` | CPU 特性探测头（引擎使用） |
| `native/CMakeLists.txt` | 编译 `libclearmotion_jni.so`（不链接引擎） |
| `native/jniLibs/<abi>/libCLEARMotionPro.so` | 引擎预编译库：arm64-v8a / armeabi-v7a / x86 / x86_64 |
| `native/engine/` | 引擎 C++ 源码（重建参考） |
| `native/licenses/` | CLEAR Motion 许可与声明 |

---

## 2. 链接方式（关键）

桥接层**不**在编译期链接 `libCLEARMotionPro.so`，而是运行时动态解析符号：

```cpp
// clearmotion_jni_bridge.cpp
static void* gLibHandle = nullptr;   // dlopen("libCLEARMotionPro.so")
// 之后 dlsym 出 create / destroy / update / setTarget … 等函数指针
```

因此集成时：
- `libCLEARMotionPro.so` 放进 `jniLibs/<abi>/` 即可（**无需**改动 CMake 链接项）；
- `libclearmotion_jni.so` 由 CMake 编译生成；
- Kotlin 侧 `System.loadLibrary("clearmotion_jni")`，由桥接层内部去加载引擎库。

> 好处：引擎库可独立替换 / 升级，不改动桥接编译产物。

---

## 3. Kotlin 侧接口

### 3.1 `object CLEARMotion`

**PhysicsEngine**

```kotlin
nativeEngineCreate(): Long
nativeEngineDestroy(engine: Long)
nativeEngineUpdate(engine: Long, dt: Double)
nativeEngineStart(engine: Long)
nativeEngineStop(engine: Long)
```

**ScrollSimulator（滚动模拟器）**

```kotlin
nativeScrollCreate(): Long
nativeScrollDestroy(ptr: Long)
nativeScrollSetBounds(ptr: Long, minPos: Double, maxPos: Double)
nativeScrollStart(ptr: Long, position: Double)
nativeScrollMove(ptr: Long, delta: Double)
nativeScrollEnd(ptr: Long, velocity: Double)
nativeScrollUpdate(ptr: Long, dt: Double)
nativeScrollGetPosition(ptr: Long): Double
nativeScrollGetVelocity(ptr: Long): Double
nativeScrollIsDragging(ptr: Long): Boolean
```

**SpringAnimator（弹簧动画器）**

```kotlin
nativeSpringCreate(
    initialValue: Double, stiffness: Double, damping: Double, mass: Double,
    gravity: Double, roughness: Double, airResistance: Double
): Long
nativeSpringDestroy(ptr: Long)
nativeSpringSetTarget(ptr: Long, target: Double)
nativeSpringUpdate(ptr: Long, dt: Double)
nativeSpringGetValue(ptr: Long): Double
nativeSpringGetVelocity(ptr: Long): Double
nativeSpringIsRunning(ptr: Long): Boolean
```

用法：先 `create` 拿到指针，每帧传入 `dt` 调 `update`，用 `get*` 读值，最后 `destroy` 释放。

### 3.2 `SpringDriver`（Kotlin 实现）

```kotlin
class SpringDriver(
    stiffness: Float = 200f,
    damping: Float = 26f,
    mass: Float = 1f,
    roughness: Float = 0.05f,
    airResistance: Float = 0.01f
) {
    var onUpdate: ((Float) -> Unit)?
    var onSettled: (() -> Unit)?
    fun setTarget(t: Float)
    fun getValue(): Float
    fun isRunning(): Boolean
    fun reset(value: Float = 0f)
    fun destroy()
}
```

基于 `Choreographer` 逐帧积分（弹簧力 + 阻尼 + 空气阻力）。`CMSBottomBar` 的展开 / 收起即用它驱动。

> ⚠ 源码注释标注：`SpringDriver` 是 Kotlin 侧实现，后续计划迁移到 `nativeSpring*`。
> 追求极致一致性时，优先用 `CLEARMotion.nativeSpring*`。

---

## 4. 集成步骤

1. `native/jniLibs/` 整个目录 → 宿主 `app/src/main/jniLibs/`。
2. `native/clearmotion_jni_bridge.cpp`、`CLEARMotionPro.h`、`CpuFeatures.h`、`CMakeLists.txt`
   → 宿主 `app/src/main/cpp/`。
3. `app/build.gradle.kts` 开启 CMake：

```kotlin
android {
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

4. 拷贝 `cms/CLEARMotion.kt` 到源码，直接使用。

---

## 5. 平台与许可

- **16KB 页对齐**：`jniLibs/` 为通用构建；Android 15+ 需要的 16KB 变体位于源工程的
  `CLEAR Motion/CLEAR Motion Pro/lib/android/arm64-v8a/*_16kb.so`。
- **其他平台**：引擎同时提供 Windows（`CLEAR Motion Pro/lib/windows/`）与 Rust 源码
  （`CLEAR Motion/rust/`），本包只带 Android 运行时。
- **许可**：见 `native/licenses/`（`LICENSE`、`LICENSE-PROPRIETARY`、`NOTICE`），使用前请阅读。
