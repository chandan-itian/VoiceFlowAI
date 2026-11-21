cmake_minimum_required(VERSION 3.22.1)
project(voiceflow_native C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "Build tests" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "Build examples" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "Build server" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libs" FORCE)

add_subdirectory(whisper_src)

add_library(whisper_jni SHARED
    whisper_jni.cpp
)

target_include_directories(whisper_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper_src/include
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper_src/ggml/include
)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

target_link_libraries(whisper_jni
    whisper
    ${ANDROID_LIB}
    ${LOG_LIB}
)
