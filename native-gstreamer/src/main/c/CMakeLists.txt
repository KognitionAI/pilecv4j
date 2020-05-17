cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j.gstreamer)

find_package(PkgConfig)

set (CMAKE_CXX_STANDARD 11)

pkg_check_modules(GST_VIDEO gstreamer-video-1.0 REQUIRED)

#####################################################
# Adding opencv
message(STATUS "OpenCV Dependency version $ENV{DEP_OPENCV_VERSION}")
find_package( OpenCV CONFIG REQUIRED )
set(OPENCV_SHORT_VERSION "${OpenCV_VERSION_MAJOR}${OpenCV_VERSION_MINOR}${OpenCV_VERSION_PATCH}")

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
#####################################################

link_directories(${GST_VIDEO_LIBRARY_DIRS})

add_library(gstbreakout SHARED
  gstbreakout.c
  gstmat.cpp
  gstbreakout-marshal.c
  )

target_include_directories(gstbreakout PRIVATE src/main/c PUBLIC ${GST_VIDEO_INCLUDE_DIRS})
target_compile_options(gstbreakout PUBLIC ${GST_VIDEO_CFLAGS_OTHER})
target_link_libraries(gstbreakout ${GST_VIDEO_LIBRARIES}
                                  ${OPENCV_STATIC_LIB})

