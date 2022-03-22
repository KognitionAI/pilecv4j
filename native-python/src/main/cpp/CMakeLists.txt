cmake_minimum_required (VERSION 3.16)
project (pilecv4jpython)

set(CMAKE_BUILD_TYPE Debug)
if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()
if(UNIX)
  set(CMAKE_CXX_FLAGS_RELEASE "-O3")
endif()

message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")

if(NOT DEFINED ENV{COMMON_INCLUDE})
  message(FATAL_ERROR "You need to define the environment variable COMMON_INCLUDE to point to '[pilecv4j-root]/native-common-include/include' or build from the pom file which sets it correctly.")
endif()

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

find_package(OpenCV CONFIG REQUIRED)
set(OPENCV_STATIC_LIB opencv_java${OPENCV_SHORT_VERSION})
find_package(Python3 COMPONENTS Interpreter Development NumPy REQUIRED)

include_directories($ENV{COMMON_INCLUDE})

add_library(pilecv4jpython SHARED
  log.cpp
  RunPythonFunction.cpp
  PythonEnvironment.cpp
  KogSystem.cpp
  ImageSource.cpp
  module.cpp
  kognition.cpp
  )

target_include_directories(pilecv4jpython PRIVATE src/main/cpp
  PUBLIC ${Python3_INCLUDE_DIRS}
  PUBLIC ${Python3_NumPy_INCLUDE_DIRS}
  PUBLIC ${OpenCV_INCLUDE_DIRS}
)

message(STATUS "Python 3 libraries: ${Python3_LIBRARIES}")
message(STATUS "OpenCV libraries: ${OPENCV_STATIC_LIB}")

target_link_libraries(pilecv4jpython ${Python3_LIBRARIES} ${OPENCV_STATIC_LIB})

#get_cmake_property(_variableNames VARIABLES)
#list (SORT _variableNames)
#foreach (_variableName ${_variableNames})
#   message(STATUS "${_variableName}=${${_variableName}}")
#endforeach()
