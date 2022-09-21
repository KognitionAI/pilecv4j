cmake_minimum_required (VERSION 3.4)
project (ai.kognition.pilecv4j.ffmpeg)

if (DEFINED ENV{CMAKE_BUILD_TYPE})
   set(CMAKE_BUILD_TYPE $ENV{CMAKE_BUILD_TYPE})
endif()

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

message(STATUS "Build type: ${CMAKE_BUILD_TYPE}")

if (UNIX)
  set(CMAKE_CXX_FLAGS_RELEASE "-O3")
  set(CMAKE_C_FLAGS_RELEASE "-O3")
  set(CMAKE_CXX_FLAGS_DEBUG "-g")
  set(CMAKE_C_FLAGS_DEBUG "-g")
endif()

set(CMAKE_VERBOSE_MAKEFILE ON)

# set where to find additional cmake modules if any
# comment it out if not required
set(CMAKE_MODULE_PATH ${PROJECT_SOURCE_DIR}/cmake ${CMAKE_MODULE_PATH})

# std C++-11
set(CMAKE_CXX_STANDARD 11)

if(NOT DEFINED ENV{COMMON_INCLUDE})
  message(FATAL_ERROR "You need to define the environment variable COMMON_INCLUDE to point to '[pilecv4j-root]/native-common-include/include' or build from the pom file which sets it correctly.")
endif()
include_directories($ENV{COMMON_INCLUDE})

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

#get_cmake_property(_variableNames VARIABLES)
#list (SORT _variableNames)
#foreach (_variableName ${_variableNames})
#   message(STATUS "${_variableName}=${${_variableName}}")
#endforeach()
#execute_process(COMMAND "${CMAKE_COMMAND}" "-E" "environment")

# comment this out to remove logging from the underlying code. Though it's also gated in the code so it's likely unnecessary
add_definitions( -DLOGGING -DPILECV4J_ENABLE_TRACE_API )
#add_definitions( -DLOGGING )

if(MSVC)
  add_definitions( -D_CRT_SECURE_NO_WARNINGS -D_CRT_NO_VA_START_VALIDATION )
endif()

# build target
add_library(${PROJECT_NAME} SHARED
  utils/log.cpp
  utils/pilecv4j_ffmpeg_utils.cpp
  utils/ffmpeg_codes.cpp
  utils/IMakerManager.cpp
  utils/Synchronizer.cpp
  api/StreamContext.cpp
  api/MediaDataSource.cpp
  api/MediaProcessor.cpp
  api/MediaProcessorChain.cpp
  api/StreamSelector.cpp
  api/PacketFilter.cpp
  api/StreamDetails.cpp
  api/EncodingContext.cpp
  api/MediaOutput.cpp
  sources/UriMediaDataSource.cpp
  sources/CustomMediaDataSource.cpp
  processors/DecodedFrameProcessor.cpp
  processors/Remuxer.cpp
  selectors/FirstVideoStreamSelector.cpp
  selectors/JavaStreamSelector.cpp
  filters/JavaPacketFilter.cpp
  output/CustomOutput.cpp
  output/UriOutput.cpp
  )

target_include_directories(${PROJECT_NAME} PRIVATE . PUBLIC ${AVCODEC_INCLUDE_DIR})
if (AVDEVICE_LIBRARY)
target_link_libraries(${PROJECT_NAME} ${AVCODEC_LIBRARY} ${AVUTIL_LIBRARY} ${AVFORMAT_LIBRARY} ${SWSCALE_LIBRARY} ${AVDEVICE_LIBRARY})
else()
target_link_libraries(${PROJECT_NAME} ${AVCODEC_LIBRARY} ${AVUTIL_LIBRARY} ${AVFORMAT_LIBRARY} ${SWSCALE_LIBRARY})
endif()

