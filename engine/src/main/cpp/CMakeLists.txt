cmake_minimum_required(VERSION 3.22.1)
project(snapvmaf)

set(VMAF_PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/vmaf/prebuilt/${ANDROID_ABI}/libvmaf.a")
if(NOT EXISTS "${VMAF_PREBUILT}")
    message(WARNING "libvmaf prebuilt missing for ABI ${ANDROID_ABI}; VMAF native lib skipped")
    return()
endif()

add_library(vmaf_prebuilt STATIC IMPORTED)
set_target_properties(vmaf_prebuilt PROPERTIES
    IMPORTED_LOCATION "${VMAF_PREBUILT}"
)

add_library(snapvmaf SHARED vmaf_jni.c)
target_include_directories(snapvmaf PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/vmaf/include")
target_link_libraries(snapvmaf
    vmaf_prebuilt
    android
    log
    m
    c++_static
)
set_target_properties(snapvmaf PROPERTIES LINKER_LANGUAGE CXX)
