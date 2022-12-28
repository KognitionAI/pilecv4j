cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j.image)

# if you're going to set the build type here, do it before include CMakeHelpers.cmake
#set(CMAKE_BUILD_TYPE Debug)

# set this before include CMakeHelpers.cmake
set(REQUIRE_OPENCV_COMPILE ON)
set(REQUIRE_OPENCV_LINK ON)

# make sure the commons dir is available
if(NOT DEFINED COMMON_INCLUDE)
  if(DEFINED ENV{COMMON_INCLUDE})
    set(COMMON_INCLUDE $ENV{COMMON_INCLUDE})
  else()
    set(COMMON_INCLUDE ${PROJECT_SOURCE_DIR}/../native-common-include/include)
  endif()
endif()

message(STATUS "COMMON_INCLUDE: ${COMMON_INCLUDE}")

INCLUDE(${COMMON_INCLUDE}/../CMakeHelpers.cmake)

#add_definitions( -DLOGGING -DENABLE_TRACE_API )
add_definitions( -DLOGGING )

CHECK_INCLUDE_FILE("endian.h" HAVE_ENDIAN_H)
if (HAVE_ENDIAN_H)
  add_definitions(-DHAVE_ENDIAN_H)
endif()

if(MSVC)
  # ===========================================================
  # Swith linking against the dynamic runtime to linking against
  # the static runtime since that's required to link against
  # the highgui static library since that's the way that was
  # built. 
  set(variables
    CMAKE_C_FLAGS_DEBUG
    CMAKE_C_FLAGS_MINSIZEREL
    CMAKE_C_FLAGS_RELEASE
    CMAKE_C_FLAGS_RELWITHDEBINFO
    CMAKE_CXX_FLAGS_DEBUG
    CMAKE_CXX_FLAGS_MINSIZEREL
    CMAKE_CXX_FLAGS_RELEASE
    CMAKE_CXX_FLAGS_RELWITHDEBINFO
    )
  message(STATUS "MSVC -> forcing use of statically-linked runtime." )
  foreach(variable ${variables})
    if(${variable} MATCHES "/MD")
      string(REGEX REPLACE "/MD" "/MT" ${variable} "${${variable}}")
    endif()
  endforeach()
endif()

include_directories(src/main/cpp)

add_library(ai.kognition.pilecv4j.image
  MODULE
  src/main/cpp/opencv/CvRasterNative.cpp
  src/main/cpp/opencv/imagemaker.cpp
  src/main/cpp/houghspace/Transform.cpp
  src/main/cpp/mjpeg/jpegtoavi.cpp
  src/main/cpp/utils/log.cpp)

target_link_libraries(ai.kognition.pilecv4j.image
  ${OPENCV_STATIC_LIB})
