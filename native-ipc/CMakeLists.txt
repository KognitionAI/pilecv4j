cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j.ipc C CXX)

if (DEFINED ENV{CMAKE_BUILD_TYPE})
   set(CMAKE_BUILD_TYPE $ENV{CMAKE_BUILD_TYPE})
endif()

#set(CMAKE_BUILD_TYPE Debug)
if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")

if (UNIX)
  set(CMAKE_CXX_FLAGS_RELEASE "-O")
  set(CMAKE_C_FLAGS_RELEASE "-O")
  set(CMAKE_CXX_FLAGS_DEBUG "-g")
  set(CMAKE_C_FLAGS_DEBUG "-g")
endif()

set(CMAKE_VERBOSE_MAKEFILE ON)

# set where to find additional cmake modules if any
# comment it out if not required
set(CMAKE_MODULE_PATH ${PROJECT_SOURCE_DIR}/cmake ${CMAKE_MODULE_PATH})

# std C++-11
set(CMAKE_CXX_STANDARD 11)

########################################################
# set float types
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
########################################################

set(CMAKE_INSTALL_PREFIX ${PROJECT_SOURCE_DIR})
set(CMAKE_CXX_STANDARD 17)

if (DEFINED ENV{OPENCV_INSTALL})
  message(STATUS "OPENCV_INSTALL is defined as $ENV{OPENCV_INSTALL}")
  set (OpenCV_DIR "$ENV{OPENCV_INSTALL}/share/OpenCV")
endif()

find_package(OpenCV CONFIG REQUIRED)

#############
#get_cmake_property(_variableNames VARIABLES)
#list (SORT _variableNames)
#foreach (_variableName ${_variableNames})
#  message(STATUS "${_variableName}=${${_variableName}}")
#endforeach()
#
#execute_process(COMMAND "${CMAKE_COMMAND}" "-E" "environment")
#############

# comment this out to remove logging from the underlying code. Though it's also gated in the code so it's likely unnecessary
#add_definitions( -DLOGGING -DENABLE_TRACE_API )
add_definitions( -DLOGGING )
add_definitions( -DTIMING )

if(MSVC)
  add_definitions( -D_CRT_SECURE_NO_WARNINGS )
endif()

set(SOURCE_FILES
  src/utils/log.h
  src/utils/log.cpp
  src/utils/errHandling.h
  src/utils/errHandling.cpp
  src/utils/SharedMemory.h
  src/utils/SharedMemory.cpp
  src/utils/platform/SharedMemoryTypes.h
  src/utils/platform/errHandlingImpl.h
  )

if(MSVC)
  set(SOURCE_FILES ${SOURCE_FILES}
     src/utils/platform/win/SharedMemoryTypes.h
     src/utils/platform/win/SharedMemoryImpl.h
     src/utils/platform/win/SharedMemoryImpl.cpp
     src/utils/platform/win/errHandlingImpl.h
     src/utils/platform/win/errHandlingImpl.cpp
     )
else()
  set(SOURCE_FILES ${SOURCE_FILES}
     src/utils/platform/posix/SharedMemoryTypes.h
     src/utils/platform/posix/SharedMemoryImpl.h
     src/utils/platform/posix/SharedMemoryImpl.cpp
     src/utils/platform/posix/errHandlingImpl.h
     src/utils/platform/posix/errHandlingImpl.cpp
     )
endif()

include_directories("src")
include_directories(${OpenCV_INCLUDE_DIRS})
include_directories($ENV{COMMON_INCLUDE})

add_library(ai.kognition.pilecv4j.ipc
  SHARED
  ${SOURCE_FILES})

if(NOT MSVC)
  target_link_options(ai.kognition.pilecv4j.ipc PUBLIC -pthread)
  target_link_libraries(ai.kognition.pilecv4j.ipc
    ${OPENCV_STATIC_LIB}
    rt
    )
else()
  target_link_libraries(ai.kognition.pilecv4j.ipc
    ${OPENCV_STATIC_LIB}
    )
endif()

  