// clearmotion_jni_bridge.cpp
// JNI 桥接层：通过 dlopen/dlsym 动态调用 CLEAR Motion Pro 原生库
// 不直接链接 libCLEARMotionPro.so，而是运行时解析符号

#include <jni.h>
#include <dlfcn.h>
#include <cstdint>

// ===== 函数指针类型定义 =====
typedef void* (*CreateScrollFn)(void);
typedef void  (*DestroyScrollFn)(void*);
typedef void  (*SetBoundsFn)(void*, double, double);
typedef void  (*StartFn)(void*, double);
typedef void  (*MoveFn)(void*, double);
typedef void  (*EndFn)(void*, double);
typedef void  (*UpdateFn)(void*, double);
typedef double (*GetPositionFn)(void*);
typedef double (*GetVelocityFn)(void*);
typedef int32_t (*IsDraggingFn)(void*);
typedef void* (*CreateSpringFn)(double, double, double, double, double, double, double);
typedef void  (*DestroySpringFn)(void*);
typedef void  (*SetSpringTargetFn)(void*, double);
typedef void  (*UpdateSpringFn)(void*, double);
typedef double (*GetSpringValueFn)(void*);
typedef double (*GetSpringVelocityFn)(void*);
typedef int32_t (*IsSpringRunningFn)(void*);

// ===== 动态解析的符号表 =====
static void* gLibHandle = nullptr;

// 函数指针
static CreateScrollFn    fn_create  = nullptr;
static DestroyScrollFn   fn_destroy = nullptr;
static SetBoundsFn       fn_setBounds = nullptr;
static StartFn           fn_start   = nullptr;
static MoveFn            fn_move    = nullptr;
static EndFn             fn_end     = nullptr;
static UpdateFn          fn_update  = nullptr;
static GetPositionFn     fn_getPos  = nullptr;
static GetVelocityFn     fn_getVel  = nullptr;
static IsDraggingFn      fn_isDrag  = nullptr;
static CreateSpringFn    fn_springCreate = nullptr;
static DestroySpringFn   fn_springDestroy = nullptr;
static SetSpringTargetFn fn_springSetTarget = nullptr;
static UpdateSpringFn    fn_springUpdate = nullptr;
static GetSpringValueFn  fn_springGetValue = nullptr;
static GetSpringVelocityFn fn_springGetVelocity = nullptr;
static IsSpringRunningFn fn_springIsRunning = nullptr;

static bool ensureLoaded() {
    if (gLibHandle != nullptr) return true;
    gLibHandle = dlopen("libCLEARMotionPro.so", RTLD_LAZY | RTLD_LOCAL);
    if (gLibHandle == nullptr) return false;

    fn_create     = (CreateScrollFn)   dlsym(gLibHandle, "cm_scroll_create");
    fn_destroy    = (DestroyScrollFn)  dlsym(gLibHandle, "cm_scroll_destroy");
    fn_setBounds  = (SetBoundsFn)      dlsym(gLibHandle, "cm_scroll_set_bounds");
    fn_start      = (StartFn)          dlsym(gLibHandle, "cm_scroll_start");
    fn_move       = (MoveFn)           dlsym(gLibHandle, "cm_scroll_move");
    fn_end        = (EndFn)            dlsym(gLibHandle, "cm_scroll_end");
    fn_update     = (UpdateFn)         dlsym(gLibHandle, "cm_scroll_update");
    fn_getPos     = (GetPositionFn)    dlsym(gLibHandle, "cm_scroll_get_position");
    fn_getVel     = (GetVelocityFn)    dlsym(gLibHandle, "cm_scroll_get_velocity");
    fn_isDrag     = (IsDraggingFn)     dlsym(gLibHandle, "cm_scroll_is_dragging");
    fn_springCreate = (CreateSpringFn) dlsym(gLibHandle, "cm_spring_animator_create");
    fn_springDestroy = (DestroySpringFn) dlsym(gLibHandle, "cm_spring_animator_destroy");
    fn_springSetTarget = (SetSpringTargetFn) dlsym(gLibHandle, "cm_spring_animator_set_target");
    fn_springUpdate = (UpdateSpringFn) dlsym(gLibHandle, "cm_spring_animator_update");
    fn_springGetValue = (GetSpringValueFn) dlsym(gLibHandle, "cm_spring_animator_get_value");
    fn_springGetVelocity = (GetSpringVelocityFn) dlsym(gLibHandle, "cm_spring_animator_get_velocity");
    fn_springIsRunning = (IsSpringRunningFn) dlsym(gLibHandle, "cm_spring_animator_is_running");

    return fn_create && fn_destroy && fn_setBounds &&
           fn_start && fn_move && fn_end &&
           fn_update && fn_getPos && fn_getVel && fn_isDrag &&
           fn_springCreate && fn_springDestroy && fn_springSetTarget &&
           fn_springUpdate && fn_springGetValue &&
           fn_springGetVelocity && fn_springIsRunning;
}

// ===== JNI 导出函数 =====

extern "C" {

// 滚动模拟器
JNIEXPORT jlong JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeScrollCreate(JNIEnv* env, jclass clazz) {
    if (!ensureLoaded()) return 0;
    return (jlong)(intptr_t)fn_create();
}

JNIEXPORT void JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeScrollDestroy(JNIEnv* env, jclass clazz, jlong ptr) {
    if (fn_destroy && ptr) fn_destroy((void*)(intptr_t)ptr);
}

JNIEXPORT void JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeScrollSetBounds(JNIEnv* env, jclass clazz, jlong ptr, jdouble minPos, jdouble maxPos) {
    if (fn_setBounds && ptr) fn_setBounds((void*)(intptr_t)ptr, minPos, maxPos);
}

JNIEXPORT void JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeScrollStart(JNIEnv* env, jclass clazz, jlong ptr, jdouble position) {
    if (fn_start && ptr) fn_start((void*)(intptr_t)ptr, position);
}

JNIEXPORT void JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeScrollMove(JNIEnv* env, jclass clazz, jlong ptr, jdouble delta) {
    if (fn_move && ptr) fn_move((void*)(intptr_t)ptr, delta);
}

JNIEXPORT void JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeScrollEnd(JNIEnv* env, jclass clazz, jlong ptr, jdouble velocity) {
    if (fn_end && ptr) fn_end((void*)(intptr_t)ptr, velocity);
}

JNIEXPORT void JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeScrollUpdate(JNIEnv* env, jclass clazz, jlong ptr, jdouble dt) {
    if (fn_update && ptr) fn_update((void*)(intptr_t)ptr, dt);
}

JNIEXPORT jdouble JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeScrollGetPosition(JNIEnv* env, jclass clazz, jlong ptr) {
    if (fn_getPos && ptr) return (jdouble)fn_getPos((void*)(intptr_t)ptr);
    return 0.0;
}

JNIEXPORT jdouble JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeScrollGetVelocity(JNIEnv* env, jclass clazz, jlong ptr) {
    if (fn_getVel && ptr) return (jdouble)fn_getVel((void*)(intptr_t)ptr);
    return 0.0;
}

JNIEXPORT jboolean JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeScrollIsDragging(JNIEnv* env, jclass clazz, jlong ptr) {
    if (fn_isDrag && ptr) return (jboolean)fn_isDrag((void*)(intptr_t)ptr);
    return JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeSpringCreate(
    JNIEnv* env,
    jclass clazz,
    jdouble initialValue,
    jdouble stiffness,
    jdouble damping,
    jdouble mass,
    jdouble gravity,
    jdouble roughness,
    jdouble airResistance
) {
    if (!ensureLoaded()) return 0;
    return (jlong)(intptr_t)fn_springCreate(
        initialValue,
        stiffness,
        damping,
        mass,
        gravity,
        roughness,
        airResistance
    );
}

JNIEXPORT void JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeSpringDestroy(JNIEnv* env, jclass clazz, jlong ptr) {
    if (fn_springDestroy && ptr) fn_springDestroy((void*)(intptr_t)ptr);
}

JNIEXPORT void JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeSpringSetTarget(
    JNIEnv* env,
    jclass clazz,
    jlong ptr,
    jdouble target
) {
    if (fn_springSetTarget && ptr) fn_springSetTarget((void*)(intptr_t)ptr, target);
}

JNIEXPORT void JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeSpringUpdate(JNIEnv* env, jclass clazz, jlong ptr, jdouble dt) {
    if (fn_springUpdate && ptr) fn_springUpdate((void*)(intptr_t)ptr, dt);
}

JNIEXPORT jdouble JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeSpringGetValue(JNIEnv* env, jclass clazz, jlong ptr) {
    if (fn_springGetValue && ptr) return (jdouble)fn_springGetValue((void*)(intptr_t)ptr);
    return 0.0;
}

JNIEXPORT jdouble JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeSpringGetVelocity(JNIEnv* env, jclass clazz, jlong ptr) {
    if (fn_springGetVelocity && ptr) {
        return (jdouble)fn_springGetVelocity((void*)(intptr_t)ptr);
    }
    return 0.0;
}

JNIEXPORT jboolean JNICALL
Java_com_lindicra_cms_CLEARMotion_nativeSpringIsRunning(JNIEnv* env, jclass clazz, jlong ptr) {
    if (fn_springIsRunning && ptr) return (jboolean)fn_springIsRunning((void*)(intptr_t)ptr);
    return JNI_FALSE;
}

} // extern "C"
