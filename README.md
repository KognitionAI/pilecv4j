
## What is this project?

This project contains several tools for creating image and video processing applications in Java. It combines [GStreamer](https://gstreamer.freedesktop.org/), [OpenCv](https://opencv.org/), and [TensorFlow](https://www.tensorflow.org/) into an interoparable Java system.

## Introduction

The documentation for this project is in its infancy. 

## Contents

### lib-image

`lib-image` contains the main image processing routines and OpenCv functionality. It contains some decent general purpose classes but also some more esoteric implementations that might be hard to find in other places.

1. `com.jiminger.image.CvRaster` is an alternative (actually, an extention) to the OpenCv Java `Mat`. It allows for direct manipulation of the actual data contained in the `Mat` in a more convenient manner. See the Javadocs for `CvRaster` for more information.
1. `com.jiminger.image.ImageFile` is a means of loading and writing images to disk. It's more robust than most implemetations since it uses `OpenCv` and falls back to `ImageIO` when that doesn't work.
1. `com.jiminger.image.houghspace.Transform` is a generalized [Hough Transform](https://en.wikipedia.org/wiki/Hough_transform). It's written from scratch and doesn't use `OpenCv`'s who's implementation isn't "generalized" anyway.
1. `com.jiminger.image.mjpeg.MJPEGWriter` will take a series of images and write a "Motion JPEG" movie file.
1. `com.jiminger.image.TensorUtils` is a lightweight wrapper around TensorFlow. It will eventually grow to a "zero-copy" utility that makes TensorFlow much easier to use from Java. Currently it's not that extensive.

### lib-gstreamer

`lib-gstreamer` contains a set of utilities to make using Gstreamer much easier from Java. You can construct GStreamer pipelines using a builder pattern, manage resources more easily, and it also contains a `BreakoutFilter` that allows the easy processing of video frames from within Java.

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

### An example of it all put together

See the [TestBedTensorFlow.java](https://github.com/jimfcarroll/utilities/blob/master/lib-gstreamer/src/test/java/com/jiminger/gstreamer/TestBedTensorFlow.java) class for an example of GStreamer, OpenCv, and TensorFlow put together in a single Java application using the libraries here.

## History

It grew out of an old project from 2004. Details of that old project can be found at [S8](http://jiminger.com/s8/) and the still working image processing code can be found in the sub project `s8` which will probably be moved to its own repository shortly.

In brief the original project was my attempt to convert my parents library of legacy super-8 mm film to DVD. I have actually got this working and have scanned several 400 foot rolls of film. See the above link for details.


