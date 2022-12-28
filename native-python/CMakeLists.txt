cmake_minimum_required (VERSION 3.16)
project (pilecv4jpython)

# if you're going to set the build type here, do it before include CMakeHelpers.cmake
#set(CMAKE_BUILD_TYPE Debug)

# set this before include CMakeHelpers.cmake
set(REQUIRE_OPENCV_COMPILE ON)
if(MSVC)
  set(REQUIRE_OPENCV_LINK ON)
endif()

# make sure the commons dir is available
if(NOT DEFINED COMMON_INCLUDE)
  if(DEFINED ENV{COMMON_INCLUDE})
    set(COMMON_INCLUDE $ENV{COMMON_INCLUDE})
  else()
    set(COMMON_INCLUDE ${PROJECT_SOURCE_DIR}/../native-common-include/include)
  endif()
endif()

INCLUDE(${COMMON_INCLUDE}/../CMakeHelpers.cmake)

find_package(Python3 COMPONENTS Interpreter Development NumPy REQUIRED)

include_directories(src/main/cpp)

add_library(pilecv4jpython SHARED
  src/main/cpp/log.cpp
  src/main/cpp/RunPythonFunction.cpp
  src/main/cpp/PythonEnvironment.cpp
  src/main/cpp/KogSystem.cpp
  src/main/cpp/ImageSource.cpp
  src/main/cpp/module.cpp
  src/main/cpp/kognition.cpp
  )

target_include_directories(pilecv4jpython PRIVATE src/main/cpp
  PUBLIC ${Python3_INCLUDE_DIRS}
  PUBLIC ${Python3_NumPy_INCLUDE_DIRS}
  PUBLIC ${OpenCV_INCLUDE_DIRS}
)

message(STATUS "Python 3 libraries: ${Python3_LIBRARIES}")

target_link_libraries(pilecv4jpython
   ${Python3_LIBRARIES}
   ${OPENCV_STATIC_LIB}
)

