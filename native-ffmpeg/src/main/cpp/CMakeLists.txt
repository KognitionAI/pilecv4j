#--------------------------------------------
# C++ language project template
#
# default folder structure as follows
# <project root folder>
#      cmake/   <- here the additional modules with handy macro and finders
#      include/
#      src/
#      test/
#
#--------------------------------------------
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

# use standard file endings - rename to cxx if needed
set(SRC_EXT_PATTERN ".cpp")

# define project for C++ language
project(${PROJECT_NAME} CXX)

# set where to find additional cmake modules if any
# comment it out if not required
set(CMAKE_MODULE_PATH ${PROJECT_SOURCE_DIR}/cmake ${CMAKE_MODULE_PATH})

# set compiler support for C++11 standard
option(USE_CXX11_STD "Product should be build with C++11 compiler option enabled" ON)

if(USE_CXX11_STD)
    set(CMAKE_CXX_STANDARD 11)
endif()

if(NOT DEFINED ENV{COMMON_INCLUDE})
  message(FATAL_ERROR "You need to define the environment variable COMMON_INCLUDE to point to '[pilecv4j-root]/native-common-include/include' or build from the pom file which sets it correctly.")
endif()
include_directories($ENV{COMMON_INCLUDE})

find_path( AVCODEC_INCLUDE_DIR libavcodec/avcodec.h )
find_library( AVCODEC_LIBRARY avcodec )

add_definitions( -DLOGGING )

# build target
add_library(${PROJECT_NAME} SHARED
  ffmpeg_wrapper.cpp
  )

target_include_directories(${PROJECT_NAME} PRIVATE src/main/cpp PUBLIC ${AVCODEC_INCLUDE_DIR})
target_link_libraries(${PROJECT_NAME} ${AVCODEC_LIBRARY})


