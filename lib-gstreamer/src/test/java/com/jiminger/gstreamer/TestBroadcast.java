package com.jiminger.gstreamer;

import java.io.File;
import java.net.URI;
import java.util.List;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;

import com.jiminger.gstreamer.util.GstUtils;

public class TestBroadcast {
   // private static final Logger LOGGER = LoggerFactory.getLogger(TestBroadcast.class);

   public static final double threshold = 0.5;
   public static final int fontHeight = 20;
   public static final double fontScale = 3;

   public static final String defaultSsdModel = "tensor/ssd_mobilenet_v1_coco_2017_11_17/frozen_inference_graph.pb";
   final static URI labelUri = new File(
         TestBedTensorFlow.class.getClassLoader().getResource("tensor/ssd_mobilenet_v1_coco_2017_11_17/mscoco_label_map.txt").getFile())
               .toURI();

   public static void main(final String[] args) throws Exception {

      Gst.init(TestBroadcast.class.getSimpleName(), args);

      // ====================================================================
      // final BinManager bb = new BinManager()
      // .make("v4l2src")
      // .make("videoconvert")
      // .caps("video/x-raw")
      // .make("vp8enc")
      // .make("fakesink");

      // rtspsrc location=rtsp://admin:811orrac8@172.16.2.51:554/ ! parsebin !
      // flvmux streamable=true ! rtmpsink sync=true async=true location=rtmp://localhost:1935/live/test
      final BinManager bb = new BinManager()
            // .add(Bin.launch(
            // "rtspsrc location=rtsp://admin:greg0rmendel@10.1.1.19:554/ ! rtph264depay ! h264parse ! capsfilter
            // caps=video/x-h264,stream-format=avc",
            // true))
            .delayed("rtspsrc").with("location", "rtsp://admin:greg0rmendel@10.1.1.19:554/")
            .make("rtph264depay")
            .make("h264parse")
            .caps("video/x-h264,stream-format=avc")
            .dynamicLink((final Element src, final Element sink) -> {
               final Pad videoSink = sink.getRequestPad("video");
               final List<Pad> pads = src.getSrcPads();
               final Pad srcPad = pads.get(0);
               srcPad.link(videoSink);
            })
            .make("flvmux").with("streamable", true)
            .delayed("decodebin")
            .make("videoconvert")
            .make("xvimagesink")

      ;

      // .make("rtmpsink").with("location", "rtmp://localhost:1935/live/test");
      // .make("decodebin").make("xvimagesink");

      final Pipeline pipe = bb.buildPipeline();
      pipe.play();
      Thread.sleep(5000);
      GstUtils.printDetails(pipe);
      Gst.main();
   }
}
