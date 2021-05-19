cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j.ffmpeg)

if (DEFINED ENV{CMAKE_BUILD_TYPE})
   set(CMAKE_BUILD_TYPE $ENV{CMAKE_BUILD_TYPE})
endif()

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")

set(CMAKE_CXX_FLAGS_RELEASE "-O3")
set(CMAKE_C_FLAGS_RELEASE "-O3")
set(CMAKE_CXX_FLAGS_DEBUG "-g")
set(CMAKE_C_FLAGS_DEBUG "-g")

# set where to find additional cmake modules if any
# comment it out if not required
set(CMAKE_MODULE_PATH ${PROJECT_SOURCE_DIR}/cmake ${CMAKE_MODULE_PATH})

# std C++-11
set(CMAKE_CXX_STANDARD 11)

if(NOT DEFINED ENV{COMMON_INCLUDE})
  message(FATAL_ERROR "You need to define the environment variable COMMON_INCLUDE to point to '[pilecv4j-root]/native-common-include/include' or build from the pom file which sets it correctly.")
endif()
include_directories($ENV{COMMON_INCLUDE})

find_path( AVCODEC_INCLUDE_DIR libavcodec/avcodec.h )
find_library( AVCODEC_LIBRARY avcodec )

# comment this out to remove logging from the underlying code. Though it's also gated in the code so it's likely unnecessary
add_definitions( -DLOGGING )

# build target
add_library(${PROJECT_NAME} SHARED
  ffmpeg_wrapper.cpp
  ffmpeg_codes.cpp
  )

target_include_directories(${PROJECT_NAME} PRIVATE src/main/cpp PUBLIC ${AVCODEC_INCLUDE_DIR})
target_link_libraries(${PROJECT_NAME} ${AVCODEC_LIBRARY})


