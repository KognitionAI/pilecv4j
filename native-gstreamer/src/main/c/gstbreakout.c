/* GStreamer
 * Copyright (C) 2018 FIXME <fixme@example.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Suite 500,
 * Boston, MA 02110-1335, USA.
 */
/**
 * SECTION:element-gstbreakout
 *
 * The breakout element is to facilitate filtering of video frames from other language bindings.
 *
 * <refsect2>
 * <title>Example launch line</title>
 * |[
 * gst-launch-1.0 -v fakesrc ! videoconvert ! "video/x-raw,..." ! breakout ! fakesink
 * ]|
 * The pipeline will do nothing from gst-launch. See: https://github.com/jimfcarroll/utilities for
 * examples of how to use the breakout from Java.
 * </refsect2>
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <gst/gst.h>
#include <gst/video/video.h>
#include <gst/video/video-frame.h>
#include <gst/video/gstvideofilter.h>
#include "gstbreakout.h"

#include <stdio.h>
#include "gstbreakout-marshal.h"

// This is index into the callback array
enum
{
  /* signals */
  SIGNAL_TRANSFORM_IP,

  LAST_SIGNAL
};


GST_DEBUG_CATEGORY_STATIC (gst_breakout_debug_category);
#define GST_CAT_DEFAULT gst_breakout_debug_category

/* prototypes */

static void gst_breakout_set_property (GObject * object,
    guint property_id, const GValue * value, GParamSpec * pspec);
static void gst_breakout_get_property (GObject * object,
    guint property_id, GValue * value, GParamSpec * pspec);
static void gst_breakout_dispose (GObject * object);
static void gst_breakout_finalize (GObject * object);

static gboolean gst_breakout_start (GstBaseTransform * trans);
static gboolean gst_breakout_stop (GstBaseTransform * trans);
static gboolean gst_breakout_set_info (GstVideoFilter * filter, GstCaps * incaps,
    GstVideoInfo * in_info, GstCaps * outcaps, GstVideoInfo * out_info);
static GstFlowReturn gst_breakout_transform_frame_ip (GstVideoFilter * filter,
    GstVideoFrame * frame);

//static GstFlowReturn gst_breakout_prepare_output_buffer (GstBaseTransform * trans,
//    GstBuffer *input, GstBuffer **outbuf);

// exposed to java
typedef struct {
  GstBuffer* buffer;
  GstCaps* caps;
  guint32 width;
  guint32 height;
} FrameDetails;

void gst_breakout_current_frame_details(GstBreakout* breakout, FrameDetails* details);

enum
{
  PROP_0,
};

/* pad templates */

#define VIDEO_SRC_CAPS \
    GST_VIDEO_CAPS_MAKE(GST_VIDEO_FORMATS_ALL)

#define VIDEO_SINK_CAPS \
    GST_VIDEO_CAPS_MAKE(GST_VIDEO_FORMATS_ALL)

static guint gst_breakout_signals[LAST_SIGNAL] = { 0 };

/* class initialization */

G_DEFINE_TYPE_WITH_CODE (GstBreakout, gst_breakout, GST_TYPE_VIDEO_FILTER,
    GST_DEBUG_CATEGORY_INIT (gst_breakout_debug_category, "breakout", 0,
        "debug category for breakout element"));

GstSegment defaultSegment;

static void
gst_breakout_class_init (GstBreakoutClass * klass)
{
  GObjectClass *gobject_class = G_OBJECT_CLASS (klass);
  GstBaseTransformClass *base_transform_class = GST_BASE_TRANSFORM_CLASS (klass);
  GstVideoFilterClass *video_filter_class = GST_VIDEO_FILTER_CLASS (klass);

  // This gets rid of a compiler warning for an unused static generated from G_DEFINE_TYPE_WITH_CODE
  void* compile_warning_wastoid = gst_breakout_get_instance_private;

  /* Setting up pads and setting metadata should be moved to
     base_class_init if you intend to subclass this class. */
  gst_element_class_add_pad_template (GST_ELEMENT_CLASS(klass),
      gst_pad_template_new ("src", GST_PAD_SRC, GST_PAD_ALWAYS,
          gst_caps_from_string (VIDEO_SRC_CAPS)));
  gst_element_class_add_pad_template (GST_ELEMENT_CLASS(klass),
      gst_pad_template_new ("sink", GST_PAD_SINK, GST_PAD_ALWAYS,
          gst_caps_from_string (VIDEO_SINK_CAPS)));

  gst_element_class_set_static_metadata (GST_ELEMENT_CLASS(klass),
      "Breakout", "Generic", "Allow other programming languages to provide video filter functionality.",
      "Jim Carroll");

  gobject_class->set_property = gst_breakout_set_property;
  gobject_class->get_property = gst_breakout_get_property;
  gobject_class->dispose = gst_breakout_dispose;
  gobject_class->finalize = gst_breakout_finalize;

  // This shouldn't be needed when the always_in_place is TRUE
  //base_transform_class->prepare_output_buffer = gst_breakout_prepare_output_buffer;

  base_transform_class->start = GST_DEBUG_FUNCPTR (gst_breakout_start);
  base_transform_class->stop = GST_DEBUG_FUNCPTR (gst_breakout_stop);

  video_filter_class->set_info = GST_DEBUG_FUNCPTR (gst_breakout_set_info);
  // There is currently no transform_frame. All processing is done "IP"
  video_filter_class->transform_frame = NULL;
  video_filter_class->transform_frame_ip = GST_DEBUG_FUNCPTR (gst_breakout_transform_frame_ip);

  gst_breakout_signals[SIGNAL_TRANSFORM_IP] =
      g_signal_new ("new_sample", G_TYPE_FROM_CLASS (klass), G_SIGNAL_RUN_LAST,
          G_STRUCT_OFFSET (GstBreakoutClass, new_sample),
          NULL, NULL, g_cclosure_marshal_VOID__VOID, G_TYPE_NONE, 0, G_TYPE_NONE);

  gst_segment_init (&defaultSegment, GST_FORMAT_TIME);
}

static void gst_breakout_init (GstBreakout *breakout)
{
  GstIterator* it = gst_element_iterate_sink_pads(&(breakout->base_breakout.element.element));
  GstIteratorResult result = GST_ITERATOR_OK;
  GstPad* pad = NULL;
  GValue p = G_VALUE_INIT;

  breakout->cur = NULL;

  // =====================================================================
  // store off the sink pad for quick retrieval later. It's an ALWAYS pad
  // so the instance should never change.
  while (result == GST_ITERATOR_OK && pad == NULL) {
    result = gst_iterator_next(it, &p);
    pad = GST_PAD(p.data[0].v_pointer);
  }
  gst_iterator_free(it);
  if (pad != NULL) {
    breakout->sink = pad;
    gst_object_ref(breakout->sink);
  } else {
    GST_WARNING_OBJECT(breakout, "Couldn't find the sink pad.");
    breakout->sink = NULL;
  }
  // =====================================================================

  // The transform is always_in_place. This should be set because I'm only setting
  // the transform_ip and not the transform but it can't hurt.
  gst_base_transform_set_in_place(&(breakout->base_breakout.element), TRUE);
}

void
gst_breakout_set_property (GObject * object, guint property_id,
    const GValue * value, GParamSpec * pspec)
{
  GstBreakout *breakout = GST_BREAKOUT (object);
  GST_TRACE_OBJECT (breakout, "set_property");

  switch (property_id) {
  default:
    G_OBJECT_WARN_INVALID_PROPERTY_ID (object, property_id, pspec);
    break;
  }
}

void
gst_breakout_get_property (GObject * object, guint property_id,
    GValue * value, GParamSpec * pspec)
{
  GstBreakout *breakout = GST_BREAKOUT (object);
  GST_TRACE_OBJECT (breakout, "get_property");

  switch (property_id) {
  default:
    G_OBJECT_WARN_INVALID_PROPERTY_ID (object, property_id, pspec);
    break;
  }
}

void
gst_breakout_dispose (GObject * object)
{
  GstBreakout *breakout = GST_BREAKOUT (object);
  GST_TRACE_OBJECT (breakout, "dispose");

  /* clean up as possible.  may be called multiple times */
  if (breakout->sink != NULL) {
    gst_object_unref(breakout->sink);
    breakout->sink = NULL;
  }

  G_OBJECT_CLASS (gst_breakout_parent_class)->dispose (object);
}

void
gst_breakout_finalize (GObject * object)
{
  GstBreakout *breakout = GST_BREAKOUT (object);
  GST_TRACE_OBJECT (breakout, "finalize");

  /* clean up object here */

  G_OBJECT_CLASS (gst_breakout_parent_class)->finalize (object);
}

static gboolean
gst_breakout_start (GstBaseTransform * trans)
{
  GstBreakout *breakout = GST_BREAKOUT (trans);
  GST_TRACE_OBJECT (breakout, "start");

  return TRUE;
}

static gboolean
gst_breakout_stop (GstBaseTransform * trans)
{
  GstBreakout *breakout = GST_BREAKOUT (trans);
  GST_TRACE_OBJECT (breakout, "stop");

  return TRUE;
}

static gboolean
gst_breakout_set_info (GstVideoFilter * filter, GstCaps * incaps,
    GstVideoInfo * in_info, GstCaps * outcaps, GstVideoInfo * out_info)
{
  GstBreakout *breakout = GST_BREAKOUT (filter);
  GST_TRACE_OBJECT (breakout, "set_info");

  return TRUE;
}

static GstFlowReturn
gst_breakout_transform_frame_ip (GstVideoFilter * filter, GstVideoFrame * frame)
{
  GstBreakout *breakout = GST_BREAKOUT (filter);
  GST_TRACE_OBJECT (breakout, "transform_frame_ip");

  breakout->cur = frame;
  g_signal_emit (breakout, gst_breakout_signals[SIGNAL_TRANSFORM_IP], 0);
  breakout->cur = NULL;

  return GST_FLOW_OK;
}

GstBuffer *
gst_breakout_current_frame_buffer (GstBreakout * breakout)
{
  GstBuffer *buffer;
  GstVideoFrame* current;

  current = breakout->cur;
  if (current == NULL)
    goto nothing;
  buffer = current->buffer;
  GST_DEBUG_OBJECT (breakout, "we have a buffer %p with a ref count %d", buffer, (int)buffer->mini_object.refcount);
  return buffer;

  /* special conditions */
  nothing:
  {
    GST_WARNING_OBJECT (breakout, "there is no frame at this point. The method should be called from within a callback. Return NULL");
    return NULL;
  }
}

guint32 gst_breakout_current_frame_width(GstBreakout* breakout) {
  GstVideoFrame* current;

  current = breakout->cur;
  if (current == NULL)
    goto nothing;

  return GST_VIDEO_FRAME_WIDTH(current);

  nothing:
  {
    GST_WARNING_OBJECT (breakout, "there is no frame at this point. The method should be called from within a callback. Return NULL");
    return -1;
  }
}

guint32 gst_breakout_current_frame_height(GstBreakout* breakout) {
  GstVideoFrame* current;

  current = breakout->cur;
  if (current == NULL)
    goto nothing;

  return GST_VIDEO_FRAME_HEIGHT(current);

  nothing:
  {
    GST_WARNING_OBJECT (breakout, "there is no frame at this point. The method should be called from within a callback. Return NULL");
    return -1;
  }
}

GstCaps* gst_breakout_current_frame_caps(GstBreakout* breakout) {
  GstVideoFrame* current;

  current = breakout->cur;
  if (current == NULL)
    goto nothing;

  return gst_video_info_to_caps(&(current->info));

  nothing:
  {
    GST_WARNING_OBJECT (breakout, "there is no frame at this point. The method should be called from within a callback. Return NULL");
    return NULL;
  }
}

void gst_breakout_current_frame_details(GstBreakout* breakout, FrameDetails* details) {
  GstVideoFrame* current;

  current = breakout->cur;
  if (current == NULL)
    goto nothing;

  details->buffer = current->buffer;
  details->caps = gst_video_info_to_caps(&(current->info));
  details->width = GST_VIDEO_FRAME_WIDTH(current);
  details->height = GST_VIDEO_FRAME_HEIGHT(current);

  nothing:
  {
    GST_WARNING_OBJECT (breakout, "there is no frame at this point. The method should be called from within a callback. Return NULL");
  }
}



//static GstFlowReturn gst_breakout_prepare_output_buffer (GstBaseTransform * trans,
//    GstBuffer *input, GstBuffer **output) {
//  *output = gst_buffer_make_writable(input);
//  return GST_FLOW_OK;
//}

static gboolean plugin_init (GstPlugin * plugin) {
  // Since this is not meant to be used from a decodebin the rank is NONE
  return gst_element_register (plugin, "breakout", GST_RANK_NONE,
      GST_TYPE_BREAKOUT);
}

#ifndef VERSION
#define VERSION "0.0.1-SNAPSHOT"
#endif
#ifndef PACKAGE
#define PACKAGE "ai_kognition_pilecv4j_lib-gstreamer"
#endif
#ifndef PACKAGE_NAME
#define PACKAGE_NAME "Kognition.ai GStreamer lib"
#endif
#ifndef GST_PACKAGE_ORIGIN
#define GST_PACKAGE_ORIGIN "https://github.com/KognitionAI/pilecv4j/"
#endif

GST_PLUGIN_DEFINE (GST_VERSION_MAJOR,
    GST_VERSION_MINOR,
    breakout,
    "A filter that allows a callback from Java",
    plugin_init, VERSION, "LGPL", PACKAGE_NAME, GST_PACKAGE_ORIGIN)

