cmake_minimum_required(VERSION 3.22.1)
project(vybe_fingerprint LANGUAGES C CXX)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(FFT_LIB kissfft CACHE STRING "" FORCE)
set(KISSFFT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/chromaprint/src/3rdparty/kissfft" CACHE PATH "" FORCE)
set(KISSFFT_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/chromaprint/src/3rdparty/kissfft" CACHE PATH "" FORCE)
set(USE_INTERNAL_AVRESAMPLE ON CACHE BOOL "" FORCE)

add_subdirectory(chromaprint EXCLUDE_FROM_ALL)
add_library(vybe_fingerprint SHARED fingerprint_jni.cpp)
target_include_directories(vybe_fingerprint PRIVATE chromaprint/src chromaprint/src/include)
# Keep the C++ runtime inside the single ARM64 recognition library. This avoids
# shipping libc++_shared.so and guarantees that every Chromaprint C++ symbol is
# resolved by the library that loads through System.loadLibrary().
target_link_libraries(vybe_fingerprint PRIVATE chromaprint c++_static c++abi log)
