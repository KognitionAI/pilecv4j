
##############################################
# Set the build type
##############################################
if (DEFINED ENV{CMAKE_BUILD_TYPE})
   set(CMAKE_BUILD_TYPE $ENV{CMAKE_BUILD_TYPE})
endif()

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")
##############################################

if (CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
  # buffer overrun protection. Disallow data on the stack from being executed.
  set (CMAKE_MODULE_LINKER_FLAGS "-Wl,-z,noexecstack")

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

set(CMAKE_INSTALL_PREFIX ${PROJECT_SOURCE_DIR})
set(CMAKE_CXX_STANDARD 17)

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

include_directories(${COMMON_INCLUDE})

if(DEFINED REQUIRE_OPENCV_COMPILE OR DEFINED REQUIRE_OPENCV_LINK)
  if (DEFINED ENV{OPENCV_INSTALL})
    message(STATUS "OPENCV_INSTALL is defined as $ENV{OPENCV_INSTALL}")
    set (OpenCV_DIR "$ENV{OPENCV_INSTALL}/share/OpenCV")
  endif()

  find_package(OpenCV CONFIG REQUIRED)

  if(DEFINED REQUIRE_OPENCV_COMPILE)
    include_directories(${OpenCV_INCLUDE_DIRS})
  endif()

  if(DEFINED REQUIRE_OPENCV_LINK)
    message(STATUS "OpenCV Dependency version $ENV{DEP_OPENCV_VERSION}")
    if (NOT DEFINED ENV{DEP_OPENCV_VERSION})
      message(FATAL_ERROR "You must define DEP_OPENCV_VERSION to link against pilecv4j's opencv build.")
    endif()

    set(OPENCV_SHORT_VERSION "${OpenCV_VERSION_MAJOR}${OpenCV_VERSION_MINOR}${OpenCV_VERSION_PATCH}")

    set(OPENCV_STATIC_LIB opencv_java${OPENCV_SHORT_VERSION})
    if(EXISTS "${OpenCV_INSTALL_PATH}/share/java/opencv4/lib${OPENCV_STATIC_LIB}.so" )
      # OpenCV 4 Linux
      link_directories("${OpenCV_INSTALL_PATH}/share/java/opencv4")
    elseif (EXISTS "${OpenCV_INSTALL_PATH}/share/OpenCV/java/" )
      # OpenCV 3 Linux
      link_directories("${OpenCV_INSTALL_PATH}/share/OpenCV/java")
    elseif (EXISTS "${OpenCV_INSTALL_PATH}/java/${OPENCV_STATIC_LIB}.dll" )
      # OpenCV 3&4 Windows
      link_directories("${OpenCV_INSTALL_PATH}/java" "${OpenCV_DIR}")
      set(OPENCV_STATIC_LIB ${OPENCV_STATIC_LIB} "opencv_highgui${OPENCV_SHORT_VERSION}")
    else()
      message(FATAL_ERROR "Can't determine the directory where the library ${OPENCV_STATIC_LIB} is on this platform.")
    endif()

    if ( NOT "${OpenCV_VERSION}" STREQUAL "$ENV{DEP_OPENCV_VERSION}")
      message(FATAL_ERROR "OpenCV Dependency version ($ENV{DEP_OPENCV_VERSION}) is different from the version being built against (${OpenCV_VERSION}).")
    endif()
  endif()
endif()

#############
#get_cmake_property(_variableNames VARIABLES)
#list (SORT _variableNames)
#foreach (_variableName ${_variableNames})
#  message(STATUS "${_variableName}=${${_variableName}}")
#endforeach()
#
#execute_process(COMMAND "${CMAKE_COMMAND}" "-E" "environment")
#############

