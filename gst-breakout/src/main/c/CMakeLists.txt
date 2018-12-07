cmake_minimum_required (VERSION 3.4)
find_package(PkgConfig)
pkg_check_modules(GST_VIDEO gstreamer-video-1.0 REQUIRED)
link_directories(${GST_VIDEO_LIBRARY_DIRS})


get_cmake_property(_variableNames VARIABLES)
list (SORT _variableNames)
foreach (_variableName ${_variableNames})
  message(STATUS "${_variableName}=${${_variableName}}")
endforeach()
  
add_library(gstbreakout SHARED
  gstbreakout.c
  gstbreakout-marshal.c
  )

message(STATUS "Link Directories  ${GST_VIDEO_LIBRARY_DIRS}")

get_directory_property(OUT_VAR LINK_DIRECTORIES)
message(STATUS "DIR: ${OUT_VAR}")

target_include_directories(gstbreakout PRIVATE src/main/c PUBLIC ${GST_VIDEO_INCLUDE_DIRS})
target_compile_options(gstbreakout PUBLIC ${GST_VIDEO_CFLAGS_OTHER})
target_link_libraries(gstbreakout ${GST_VIDEO_LIBRARIES})

