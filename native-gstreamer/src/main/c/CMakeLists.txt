cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j.gstreamer)

############################################
# checks and initial confs

if(NOT DEFINED ENV{COMMON_INCLUDE})
  message(FATAL_ERROR "You need to define the environment variable COMMON_INCLUDE to point to '[pilecv4j-root]/native-common-include/include' or build from the pom file which sets it correctly.")
endif()

if (DEFINED ENV{CMAKE_BUILD_TYPE})
   set(CMAKE_BUILD_TYPE $ENV{CMAKE_BUILD_TYPE})
endif()

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
############################################
# flags

if(UNIX)
  set(CMAKE_CXX_FLAGS_RELEASE "-O3")
  set(CMAKE_C_FLAGS_RELEASE "-O3")
  set(CMAKE_CXX_FLAGS_DEBUG "-g")
  set(CMAKE_C_FLAGS_DEBUG "-g")
endif()

set(CMAKE_VERBOSE_MAKEFILE ON)
set(CMAKE_CXX_STANDARD 11)

############################################
# dependencies

find_package(PkgConfig)

pkg_check_modules(GST_VIDEO gstreamer-video-1.0 REQUIRED)

############################################
# Fix dependencies

if (MSVC)
  if (DEFINED ENV{GST_PREFIX})
    message(STATUS "GST_PREFIX defined so assuming the gstreamer install pkg-config is screwed because the developers of gstreamer are morons.")

    macro(gst_prefix_fix macro_var)
      string(REGEX REPLACE "c:/projects/repos/cerbero.git/1.[0-9]+/build/dist" "$ENV{GST_PREFIX}/1.0" ${macro_var} "${${macro_var}}")
    endmacro()

    gst_prefix_fix(GST_VIDEO_LIBRARY_DIRS)
    gst_prefix_fix(GST_VIDEO_INCLUDE_DIRS)
  endif()

  # pkg-config creates msys toolchain paths rather than windows paths so we need
  # to fix a few of them explicitly
  macro(msys_to_cmake_path macro_var)
    string(REGEX REPLACE "^/([a-zA-Z])/" "\\1:/" ${macro_var} "${${macro_var}}")
    string(REGEX REPLACE ";/([a-zA-Z])/" ";\\1:/" ${macro_var} "${${macro_var}}")
  endmacro()

  msys_to_cmake_path(GST_VIDEO_LIBRARY_DIRS)
  msys_to_cmake_path(GST_VIDEO_INCLUDE_DIRS)
endif()

############################################
# dump details for debugging
get_cmake_property(_variableNames VARIABLES)
list (SORT _variableNames)
foreach (_variableName ${_variableNames})
   message(STATUS "${_variableName}=${${_variableName}}")
endforeach()
execute_process(COMMAND "${CMAKE_COMMAND}" "-E" "environment")

############################################
# build config

include_directories($ENV{COMMON_INCLUDE})

link_directories(${GST_VIDEO_LIBRARY_DIRS})

add_library(gstbreakout SHARED
  gstbreakout.c
  gstmat.cpp
  )

target_include_directories(gstbreakout PRIVATE src/main/c PUBLIC ${GST_VIDEO_INCLUDE_DIRS})
target_compile_options(gstbreakout PUBLIC ${GST_VIDEO_CFLAGS_OTHER})
target_link_libraries(gstbreakout ${GST_VIDEO_LIBRARIES})

