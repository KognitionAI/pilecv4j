cmake_minimum_required (VERSION 3.4)
project (utilities.jiminger.com)

find_package(Java REQUIRED)
find_package(JNI REQUIRED)

include(UseJava)
set(CMAKE_JAVA_COMPILE_FLAGS "-source" "1.8" "-target" "1.8")

set (_stubDir "${CMAKE_CURRENT_BINARY_DIR}")
set (CMAKE_CXX_STANDARD 11)

if(UNIX)
  add_definitions(-DUNIX)
endif()


add_custom_command(
    OUTPUT com_jiminger_image_houghspace_Transform.h
           com_jiminger_image_mjpeg_MJPEGWriter.h
           com_jiminger_image_CvRasterNative.h
    COMMAND ${Java_JAVAH_EXECUTABLE} -verbose
        -classpath ../lib/lib-image-$ENV{PROJECT_VERSION}.jar
        -d ${_stubDir}
        -jni com.jiminger.image.houghspace.Transform com.jiminger.image.mjpeg.MJPEGWriter com.jiminger.image.CvRasterNative
    )

add_custom_command(
    OUTPUT com_jiminger_nr_Minimizer.h
    COMMAND ${Java_JAVAH_EXECUTABLE} -verbose
        -classpath ../lib/lib-nr-$ENV{PROJECT_VERSION}.jar
        -d ${_stubDir}
        -jni com.jiminger.nr.Minimizer
    )

include_directories(${JNI_INCLUDE_DIRS} ${_stubDir})
add_library(utilities.jiminger.com
	MODULE com_jiminger_image_houghspace_Transform.h
               com_jiminger_nr_Minimizer.h
               com_jiminger_image_mjpeg_MJPEGWriter.h
               sources/houghspace/Transform.cpp
               sources/mjpeg/jpegtoavi.cpp
               sources/nr/Minimizer.cpp
               sources/nr/brent.cpp
               sources/nr/dbrent.cpp
               sources/nr/mnbrak.cpp
               sources/nr/nrutil.cpp
               sources/nr/powell.cpp)

target_link_libraries(utilities.jiminger.com ${JNI_LIBRARIES})
