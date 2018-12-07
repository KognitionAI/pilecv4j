cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j)

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
endif()

message(STATUS "OpenCV Dependency version $ENV{DEP_OPENCV_VERSION}")

find_package( OpenCV CONFIG REQUIRED )
set(OPENCV_SHORT_VERSION "${OpenCV_VERSION_MAJOR}${OpenCV_VERSION_MINOR}${OpenCV_VERSION_PATCH}")

#get_cmake_property(_variableNames VARIABLES)
#list (SORT _variableNames)
#foreach (_variableName ${_variableNames})
#   message(STATUS "${_variableName}=${${_variableName}}")
#endforeach()
#execute_process(COMMAND "${CMAKE_COMMAND}" "-E" "environment")

include_directories(${OpenCV_INCLUDE_DIRS})
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

include_directories(sources/include)

if(MSVC)
  # Set compiler options.
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

  set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS ON)
endif()

add_library(ai.kognition.pilecv4j
  MODULE
  sources/opencv/CvRasterNative.cpp
  sources/houghspace/Transform.cpp
  sources/mjpeg/jpegtoavi.cpp
  sources/nr/minimizer.c
  sources/nr/brent.cpp
  sources/nr/dbrent.cpp
  sources/nr/mnbrak.cpp
  sources/nr/nrutil.cpp
  sources/nr/powell.cpp)

target_link_libraries(ai.kognition.pilecv4j
  ${OPENCV_STATIC_LIB})
