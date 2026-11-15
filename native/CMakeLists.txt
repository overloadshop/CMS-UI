cmake_minimum_required(VERSION 3.18.1)
project("clearmotion_jni")

# CLEAR Motion Pro —— Android JNI 桥接库
#
# 只编译 clearmotion_jni_bridge.cpp；运行时通过 dlopen/dlsym 动态打开
# libCLEARMotionPro.so（因此无需在编译期链接引擎，引擎库放 jniLibs/ 即可）。
add_library(clearmotion_jni SHARED
    clearmotion_jni_bridge.cpp
)

find_library(log-lib log)
find_library(dl-lib dl)
target_link_libraries(clearmotion_jni
    ${log-lib}
    ${dl-lib}
    android
)

target_compile_features(clearmotion_jni PUBLIC cxx_std_17)

# 16KB 页对齐（Android 15+ / Google Play 要求）
target_link_options(clearmotion_jni PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)
