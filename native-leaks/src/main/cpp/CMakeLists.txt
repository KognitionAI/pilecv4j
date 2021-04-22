cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j.leaks)

if (DEFINED ENV{CMAKE_BUILD_TYPE})
   set(CMAKE_BUILD_TYPE $ENV{CMAKE_BUILD_TYPE})
endif()

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

set(CMAKE_CXX_FLAGS_RELEASE "-O2")
set(CMAKE_C_FLAGS_RELEASE "-O2")
set(CMAKE_CXX_FLAGS_DEBUG "-g")
set(CMAKE_C_FLAGS_DEBUG "-g")

message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")

set (CMAKE_CXX_STANDARD 11)

find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

if(UNIX)
  add_definitions(-DUNIX -D_GNU_SOURCE)
  add_compile_options(-Wall -Wextra)
else()
  message(FATAL_ERROR "This library can only be built on linux.")
endif()

add_library(${PROJECT_NAME}
  MODULE
  sources/leak.cpp)

target_link_libraries(${PROJECT_NAME}
  ${JNI_LIBRARIES})
