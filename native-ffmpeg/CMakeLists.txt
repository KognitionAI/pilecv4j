cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j.ffmpeg)

# if you're going to set the build type here, do it before include CMakeHelpers.cmake
#set(CMAKE_BUILD_TYPE Debug)

# set this before include CMakeHelpers.cmake
set(REQUIRE_OPENCV_COMPILE ON)

# make sure the commons dir is available
if(NOT DEFINED COMMON_INCLUDE)
  if(DEFINED ENV{COMMON_INCLUDE})
    set(COMMON_INCLUDE $ENV{COMMON_INCLUDE})
  else()
    set(COMMON_INCLUDE ${PROJECT_SOURCE_DIR}/../native-common-include/include)
  endif()
endif()

INCLUDE(${COMMON_INCLUDE}/../CMakeHelpers.cmake)

find_path( AVCODEC_INCLUDE_DIR libavcodec/avcodec.h REQUIRED )
message(STATUS "avcodec.h location: ${AVCODEC_INCLUDE_DIR}")
find_library( AVCODEC_LIBRARY avcodec REQUIRED )
message(STATUS "avcodec lib: ${AVCODEC_LIBRARY}")
find_library( AVUTIL_LIBRARY avutil REQUIRED )
message(STATUS "avutil lib: ${AVUTIL_LIBRARY}")
find_library( AVFORMAT_LIBRARY avformat REQUIRED )
message(STATUS "avformat lib: ${AVFORMAT_LIBRARY}")
find_library( SWSCALE_LIBRARY swscale REQUIRED )
message(STATUS "swscale lib: ${SWSCALE_LIBRARY}")

find_library( AVDEVICE_LIBRARY avdevice)
message(STATUS "avdevice lib: ${AVDEVICE_LIBRARY}")

if (AVDEVICE_LIBRARY)
  message(STATUS "Including devices such as web cams")
  add_definitions( -DPILECV4J_FFMPEG_DEVICE_SUPPORT )
endif()

# comment this out to remove logging from the underlying code. Though it's also gated in the code so it's likely unnecessary
add_definitions( -DPILECV4J_ENABLE_TRACE_API )
add_definitions( -DLOGGING )
add_definitions( -DTIMING )

if(MSVC)
  add_definitions( -D_CRT_SECURE_NO_WARNINGS -D_CRT_NO_VA_START_VALIDATION )
endif()

include_directories(src/main/cpp)

# build target
add_library(${PROJECT_NAME} SHARED
  src/main/cpp/utils/log.cpp
  src/main/cpp/utils/pilecv4j_ffmpeg_utils.cpp
  src/main/cpp/utils/ffmpeg_codes.cpp
  src/main/cpp/utils/IMakerManager.cpp
  src/main/cpp/utils/Synchronizer.cpp
  src/main/cpp/api/MediaContext.cpp
  src/main/cpp/api/MediaDataSource.cpp
  src/main/cpp/api/MediaProcessor.cpp
  src/main/cpp/api/MediaProcessorChain.cpp
  src/main/cpp/api/PacketFilter.cpp
  src/main/cpp/api/StreamDetails.cpp
  src/main/cpp/api/EncodingContext.cpp
  src/main/cpp/api/Muxer.cpp
  src/main/cpp/sources/UriMediaDataSource.cpp
  src/main/cpp/sources/CustomMediaDataSource.cpp
  src/main/cpp/processors/DecodedFrameProcessor.cpp
  src/main/cpp/processors/Remuxer.cpp
  src/main/cpp/filters/FirstVideoStreamSelector.cpp
  src/main/cpp/filters/JavaStreamSelector.cpp
  src/main/cpp/filters/JavaPacketFilter.cpp
  src/main/cpp/muxers/DefaultMuxer.cpp
  src/main/cpp/muxers/SegmentedMuxer.cpp
  )

target_include_directories(${PROJECT_NAME} PRIVATE . PUBLIC ${AVCODEC_INCLUDE_DIR})
if (AVDEVICE_LIBRARY)
  target_link_libraries(${PROJECT_NAME}
    ${AVCODEC_LIBRARY}
    ${AVUTIL_LIBRARY}
    ${AVFORMAT_LIBRARY}
    ${SWSCALE_LIBRARY}
    ${AVDEVICE_LIBRARY})
else()
  target_link_libraries(${PROJECT_NAME}
    ${AVCODEC_LIBRARY}
    ${AVUTIL_LIBRARY}
    ${AVFORMAT_LIBRARY}
    ${SWSCALE_LIBRARY})
endif()

