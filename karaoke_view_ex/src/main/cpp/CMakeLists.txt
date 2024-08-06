cmake_minimum_required(VERSION 2.8.0)
project(ai-engine-sdk)

add_library( # Sets the name of the library.
        agora-karaoke-ai

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        io_agora_karaoke_view_ex_internal_ai_AIAlgorithmScoreNative.cpp
        Algorithm.c
        )

find_library( # Sets the name of the path variable.
        log-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/)

target_link_libraries( # Specifies the target library.
        agora-karaoke-ai

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})
