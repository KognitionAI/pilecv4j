cmake_minimum_required (VERSION 3.4)
project (utilities.jiminger.com)

find_package(Java REQUIRED)
find_package(JNI REQUIRED)

include(CheckTypeSize)
CHECK_TYPE_SIZE(float FLOAT_SIZE BUILTIN_TYPES_ONLY)
CHECK_TYPE_SIZE(double DOUBLE_SIZE BUILTIN_TYPES_ONLY)
CHECK_TYPE_SIZE("long double" LONG_DOUBLE_SIZE BUILTIN_TYPES_ONLY)
message(STATUS "Double size = ${DOUBLE_SIZE}")
message(STATUS "Long Double size = ${LONG_DOUBLE_SIZE}")

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

include(UseJava)
set(CMAKE_JAVA_COMPILE_FLAGS "-source" "1.8" "-target" "1.8")

set (_stubDir "${CMAKE_CURRENT_BINARY_DIR}")
set (CMAKE_CXX_STANDARD 11)

if(UNIX)
  add_definitions(-DUNIX)
endif()

add_custom_command(
    OUTPUT com_jiminger_image_houghspace_Transform.h
           com_jiminger_image_mjpeg_MJPEGWriter.h
# We're not ready for this yet. We'll need to build TensorFlow from scratch to get it to work
#           com_jiminger_image_TensorUtils.h
    COMMAND ${Java_JAVAH_EXECUTABLE} -verbose
        -classpath ../lib/lib-image-$ENV{PROJECT_VERSION}.jar
        -d ${_stubDir}
        -jni com.jiminger.image.houghspace.Transform com.jiminger.image.mjpeg.MJPEGWriter
# We're not ready for this yet. We'll need to build TensorFlow from scratch to get it to work
#        -jni com.jiminger.image.houghspace.Transform com.jiminger.image.mjpeg.MJPEGWriter com.jiminger.image.TensorUtils
    )

# We're not ready for this yet. We'll need to build TensorFlow from scratch to get it to work
#include_directories(${JNI_INCLUDE_DIRS} ${_stubDir} $ENV{TENSORFLOW_INCLUDE})

include_directories(${JNI_INCLUDE_DIRS} ${_stubDir})
include_directories(sources/include)

add_library(utilities.jiminger.com
	MODULE com_jiminger_image_houghspace_Transform.h
               com_jiminger_image_mjpeg_MJPEGWriter.h
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

target_link_libraries(utilities.jiminger.com ${JNI_LIBRARIES})
