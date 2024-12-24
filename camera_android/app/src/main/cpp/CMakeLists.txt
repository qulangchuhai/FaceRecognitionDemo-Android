# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

#导入头文件目录
include_directories(include
        ../cpp/)

#导入OpenCV的配置文件
set(OpenCV_DIR /Users/admin/work/OpenCV-android-sdk/sdk/native/jni)
find_package(OpenCV 3 REQUIRED)

#source directory源文件目录
file(GLOB FACE_SRC *.h
        *.cpp)
set(FACE_COMPILE_CODE ${FACE_SRC})
#添加ncnn库
add_library(libncnn STATIC IMPORTED )
set_target_properties(libncnn
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libncnn.a)

#编译为动态库
add_library(Face
        SHARED
        ${FACE_COMPILE_CODE})

#添加工程所依赖的库
find_library(log-lib log android)
target_link_libraries(Face
        libncnn
        android
        z
        jnigraphics
        ${OpenCV_LIBS}
        ${log-lib})