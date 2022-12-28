cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j.util)

# make sure the commons dir is available
if(NOT DEFINED COMMON_INCLUDE)
  if(DEFINED ENV{COMMON_INCLUDE})
    set(COMMON_INCLUDE $ENV{COMMON_INCLUDE})
  else()
    set(COMMON_INCLUDE ${PROJECT_SOURCE_DIR}/../native-common-include/include)
  endif()
endif()

INCLUDE(${COMMON_INCLUDE}/../CMakeHelpers.cmake)

include_directories(src/main/cpp)

add_library(ai.kognition.pilecv4j.util
  MODULE
  src/main/cpp/nr/minimizer.cpp
  src/main/cpp/nr/brent.cpp
  src/main/cpp/nr/dbrent.cpp
  src/main/cpp/nr/mnbrak.cpp
  src/main/cpp/nr/nrutil.cpp
  src/main/cpp/nr/powell.cpp)

target_link_libraries(ai.kognition.pilecv4j.util
  ${OPENCV_STATIC_LIB})
