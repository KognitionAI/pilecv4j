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
endif()

# We're not ready for this yet. We'll need to build TensorFlow from scratch to get it to work
#include_directories(${JNI_INCLUDE_DIRS} ${_stubDir} $ENV{TENSORFLOW_INCLUDE})

include_directories(sources/include)

add_library(utilities.jiminger.com
	MODULE
# We're not ready for this yet. We'll need to build TensorFlow from scratch to get it to work
#               com_jiminger_image_TensorUtils.h
#               sources/tensor/TensorUtils.cpp
               sources/houghspace/Transform.cpp
               sources/mjpeg/jpegtoavi.cpp
               sources/nr/minimizer.c
               sources/nr/brent.cpp
               sources/nr/dbrent.cpp
               sources/nr/mnbrak.cpp
               sources/nr/nrutil.cpp
               sources/nr/powell.cpp)

target_link_libraries(utilities.jiminger.com)
