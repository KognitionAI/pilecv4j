## What is this project?

This project contains several tools for creating image and video processing applications in Java. It combines [FFMpeg](https://ffmpeg.org/), [OpenCv](https://opencv.org/), [Python](https://www.python.org/) and [TensorFlow](https://www.tensorflow.org/) into an interoparable Java system.

## Table of Contents
1. [Introduction](#introduction)
1. [Prerequisites](#prerequisites)
1. [Project Overview](#project-overview)
   1. [lib-nr](#lib-nr)
   1. [lib-image](#lib-image)
   1. [lib-ffmpeg](#lib-ffmpeg)
   1. [lib-python](#lib-python)
   1. [lib-tf](#lib-tf)
   1. [lib-util](#lib-util)
   1. [lib-tracker](#lib-tf)
1. [Building](#building)
1. [History](#history)

## Introduction

The documentation for this project is in its infancy (and likely perpetually out of date).

This project will provide a basis for building video processing chains meant for operationalizing AI models. In my own projects I'm currently using various AI frameworks like [Pytorch](https://pytorch.org/) using the `lib-python` functionality. [TensorFlow](https://www.tensorflow.org/) using `lib-tf` and even [Darknet](https://pjreddie.com/darknet/) with some minimal custom native glue code.

## Prerequisites

Most dependencies will be picked up automatically from [maven central](https://www.mvnrepository.com/) but there are several to take note of.

1. [FFMpeg](https://ffmpeg.org/) which probably needs no introduction if you're on this page.
1. [Python](https://www.python.org/). In this case you'll need the development libraries installed for Python3

## Jumping right in

Here is a simple example to get started. We'll write java code that plays a video and does some minimal manupulaiton. To use the video processing include the folloing in your project (or the gradle equivalent).

``` xml
    <dependency>
      <groupId>ai.kognition.pilecv4j</groupId>
      <artifactId>lib-ffmpeg</artifactId>
      <version>0.17</version>
    </dependency>
```

You'll also need the platform specific native libraries. For linux you can use:

``` xml
    <dependency>
      <groupId>ai.kognition.pilecv4j</groupId>
      <artifactId>native-ffmpeg-${platform}</artifactId>
      <classifier>bin</classifier>
      <version>0.17</version>
    </dependency>
```

The currently supported `platform` values include: `linux-x86_64` and `windows-x86_64`

We'll need a video file to work with. If you need one you can use the `.mp4` from here: https://github.com/leandromoreira/ffmpeg-libav-tutorial/blob/master/small_bunny_1080p_60fps.mp4

For the purposes of the tutorial we'll assume there's a file at `/tmp/test-video.mp4`.

The following is a simple example that will play display the video to a window (note, there is no audio processing in the library).

``` java
// Most components are java resources (Closeables)
try(

    // We will create an ImageDisplay in order to show the frames from the video
    ImageDisplay window = new ImageDisplay.Builder().windowName("Tutorial 1").build();

    // Create a StreamContext using Ffmpeg2. StreamContexts represent
    // a source of media data and a set of processing to be done on that data.
    final StreamContext sctx = Ffmpeg2.createStreamContext()

        // Create a media data source for the StreamContext. In this case the source
        // of media data will be our file.
        .createMediaDataSource("file:///tmp/test-video.mp4")

        // We need to open a processing chain. A processing chain is a
        // grouping of a stream selector, with a series of media stream
        // processors.
        .openChain()

        // We are simply going to pick the first video stream from the file.
        .createFirstVideoStreamSelector()

        // Then we can add a processor. In this case we want the system to call us
        // with each subsequent frame as an OpenCV Mat.
        .createVideoFrameProcessor(videoFrame -> {

            // We want to display each frame. PileCV4J extends the OpenCV Mat functionality
            // for better native resource/memory management. So we can use a try-with-resource.
            try(CvMat mat = videoFrame.bgr(false);) { // Note, we want to make sure the Mat is BGR
                // Display the image.
                window.update(mat);
            }

        })

        // We need the resulting streaming context returned.
        .streamContext();

) {

    // play the media stream.
    sctx.play();
}
```

## Project Overview

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

### lib-image

`lib-image` contains the main image processing routines and OpenCv functionality. It contains some decent general purpose classes but also some more esoteric implementations that might be hard to find in other places. When using the Java API for OpenCv with video you are very likely to run into memory problems. This is because the management of OpenCv's native, off-heap (off of the Java heap management), memory is done using Java's finalizers. Garbage collection in Java doesn't track off-heap memory and so when processing video you quickly run out of system memory. This library, among many other things, augments OpenCv's Java API to use Java `Closeable`s.

### lib-ffmpeg

`lib-ffmpeg` contains video processing capability. A short list of capabilities include:

1. Connecting to streaming as well as file based video sources.
2. Decoding video frames and reciving them in your Java code as OpenCv Mat's.
3. Remuxing any input to any output.
4. Encoding raw frames back to both streaming and file andpoints

### lib-python

Because many deep learning systems use Python (e.g. [PyTorch](https://pytorch.org/)), lib-python allows you to use these frameworks from your Java code.

### lib-tf

`lib-tf` is a small and lightweight simple wrapper for Google's [Tensorflow](https://www.tensorflow.org/) that ties it into the remaining libraries here so you can easily use Tensorflow with images from [lib-image](#lib-image) and (therefore) video frames from [lib-ffmpeg](#lib-ffmpeg).

### lib-util

You probably don't need to worry about `lib-util`. `lib-util` is a library with some utilities used by the other projects here that are primarily helpers for managing access to native code bases.

### lib-tracker

This is a bit of an oddity but it's basically an abstraction for object tracking in video with several OpenCv implementations.

## 

## Building

See [Prerequisites](#prerequisites) and make sure they're all installed.

To build this you will need to have built OpenCV using [pilecv4j-opencv-packaging](https://github.com/KognitionAI/pilecv4j-opencv-packaging). The directory where [pilecv4j-opencv-packaging](https://github.com/KognitionAI/pilecv4j-opencv-packaging) installed the build should contain, in a subdirectory somewhere, the CMake configuration file, `OpenCVModules.cmake`. The exact location of this file will depend on what version of OpenCV you're building. For example, for OpenCV 4.0.0, the file is at `[install-location]/x64/vc15/staticlib` on Windows and `[install-location]/lib/cmake/opencv4` on Linux. You need to supply this location to the build via the `OpenCV_DIR` environment variable. See the example below.

On Windows, under MSYS, an example of the command line to build this looks like

```
OpenCV_DIR=/c/utils/opencv4.0.1/x64/vc15/staticlib mvn -Dgenerator="Visual Studio 15 2017" clean install
```

## History

It grew out of an old project from 2004. Details of that old project can be found at [S8](http://jiminger.com/s8/) and the still working image processing code can be found in the separate github repository [s8](https://github.com/jimfcarroll/s8).

In brief the original project was my attempt to convert my parents library of legacy super-8 mm film to DVD. I have actually got this working and have scanned several 400 foot rolls of film. See the above link for details.

