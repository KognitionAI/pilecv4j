## What is this project?

This project contains several tools for creating image and video processing applications in Java. It combines [GStreamer](https://gstreamer.freedesktop.org/), [OpenCv](https://opencv.org/), and [TensorFlow](https://www.tensorflow.org/) into an interoparable Java system.

## Introduction

The documentation for this project is in its infancy. 

## Prerequisites

Most dependencies will be picked up automatically from [maven central](https://www.mvnrepository.com/) but there are several to take note of.

1. [pilecv4j-opencv-packaging](https://github.com/KognitionAI/pilecv4j-opencv-packaging) which contains scripts for building an packaging [OpenCV](https://opencv.org/) for use with these libraries. These projects read the native shared libraries out of packaged Jar files. [pilecv4j-opencv-packaging](https://github.com/KognitionAI/pilecv4j-opencv-packaging) will build and package [OpenCV](https://opencv.org/) itself into a jar file.
1. [dempsy-commons](https://github.com/Dempsy/dempsy-commons) is normally deployed to maven central but will occasionally (like at the time of this writing) have changes required by the projects here. Currently, to build the `master` branch of this project you will also need to build the `master` branch of [dempsy-commons](https://github.com/Dempsy/dempsy-commons).
1. [gstreamer](https://gstreamer.freedesktop.org/) is a native library for creating and managing video pipelines. The *development* libraries (which require the main runtime libraries to already have been installed separately) will need to be installed on the system. Currently the native build for `gst-gstreamer` assumes gstreamer's [PkgConfig](https://en.wikipedia.org/wiki/Pkg-config) is available on the CMake [FindPkgConfig](https://cmake.org/cmake/help/v3.13/module/FindPkgConfig.html) search path.
1. On Windows you'll need to install the correct [PkgConfig](https://en.wikipedia.org/wiki/Pkg-config). Under `mingw64` bash you can install it using:
```
pacman -Su mingw-w64-x86_64-pkg-config
```
It doesn't seem to work if you install it using `pacman -Su pkg-config` as that package doesn't translate the paths correctly.

## Contents

### lib-image

`lib-image` contains the main image processing routines and OpenCv functionality. It contains some decent general purpose classes but also some more esoteric implementations that might be hard to find in other places.

1. `ai.kognition.pilecv4j.image.CvRaster` is an alternative (actually, an extention) to the OpenCv Java `Mat`. It allows for direct manipulation of the actual data contained in the `Mat` in a more convenient manner. See the Javadocs for `CvRaster` for more information.
1. `ai.kognition.pilecv4j.image.ImageFile` is a means of loading and writing images to disk. It's more robust than most implemetations since it uses `OpenCv` and falls back to `ImageIO` when that doesn't work.
1. `ai.kognition.pilecv4j.image.houghspace.Transform` is a generalized [Hough Transform](https://en.wikipedia.org/wiki/Hough_transform). It's written from scratch and doesn't use `OpenCv`'s who's implementation isn't "generalized" anyway.
1. `ai.kognition.pilecv4j.image.mjpeg.MJPEGWriter` will take a series of images and write a "Motion JPEG" movie file.
1. `ai.kognition.pilecv4j.image.TensorUtils` is a lightweight wrapper around TensorFlow. It will eventually grow to a "zero-copy" utility that makes TensorFlow much easier to use from Java. Currently it's not that extensive.

### lib-gstreamer

`lib-gstreamer` contains a set of utilities to make using Gstreamer much easier from Java. You can construct GStreamer pipelines using a builder pattern, manage resources more easily, and it also contains a `BreakoutFilter` that allows the easy processing of video frames from within Java.

### gst-breakout

`gst-breakout` is a native "C" based [GStreamer](https://gstreamer.freedesktop.org/) plugin that `lib-gstreamer` depends on in order to process video frames from Java using the `BreakoutFilter`, See the Javadocs for `BreakoutFilter` in `lib-gstreamer`.

### lib-nr

`lib-nr` is an implementation of [Powell's method](https://en.wikipedia.org/wiki/Powell%27s_method). As a simple example of how to use this library, you pass a function to be minimized to a Minimizer. Suppose I wanted to minimize the simple polynomial `(x-2)^2 - 3`. In this case, it's obvious the minimum is at `[2, -3]` but if we wanted to use the `Minimizer` to determine that we would pass the function to the minimizer and kick it off with an initial value as follows:

``` java
        final Minimizer m = new Minimizer(x -> ((x[0] - 2.0) * (x[0] - 2.0)) - 3.0);

        final double minVal = m.minimize(new double[] { -45.0 });
        final double minParam = m.getFinalPostion()[0];

        assertEquals(-3.0, minVal, 0.0001);
        assertEquals(2.0, minParam, 0.0001);
```

Powell's method is actually implemented using the algorithm from [Numerical Recipes in C](http://www.numerical.recipes/) and called using [JNA](https://github.com/java-native-access/jna). It currently isn't threadsafe but from Java there's a global lock to prevent multiple simultaneous entries. Contributions welcome.

### native

`native` is a supporting native C/C++ code for the above projects. It contains:

1. The navive code that supports `CvRaster`.
1. The [Numerical Recipes in C](http://www.numerical.recipes/) implementation of [Powell's method](https://en.wikipedia.org/wiki/Powell%27s_method) which is called from the `Minimizer` in `lib-nr`. This implementation's been modified so it can be used recursively and from multuple threads.
1. C/C++ code for writing a JPEG images into an MJPEG `avi` file written from `MJPEGWriter` in `lib-image`
1. C++ code for performing a [Hough Transform](https://en.wikipedia.org/wiki/Hough_transform) used by the `ai.kognition.pilecv4j.image.houghspace.Transform` from `lib-image`.
1. It will eventually contain native code that will support using [TensorFlow](https://www.tensorflow.org/) from Java.

### An example of it all put together

See the [TestBedTensorFlow.java](https://github.com/KognitionAI/pilecv4j/blob/master/lib-gstreamer/src/test/java/ai/kognition/pilecv4j/gstreamer/TestBedTensorFlow.java) class for an example of GStreamer, OpenCv, and TensorFlow put together in a single Java application using the libraries here.

## Building and building against these libraries.

To build this you will need to have built OpenCV using [pilecv4j-opencv-packaging](https://github.com/KognitionAI/pilecv4j-opencv-packaging). The directory where [pilecv4j-opencv-packaging](https://github.com/KognitionAI/pilecv4j-opencv-packaging) installed the build should contain, in a subdirectory somewhere, the CMake configuration file, `OpenCVModules.cmake`. The exact location of this file will depend on what version of OpenCV you're building. For example, for OpenCV 4.0.0, the file is at `[install-location]/x64/vc15/staticlib` on Windows and `[install-location]/lib/cmake/opencv4` on Linux. You need to supply this location to the build via the `OpenCV_DIR` environment variable. See the example below.

Also, you will need to have the `gstreamer` (development) installed. If it's installed in the default location on Linux, the build should pick it up automatically. On Windows you'll need to install it and point to the location of the pkg-config information.

On Windows, under MSYS, an example of the command line to build this looks like

```
OpenCV_DIR=/c/utils/opencv4.0.1/x64/vc15/staticlib PKG_CONFIG_PATH=/c/gstreamer/1.0/x86_64/lib/pkgconfig mvn -Dgenerator="Visual Studio 15 2017" clean install
```

NOTE: On Windows, since you'll be building using MSYS bash shell, you'll need to add gstreamer lib directories to your PATH environment variable since, for some reason, MSYS overrides the Windows native PATH. My entry in `.bashrc` looks like the following:

```
export PATH="$PATH":/c/gstreamer/1.0/x86_64/bin:/c/gstreamer/1.0/x86_64/lib/gstreamer-1.0
```

## History

It grew out of an old project from 2004. Details of that old project can be found at [S8](http://jiminger.com/s8/) and the still working image processing code can be found in the separate github repository [s8](https://github.com/jimfcarroll/s8).

In brief the original project was my attempt to convert my parents library of legacy super-8 mm film to DVD. I have actually got this working and have scanned several 400 foot rolls of film. See the above link for details.

