cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j.util)

if (DEFINED ENV{CMAKE_BUILD_TYPE})
   set(CMAKE_BUILD_TYPE $ENV{CMAKE_BUILD_TYPE})
endif()

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")

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

  set(CMAKE_CXX_FLAGS_RELEASE "-O3")
  set(CMAKE_C_FLAGS_RELEASE "-O3")
  set(CMAKE_CXX_FLAGS_DEBUG "-g")
  set(CMAKE_C_FLAGS_DEBUG "-g")
endif()

include_directories($ENV{COMMON_INCLUDE})

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

add_library(ai.kognition.pilecv4j.util
  MODULE
  sources/nr/minimizer.c
  sources/nr/brent.cpp
  sources/nr/dbrent.cpp
  sources/nr/mnbrak.cpp
  sources/nr/nrutil.cpp
  sources/nr/powell.cpp)

target_link_libraries(ai.kognition.pilecv4j.util
  ${OPENCV_STATIC_LIB})
