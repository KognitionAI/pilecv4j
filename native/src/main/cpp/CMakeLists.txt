cmake_minimum_required (VERSION 3.4)
project (utilities.jiminger.com)

include(CheckTypeSize)
CHECK_TYPE_SIZE(float FLOAT_SIZE BUILTIN_TYPES_ONLY)
CHECK_TYPE_SIZE(double DOUBLE_SIZE BUILTIN_TYPES_ONLY)
CHECK_TYPE_SIZE("long double" LONG_DOUBLE_SIZE BUILTIN_TYPES_ONLY)

if (FLOAT_SIZE EQUAL 4)
  message(STATUS "4 byte float is \"float\"")
  add_definitions(-DFLOAT_4BYTE)
elseif(DOUBLE_SIZE EQUAL 4)
  add_definitions(-DDOUBLE_4BYTE)
  message(STATUS "4 byte float is \"double\"")
elseif(LONG_DOUBLE_SIZE EQUAL 4)
  add_definitions(-DLONG_DOUBLE_4BYTE)
  message(STATUS "4 byte float is \"long double\"")
else()
  message(FATAL_ERROR "Failed to determine 4 byte float")
endif()


if (FLOAT_SIZE EQUAL 8)
  message(STATUS "8 byte float is \"float\"")
  add_definitions(-DFLOAT_8BYTE)
elseif(DOUBLE_SIZE EQUAL 8)
  add_definitions(-DDOUBLE_8BYTE)
  message(STATUS "8 byte float is \"double\"")
elseif(LONG_DOUBLE_SIZE EQUAL 8)
  add_definitions(-DLONG_DOUBLE_8BYTE)
  message(STATUS "8 byte float is \"long double\"")
else()
  message(FATAL_ERROR "Failed to determine 4 byte float")
endif()

set (CMAKE_CXX_STANDARD 11)

if(UNIX)
  add_definitions(-DUNIX)
  # buffer overrun protection
  set (CMAKE_MODULE_LINKER_FLAGS "-Wl,-z,noexecstack")
endif()

message(STATUS "OpenCV Dependency version $ENV{DEP_OPENCV_VERSION}")

if (DEFINED ENV{OPENCV_INSTALL})
  set(HAVE_CUSTOM_OCV_LOCATION true)
  message(STATUS "OPENCV_INSTALL is defined as $ENV{OPENCV_INSTALL}")
  set (OpenCV_DIR "$ENV{OPENCV_INSTALL}/share/OpenCV")
endif()

find_package( OpenCV CONFIG REQUIRED )

if (DEFINED HAVE_CUSTOM_OCV_LOCATION)
  include_directories(${OpenCV_INCLUDE_DIRS})
  link_directories("$ENV{OPENCV_INSTALL}/share/OpenCV/java")
  set(OPENCV_STATIC_LIB opencv_java${OpenCV_VERSION_MAJOR}${OpenCV_VERSION_MINOR}${OpenCV_VERSION_PATCH})
endif()

if ( NOT "${OpenCV_VERSION}" STREQUAL "$ENV{DEP_OPENCV_VERSION}")
  message(FATAL_ERROR "OpenCV Dependency version ($ENV{DEP_OPENCV_VERSION}) is different from the version being built against (${OpenCV_VERSION}).")
endif()

include_directories(sources/include)

add_library(utilities.jiminger.com
  MODULE
  sources/opencv/CvRasterNative.cpp
  sources/houghspace/Transform.cpp
  sources/mjpeg/jpegtoavi.cpp
  sources/nr/minimizer.c
  sources/nr/brent.cpp
  sources/nr/dbrent.cpp
  sources/nr/mnbrak.cpp
  sources/nr/nrutil.cpp
  sources/nr/powell.cpp)

target_link_libraries(utilities.jiminger.com 
  ${OPENCV_STATIC_LIB})
