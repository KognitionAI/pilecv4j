cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j.ipc C CXX)

# if you're going to set the build type here, do it before include CMakeHelpers.cmake
#set(CMAKE_BUILD_TYPE Debug)

# set this before include CMakeHelpers.cmake
set(REQUIRE_OPENCV_COMPILE ON)

if(NOT DEFINED COMMON_INCLUDE)
  if(DEFINED ENV{COMMON_INCLUDE})
    set(COMMON_INCLUDE $ENV{COMMON_INCLUDE})
  else()
    set(COMMON_INCLUDE ${PROJECT_SOURCE_DIR}/../native-common-include/include)
  endif()
endif()

INCLUDE(${COMMON_INCLUDE}/../CMakeHelpers.cmake)

## set IPC type variables
if(DEFINED ENV{IPC_TYPE})
  set(IPC_TYPE $ENV{IPC_TYPE})
endif()

if(MSVC)
  if(NOT IPC_TYPE)
    set(IPC_TYPE windows)
  else()
    if(NOT IPC_TYPE STREQUAL "windows")
      message(FATAL_ERROR "Cannot set IPC_TYPE on Windows to anything but 'windows'. It's currently set to " ${IPC_TYPE})
    endif()
  endif()
else()
  if(NOT IPC_TYPE)
    set(IPC_TYPE posix)
  endif()
endif()

message(STATUS "ipc implementation is " ${IPC_TYPE})

# comment this out to remove logging from the underlying code. Though it's also gated in the code so it's likely unnecessary
#add_definitions( -DLOGGING -DENABLE_TRACE_API )
add_definitions( -DLOGGING )
add_definitions( -DTIMING )
add_definitions( -DIPC_TYPE=1 -DIPC_TYPE_${IPC_TYPE}=1 )

set(IPC_TYPE_SUBDIR ${IPC_TYPE})

if(MSVC)
  add_definitions( -D_CRT_SECURE_NO_WARNINGS )
  set(ERR_HANDLING_SUBDIR windows)
  add_definitions( -DERR_HANDLING=1 -DERR_HANDLING_windows=1 )
else()
  set(ERR_HANDLING_SUBDIR errno)
  add_definitions( -DERR_HANDLING=1 -DERR_HANDLING_errno=1 )
endif()

set(SOURCE_FILES
  src/main/cpp/utils/log.h
  src/main/cpp/utils/log.cpp
  src/main/cpp/utils/errHandling.h
  src/main/cpp/utils/errHandling.cpp
  src/main/cpp/utils/SharedMemory.h
  src/main/cpp/utils/SharedMemory.cpp
  src/main/cpp/utils/platform/SharedMemoryTypes.h
  src/main/cpp/utils/platform/errHandlingImpl.h
  )

set(SOURCE_FILES ${SOURCE_FILES}
  src/main/cpp/utils/platform/${IPC_TYPE_SUBDIR}/SharedMemoryTypes.h
  src/main/cpp/utils/platform/${IPC_TYPE_SUBDIR}/SharedMemoryImpl.cpp
  src/main/cpp/utils/platform/${ERR_HANDLING_SUBDIR}/errHandlingImpl.h
  src/main/cpp/utils/platform/${ERR_HANDLING_SUBDIR}/errHandlingImpl.cpp
  )

include_directories(src/main/cpp)

add_library(ai.kognition.pilecv4j.ipc
  SHARED
  ${SOURCE_FILES})

if(NOT MSVC)
  target_link_options(ai.kognition.pilecv4j.ipc PUBLIC -pthread)
  target_link_libraries(ai.kognition.pilecv4j.ipc
    rt
    )
else()
  target_link_libraries(ai.kognition.pilecv4j.ipc
    )
endif()

  