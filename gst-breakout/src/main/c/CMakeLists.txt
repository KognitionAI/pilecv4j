cmake_minimum_required (VERSION 3.4)
find_package(PkgConfig)
pkg_check_modules(GST_VIDEO gstreamer-video-1.0 REQUIRED)

add_library(gstbreakout-1.0 SHARED
  gstbreakout.c
  gstbreakout-marshal.c
  )

target_include_directories(gstbreakout-1.0 PRIVATE src/main/c PUBLIC ${GST_VIDEO_INCLUDE_DIRS})
target_compile_options(gstbreakout-1.0 PUBLIC ${GST_VIDEO_CFLAGS_OTHER})
target_link_libraries(gstbreakout-1.0 ${GST_VIDEO_LIBRARIES})

