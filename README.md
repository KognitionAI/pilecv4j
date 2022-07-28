# What is this project?

This project contains several tools for creating image and video processing applications in Java. It combines [FFMpeg](https://ffmpeg.org/), [OpenCv](https://opencv.org/), [Python](https://www.python.org/) and [TensorFlow](https://www.tensorflow.org/) into an interoperable Java system.

## Table of Contents
- [What is this project?](#what-is-this-project)
  - [Table of Contents](#table-of-contents)
- [Introduction](#introduction)
- [Prerequisites](#prerequisites)
- [Jumping Right In](#jumping-right-in)
  - [Quick Start Video Tutorial](#quick-start-video-tutorial)
  - [Short Example](#short-example)
- [Project Overview](#project-overview)
  - [lib-image](#lib-image)
  - [lib-ffmpeg](#lib-ffmpeg)
  - [lib-python](#lib-python)
  - [lib-tf](#lib-tf)
  - [lib-nr](#lib-nr)
  - [lib-util](#lib-util)
  - [lib-tracker](#lib-tracker)
- [Image Processing](#image-processing)
  - [OpenCv Mat Resource Management](#opencv-mat-resource-management)
  - [Direct Bulk Access To Pixel Data From Java](#direct-bulk-access-to-pixel-data-from-java)
- [Video Processing](#video-processing)
  - [Reading And Processing Media](#reading-and-processing-media)
    - [The Stream Context](#the-stream-context)
    - [Defining The Source of Media Data](#defining-the-source-of-media-data)
    - [Adding Stream Processing](#adding-stream-processing)
  - [Encoding Video](#encoding-video)
    - [Defining The Destination For The Encoded Streams](#defining-the-destination-for-the-encoded-streams)
    - [Adding A Specific Codec (VideoEncoder)](#adding-a-specific-codec-videoencoder)
- [Building](#building)
- [History](#history)

# Introduction

The documentation for this project is in its infancy (and likely perpetually out of date).

This project will provide a basis for building video processing chains meant for operationalizing AI models. In my own projects I'm currently using various AI frameworks like [Pytorch](https://pytorch.org/) using the `lib-python` functionality. [TensorFlow](https://www.tensorflow.org/) using `lib-tf` and even [Darknet](https://pjreddie.com/darknet/) with some minimal custom native glue code.

# Prerequisites

Most dependencies will be picked up automatically from [maven central](https://www.mvnrepository.com/) but there are several to take note of.

1. [FFMpeg](https://ffmpeg.org/) which probably needs no introduction if you're on this page.
1. [Python](https://www.python.org/). In this case you'll need the development libraries installed for Python3

*Note:* You shouldn't need [OpenCV](https://opencv.org/) installed since there is a pre-built [OpenCV](https://opencv.org/) bundled in a jar file that will be automatically picked up as a dependency.

# Jumping Right In

## Quick Start Video Tutorial

A video Quick Start Tutorial can be found here:

[![PileCV4J Quick Start Tutorial Video](https://img.youtube.com/vi/8mfDk9NLp6w/0.jpg)](https://www.youtube.com/watch?v=8mfDk9NLp6w)

## Short Example

Here is a simple example to get started. We'll write java code that plays a video and does some minimal manipulation. To use the video processing include the following in your project (or the gradle equivalent).

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

The following is a simple example that will play display the video to a window (note, there is no audio processing in the library).

For the purposes of this example we'll assume there's a video file at `/tmp/test-video.mp4`.


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
        .openChain("my chain")

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

# Project Overview

## lib-image

`lib-image` contains the main image processing routines and OpenCv functionality. It contains some decent general purpose classes but also some more esoteric implementations that might be hard to find in other places. When using the Java API for OpenCv with video you are very likely to run into memory problems. This is because the management of OpenCv's native, off-heap (off of the Java heap management), memory is done using Java's finalizers. Garbage collection in Java doesn't track off-heap memory and so when processing video you quickly run out of system memory. This library, among many other things, augments OpenCv's Java API to use Java `Closeable`s.

## lib-ffmpeg

`lib-ffmpeg` contains video processing capability. A short list of capabilities include:

1. Connecting to streaming as well as file based video sources.
2. Decoding video frames and receiving them in your Java code as OpenCv Mat's.
3. Remuxing any input to any output.
4. Encoding raw frames back to both streaming and file endpoints

## lib-python

Because many deep learning systems use Python (e.g. [PyTorch](https://pytorch.org/)), lib-python allows you to use these frameworks from your Java code.

## lib-tf

`lib-tf` is a small and lightweight simple wrapper for Google's [Tensorflow](https://www.tensorflow.org/) that ties it into the remaining libraries here so you can easily use Tensorflow with images from [lib-image](#lib-image) and (therefore) video frames from [lib-ffmpeg](#lib-ffmpeg).

## lib-nr

`lib-nr` is an implementation of [Powell's method](https://en.wikipedia.org/wiki/Powell%27s_method). As a simple example of how to use this library, you pass a function to be minimized to a Minimizer. Suppose I wanted to minimize the simple polynomial `(x-2)^2 - 3`. In this case, it's obvious the minimum is at `[2, -3]` but if we wanted to use the `Minimizer` to determine that we would pass the function to the minimizer and kick it off with an initial value as follows:

``` java
        final Minimizer m = new Minimizer(x -> ((x[0] - 2.0) * (x[0] - 2.0)) - 3.0);

        final double minVal = m.minimize(new double[] { -45.0 });
        final double minParam = m.getFinalPostion()[0];

        assertEquals(-3.0, minVal, 0.0001);
        assertEquals(2.0, minParam, 0.0001);
```

Powell's method is actually implemented using the algorithm from [Numerical Recipes in C](http://www.numerical.recipes/) and called using [JNA](https://github.com/java-native-access/jna). It currently isn't thread safe but from Java there's a global lock to prevent multiple simultaneous entries. Contributions welcome.

## lib-util

You probably don't need to worry about `lib-util`. `lib-util` is a library with some utilities used by the other projects here that are primarily helpers for managing access to native code bases.

## lib-tracker

This is a bit of an oddity but it's basically an abstraction for object tracking in video with several OpenCv implementations.

# Image Processing

In OpenCV's C/C++ API, the developer is responsible for managing the resources. The Mat class in C++ references the underlying memory resources for the image data. When a C++ Mat is deleted, memory is freed (that is, as long as other Mat's aren't referring to the same image data memory, in which case when the last one is deleted, the memory is freed). This gives the developer using the C++ API fine grained control over the compute resources.

However, for Java developers, it's not typical for the developer to manage memory or explicitly delete objects or free resources. Instead, they typically rely on garbage collection or *try-with-resource*. The problem with doing that in OpenCV's Java API is that the Java VM and it's garbage collector *can't see* the image memory referred to by the Mat. This memory is ["off-heap"](https://dev.to/jeissonflorez29/off-heap-memory-in-java-4dd1) from the perspective of the Java VM.

`lib-image` is an augmentation for [OpenCv](https://opencv.org/) Java API. It's primary purpose is to allow the user to handle [OpenCv](https://opencv.org/) [Mat](https://docs.opencv.org/4.5.3/d3/d63/classcv_1_1Mat.html)s as Java resources.

If you're familiar with the OpenCv Java API, then the examples in this section will be easy to follow.

## OpenCv Mat Resource Management

As an example of using OpenCv Mat's as Java resource (ie `Closeable`s), here is an example of loading an image from disk using OpenCV's api:
``` java
// A CvMat is an OpenCV Mat.
try(final CvMat mat = CvMat.move(Imgcodecs.imread(IMAGE_FILE_NAME, Imgcodecs.IMREAD_UNCHANGED));) {
    // Do something with the loaded mat. For example, flip it around the X axis:
    Core.flip(mat, mat, 0);
} // <- At this point all of the mat resources will be freed. It wont wait for the garbage collector and finalizer.
```
A `CvMat` is a Pilecv4j class that inherits from OpenCv's `Mat` so it can be used directly in any OpenCv API method. 

The `CvMat.move()` method conceptually follows the [C++ move semantics](https://www.cprogramming.com/c++11/rvalue-references-and-move-semantics-in-c++11.html). In short this mean DO NOT try to use the instance that was passed to `CvMat.move()` after the call.

What if I want to return a CvMat from inside of a *try-with-resource* block? You can use the `CvMat.returnMe()` method. This method should ONLY be used on the very line you're actually returning the `CvMat`. 
``` java
public CvMat getFlippedMat(final String filename) {
    try(final CvMat mat = CvMat.move(Imgcodecs.imread(IMAGE_FILE_NAME, Imgcodecs.IMREAD_UNCHANGED));) {
        // Do something with the loaded mat. For example, flip it around the X axis:
        Core.flip(mat, mat, 0);
        return mat.returnMe();
    } // <- At this point all of the mat resources will be freed. It wont wait for the garbage collector and finalizer.
}
```

## Direct Bulk Access To Pixel Data From Java

Pilecv4j also provides a means of accessing a `Mat`'s pixel data directly as a Java `ByteBuffer`. You do this with the `CvRaster` class.

**Note:** doing pixel operations from Java is not efficient. You should look to apply OpenCv operations to your data instead.

The cleanest way work with the raw `ByteBuffer` of pixel data is to use the `CvMat` functions `rasterAp` and `rasterOp`. Each take a callback. The only difference is that `rasterOp` can return a value while `rasterAp` returns `void`. Here's an example:

``` java
    CvMat mat = ....;
    mat.rasterAp(raster -> {
        ByteBuffer pixelData = raster->underlying();
        // do something with/to the pixel data
        ...
    });
```
# Video Processing

## Reading And Processing Media

### The Stream Context

To process a source of media data you construct a `StreamContext`, declare the source of media data, and define a set of processing chains to process the media.

A `StreamContext` represents the coupling of an input source to a set of processing on the media streams in that source. It's also a builder for declaring the media source and that processing to be done.
<p align="center">Fig 1.</p>
<p align="center"><img src="https://raw.githubusercontent.com/KognitionAI/pilecv4j/master/docs/Stream%20Context.png" width="500"></p>

A `StreamContext` is a Java *resource* so we should manage it using a *try-with-resource*

``` java
    try (final StreamContext sctx = Ffmpeg2.createStreamContext()
    ...
```

### Defining The Source of Media Data

There are two supported types of media data sources.

The first is a simple URI based data source which is defined through `StreamContext.createMediaDataSource(String)`. This is the same as the `-i` option passed to the `ffmpeg` command line.

``` java
    try (final StreamContext sctx = Ffmpeg2.createStreamContext()
        .createMediaDataSource("[media uri]")
        ...
```
The second type of media data source is a custom data source, where you can supply raw media stream data dynamically by supplying a `MediaDataSupplier` callback implementation and optionally a `MediaDataSeek` callback implementation. These will be called by the system in order to fetch more data or, when a `MediaDataSeek` is supplied, move around the current position in the stream.

``` java
    try (final StreamContext sctx = Ffmpeg2.createStreamContext()
        .createMediaDataSource( (ByteBuffer buf, int numBytes) -> {
            // you need to respond to this callback by filling the byte buffer with *up to*
            // the `numBytes` requested
            ...
            but.put(/* put data in the byte buffer */...);
            return numOfBytesAddedToBuffer;
        })
        ...

```
If the underlying source of data is seekable, you can optionally supply an implementation of the `MediaDataSeek` callback.

``` java
    try (final StreamContext sctx = Ffmpeg2.createStreamContext()
        .createMediaDataSource( 
            (ByteBuffer buf, int numBytes) -> {...},
            
            (final long offset, final int whence)) -> {
                /* move the current position in the data stream */
                /*    to the location implied in the parameters */
                ...
            })
        ...
```

The values for `whence` can be `Ffmpeg2.SEEK_SET`, `Ffmpeg2.SEEK_CUR`, or `Ffmpeg2.SEEK_END`. These values are synonymous with the C language stdio parameters to the function [fseek()](https://man7.org/linux/man-pages/man3/fseek.3.html). From the man page:
> The [...] function sets the [...] position indicator for the
> stream [...].  The new position, measured in bytes, is obtained
> by adding offset bytes to the position specified by whence.
> If whence is set to SEEK_SET, SEEK_CUR, or SEEK_END, the
> offset is relative to the start of the file, the current
> position indicator, or end-of-file, respectively.

### Adding Stream Processing

To process media data you need to add at least one `MediaProcessingChain` using the `openChain(String)` on the `StreamContext`. 

``` java
    try (final StreamContext sctx = Ffmpeg2.createStreamContext()
        .createMediaDataSource("[media uri]")
        .openChain("default")
        ...
```
The string passed to `openChain` can be anything you wand and is simply for disambiguation later on. You can re-open any given chain by passing the same string to the `openChain` call again.

A `MediaProcessingChain` contains at most one `StreamSelector` and one or more `MediaProcessor`s. 


Sources for media data, for example, an MP4 file, contain multiple streams of media data. Video streams, audio streams, subtitle streams, and data streams. A `StreamSelector` sets up a simple filter that determines which streams are going to be processed in this `MediaProcessingChain`. A `StreamSelector` is added to a `MediaProcessingChain` by calling one of the `create*StreamSelector(...)` methods.

The simplest stream selector currently available is one that will simply take the first decodable video stream in the source. To use this selector in your chain you call `createFirstVideoStreamSelector()` on an open chain:

``` java
    try (final StreamContext sctx = Ffmpeg2.createStreamContext()
        .createMediaDataSource("[media uri]")
        .openChain("default")
        .createFirstVideoStreamSelector()
        ...
```
You can supply a more sophisticated selection criteria by supplying a callback that will be passed the details of each stream and needs to return `true` if the stream is to be processed; `false` otherwise.

``` java
    try (final StreamContext sctx = Ffmpeg2.createStreamContext()
        .createMediaDataSource("[media uri]")
        .openChain("default")
        .createStreamSelector((StreamDetails[] details, boolean[] selection) -> {
           // use the stream details and fill out the selection array with `true`
           // where you want to process the coresponding stream
        })
        ...
```

Finally we need to decide what we want to do with the media data that's been selected in this chain. We do that by adding a set of (at least one) `MediaProcessor`s to the chain. There are currently two different processor types that can be added to a chain. 

The first is a remuxer that will remux the selected input stream to a given output destination.

``` java
    try (final StreamContext sctx = Ffmpeg2.createStreamContext()
        .createMediaDataSource("[media uri]")
        .openChain("default")
        .createFirstVideoStreamSelector()
        .createUriRemuxer(DESTINATION_URI)
        ...
```
where the `DESTINATION_URI` is a string pointing to any output supported by ffmpeg including a file or a stream. When pointing to a stream you can explicitly define the format to use. For example:

``` java
       ...
       .createUriRemuxer("flv", "rtmp://myhost:1935/live...")
       ...
```
The second type of `MediaProcessor` provides access to video frames from the selected video feeds. You supply a callback that will be given `VideoFrame`s.

``` java
    try (final StreamContext sctx = Ffmpeg2.createStreamContext()
        .createMediaDataSource("[media uri]")
        .openChain("default")
        .createFirstVideoStreamSelector()
        .createVideoFrameProcessor((VideoFrame frame) -> {
           // do something with the video frame
        })
        ...
```

A `VideoFrame` is a `CvMat` and so is also an OpenCV `Mat` and therefore can be used in any OpenCV Java API that supports Mat's. It's also coupled with a flag that tells if the color format of the data in the Mat is BGR, which is the OpenCV standard, or RGB, which is pretty much everyone else's standard. The underlying media source does the least amount of conversion necessary but will prefer RGB over BGR because most non-OpenCV (most deep learning models for example) prefer that format. However, there are a few convenience methods for handling this:
``` java
    ...
    .createVideoFrameProcessor((VideoFrame frame) -> {
        try (CvMat bgrMat = frame.bgr(false);) {
            // do something with the BGR mat
        }
    })
    ...
```

Once the chain is defined with at most one `StreamSelector` and at least one `MediaProcessor`, you need to return the original `StreamContext` which effectively closes the chain's setup.

``` java
    try (final StreamContext sctx = Ffmpeg2.createStreamContext()
        .createMediaDataSource("[media uri]")
        .openChain("default")
        .createFirstVideoStreamSelector()
        .createVideoFrameProcessor((VideoFrame frame) -> { ... })
        .streamContext()
        ....); {
    }
```

You can define additional chains if you want.

Ultimately you'll need to start the processing by calling `play()` on the configured `StreamContext`. The [Short Example](#short-example) puts it all together.

## Encoding Video

### Defining The Destination For The Encoded Streams

You can also you the library to encode videos and video streams. The first thing you need to do is define an `EncodingContext`. As with the `StreamContext` the `EncodingContext` is a Java resource and therefore should be managed with a *try-with-resource*

``` java
    try (final EncodingContext ectx = Ffmpeg2.createEncoder()
    ...
```

First you need to define where the media data is going. You do this using the `outputStream` method.

``` java
    try (final EncodingContext ectx = Ffmpeg2.createEncoder()
        .outputStream(OUTPUT_FILE_OR_STREAM_URI)
        ...
```

You can also explicitly define the format. This is often necessary for streaming output like sending the encoded data to an rtmp server. As an example:

``` java
    try (final EncodingContext ectx = Ffmpeg2.createEncoder()
        .outputStream("flv", "rtmp://myhost:1935/live...")
        ...
```

### Adding A Specific Codec (VideoEncoder)

You need to select at least one encoder to add to the context. You can do that with the method `openVideoEncoder` as follows:

``` java
    try (final EncodingContext ectx = Ffmpeg2.createEncoder()
        .outputStream(OUTPUT_FILE_OR_STREAM_URI)
        .openVideoEncoder("libx264", "my encoder")
        ...
```
The first parameter is the name of an FFMpeg support encoder. The second parameter is an arbitrary name to distinguish multiple encoders. There's a convenience method that takes just one parameter and set the name and codec to the same value.
``` java
        ...
        .openVideoEncoder("libx264")
        ...
```
You can fine tune the parameters for the codec using `addCodecOption`. You can add as many options as you want. These options are often codec specific and documented in the Ffmpeg documentation.
``` java
    try (final EncodingContext ectx = Ffmpeg2.createEncoder()
        .outputStream(OUTPUT_FILE_OR_STREAM_URI)
        .openVideoEncoder("libx264")
        .addCodecOptions("preset","slow")
        .addCodecOptions("crf", "40")
        ...
```
You should set the encoder's frame rate explicitly using `setFps`. If you don't it will default to 30.
``` java
    try (final EncodingContext ectx = Ffmpeg2.createEncoder()
        .outputStream(OUTPUT_FILE_OR_STREAM_URI)
        .openVideoEncoder("libx264")
        .setFps(10)
        ...
```
You need to initialize (`enable`) the encoder with the appropriate details of what the encoder will see and should produce. This should be done AFTER all of the `addCodecOptions` are applied. Once the encoder is "enabled" it will fail if you try to add more options.

Enabling the encoder applies the options and sets up the transform necessary to convert the input to the output. The main `enable` call takes the following:
``` java
enable(final boolean isRgb, final int inputImageWidth, final int inputImageHeight, final int inputImageStride,
       final int destWidth, final int destHeight)
```
- isRgb - this should be true if the input `Mat`s will be RGB. If BGR, it should be false., 
- inputImageWidth - the width of the input images
- inputImageHeight - the height of the input images
- inputImageStride - the *stride* (the number of bytes in a row including the padding) of the input images.
- destWidth - the width of the encoded video's frames
- destHeight - the height of the encoded video's frames

There are several convenience methods that take a `Mat` and will assume the input and output dimensions are the same. The `enable` with the shortest signature is:
``` java
enable(final Mat mat, final boolean isRgb)
```
This call will assume the mat is a frame from the input and use those dimensions. It will also assume that the input and the output are the same. There are several other overloaded `enable` methods. See the javadocs for more details.

Once each of the `VideoEncoder`s are *enabled*, the `EncodingContext` can be told using the `ready()` method.

After that you can start sending frames to the encoder using the `encode()` method.

``` java
    Mat firstFrame = ...;
    try (final EncodingContext ectx = Ffmpeg2.createEncoder()
        .outputStream(OUTPUT_FILE_OR_STREAM_URI)
        .openVideoEncoder("libx264", "my encoder")
        .addCodecOptions("preset","slow")
        .addCodecOptions("crf", "40")
        .setFps(10)
        .enable(firstFrame, false);)
    {
        VideoEncoder encoder = ectx.getVideoEncoder("my encoder");
        ectx.read();

        for (int i = 0; i < numberOfFrames; i++) {
            Mat frameToEncode = getImageToEncode(i);
            encoder.encode(frameToEncode);
        }
    }
```

# Building

See [Prerequisites](#prerequisites) and make sure they're all installed.

To build this you will need to have built OpenCV using [pilecv4j-opencv-packaging](https://github.com/KognitionAI/pilecv4j-opencv-packaging). The directory where [pilecv4j-opencv-packaging](https://github.com/KognitionAI/pilecv4j-opencv-packaging) installed the build should contain, in a subdirectory somewhere, the CMake configuration file, `OpenCVModules.cmake`. The exact location of this file will depend on what version of OpenCV you're building. For example, for OpenCV 4.0.0, the file is at `[install-location]/x64/vc15/staticlib` on Windows and `[install-location]/lib/cmake/opencv4` on Linux. You need to supply this location to the build via the `OpenCV_DIR` environment variable. See the example below.

On Windows, under MSYS, an example of the command line to build this looks like

```
OpenCV_DIR=/c/utils/opencv4.0.1/x64/vc15/staticlib mvn -Dgenerator="Visual Studio 15 2017" clean install
```

# History

It grew out of an old project from 2004. Details of that old project can be found at [S8](http://jiminger.com/s8/) and the still working image processing code can be found in the separate github repository [s8](https://github.com/jimfcarroll/s8).

In brief the original project was my attempt to convert my parents library of legacy super-8 mm film to DVD. I have actually got this working and have scanned several 400 foot rolls of film. See the above link for details.

