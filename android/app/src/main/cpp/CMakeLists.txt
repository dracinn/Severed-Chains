cmake_minimum_required(VERSION 3.22.1)
project(scjvmhost C)

add_library(scjvmhost SHARED scjvmhost.c)

find_library(android-log-lib log)
find_library(android-dl-lib dl)
find_library(android-native-window-lib android)
find_library(android-egl-lib EGL)
find_library(android-gles-lib GLESv3)

# JRE 25's libjvm.so is linked against this Android NDK runtime. Linking it here packages
# the same ARM64 library in the APK so it is available in the app linker namespace.
target_link_libraries(scjvmhost c++_shared ${android-log-lib} ${android-dl-lib} ${android-native-window-lib} ${android-egl-lib} ${android-gles-lib})
