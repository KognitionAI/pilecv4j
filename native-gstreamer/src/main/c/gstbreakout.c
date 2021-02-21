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
#include <stdint.h>
#include <stdio.h>
#include "gstbreakout-marshal.h"

// this is in the cpp module gstmat.cpp since I could put it there
uint64_t currentTimeNanos();

struct _GstBreakoutPrivate {
  int64_t        maxDelayMillis;

  // min/max TimeDifference is to keep track of the outliers
  // which will be removed from the average.
  int64_t minTimeDifference;
  int64_t maxTimeDifference;

  int64_t cumulativeTimeDifferences;
  int64_t timeDifference;
  uint64_t numTimeMeasurements;
  uint64_t numFramesWaitingForValidPts;

  uint8_t calcMode;
};

// This is index into the callback array
enum
{
  /* signals */
  SIGNAL_TRANSFORM_IP,

  LAST_SIGNAL
};

#define BREAKOUT_DEFAULT_MAX_DELAY_MILLIS -1
#define BREAKOUT_DEFAULT_NUM_MEASUREMENTS_FOR_AVERAGE 10
#define BREAKOUT_NUM_MEASUREMENTS_BEFORE_WARNING 50
#define NANOS_PER_MILLI 1000000L

enum {
  PROP_0,
  PROP_MAX_DELAY_MILLIS
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

// exposed to java
typedef struct {
  GstBuffer* buffer;
  GstCaps* caps;
  guint32 width;
  guint32 height;
} FrameDetails;

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

  g_object_class_install_property (gobject_class, PROP_MAX_DELAY_MILLIS,
     g_param_spec_int64("maxDelayMillis", "maxDelayMillis",
               "Maximum frame delay in milliseconds before filter will skip all processing to get caught up",
               -1, G_MAXINT64, BREAKOUT_DEFAULT_MAX_DELAY_MILLIS, G_PARAM_READWRITE | G_PARAM_STATIC_STRINGS));

  gst_segment_init (&defaultSegment, GST_FORMAT_TIME);
}

static void gst_breakout_init (GstBreakout *breakout)
{
  GstIterator* it = gst_element_iterate_sink_pads(&(breakout->base_breakout.element.element));
  GstIteratorResult result = GST_ITERATOR_OK;
  GstPad* pad = NULL;
  GValue p = G_VALUE_INIT;

  breakout->cur = NULL;
  breakout->priv = NULL;

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

static void makePrivateParts(GstBreakout* breakout, int64_t maxDelayMillis) {
  GST_OBJECT_LOCK(breakout);
  if (!breakout->priv) {
    GstBreakoutPrivate* priv = malloc(sizeof(GstBreakoutPrivate));
    priv->maxDelayMillis = maxDelayMillis;
    priv->cumulativeTimeDifferences = 0L;
    priv->numTimeMeasurements = 0L;
    priv->numFramesWaitingForValidPts = 0L;
    priv->timeDifference = 0L;
    priv->minTimeDifference = 0L;
    priv->maxTimeDifference = 0L;
    priv->calcMode = TRUE;
    breakout->priv = priv;
  }
  GST_OBJECT_UNLOCK(breakout);
}
void
gst_breakout_set_property (GObject * object, guint property_id,
    const GValue * value, GParamSpec * pspec)
{
  GstBreakout *breakout = GST_BREAKOUT (object);
  GST_TRACE_OBJECT (breakout, "set_property");

  switch (property_id) {
  case PROP_MAX_DELAY_MILLIS: {
    makePrivateParts(breakout, g_value_get_int64(value));
    break;
  }
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
  case PROP_MAX_DELAY_MILLIS:
    if (breakout->priv)
      g_value_set_int64(value, breakout->priv->maxDelayMillis);
    else
      g_value_set_int64(value, BREAKOUT_DEFAULT_MAX_DELAY_MILLIS);
    break;
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

  if (breakout->priv) {
    GST_OBJECT_LOCK(breakout);
    free(breakout->priv);
    breakout->priv = NULL;
    GST_OBJECT_UNLOCK(breakout);
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

static const uint64_t ONES = 0xFFFFFFFFFFFFFFFF;

static gboolean isValidPts(uint64_t pts) {
  return !((pts == ONES) || (pts == (uint64_t)0));
}

static GstFlowReturn
gst_breakout_transform_frame_ip (GstVideoFilter * filter, GstVideoFrame * frame)
{
  GstBreakout *breakout = GST_BREAKOUT (filter);
  GST_TRACE_OBJECT (breakout, "transform_frame_ip");

  GstBreakoutPrivate * priv = breakout->priv;
  if (priv) {
    const int64_t maxDelayMillis = breakout->priv->maxDelayMillis;

    // are we limiting the latency?
    if (maxDelayMillis >= 0) {
      // check to see if we're getting PTS
      uint64_t pts = (uint64_t) (frame->buffer->pts);
      if (isValidPts(pts)) {
        priv->numFramesWaitingForValidPts = 0L; // reset in case we got a few bad ones.

        uint64_t curTime = currentTimeNanos();
        const int64_t diff = curTime - (int64_t) (pts);

        // are we still gathering metrics to record the clock shift?
        if (priv->calcMode) {
          GST_OBJECT_LOCK(breakout);
          if (priv->numTimeMeasurements == 0)
            priv->minTimeDifference = priv->maxTimeDifference = diff;
          else {
            if (diff < priv->minTimeDifference)
              priv->minTimeDifference = diff;
            if (diff > priv->maxTimeDifference)
              priv->maxTimeDifference = diff;
          }

          priv->numTimeMeasurements++;
          priv->cumulativeTimeDifferences += diff;

          // if we have enough samples then just take the current average.
          //                                                                       the +2 is for the outliers
          if (priv->numTimeMeasurements >= (BREAKOUT_DEFAULT_NUM_MEASUREMENTS_FOR_AVERAGE + 2)) {
            // adjust for the min and max outliers
            priv->cumulativeTimeDifferences -= priv->minTimeDifference;
            priv->cumulativeTimeDifferences -= priv->maxTimeDifference;

            //                                                                                   -2 is for the outliers
            priv->timeDifference = (int64_t)( (double)(priv->cumulativeTimeDifferences) / (double) (priv->numTimeMeasurements - 2L));
            priv->timeDifference += (priv->maxDelayMillis * NANOS_PER_MILLI);
            priv->calcMode = FALSE;
          }
          GST_OBJECT_UNLOCK(breakout);
        } else {
          // we have the time shift we're going to assume is basically no delay.
          if (diff > priv->timeDifference) {
            GST_DEBUG_OBJECT(breakout, "Dropping a frame due to latency");
            return GST_FLOW_OK;
          }
        }
      } else { // invalid pts. Track it. If it happens for too long we need to issue a warning
        priv->numFramesWaitingForValidPts++;
        if (priv->numFramesWaitingForValidPts > BREAKOUT_NUM_MEASUREMENTS_BEFORE_WARNING) {
          GST_WARNING_OBJECT( breakout, "expected valid PTS but haven't gotten one.");
          priv->numFramesWaitingForValidPts = 0L; // reset.
        }
      }
    }
  }
  breakout->cur = frame;
  g_signal_emit (breakout, gst_breakout_signals[SIGNAL_TRANSFORM_IP], 0);
  breakout->cur = NULL;

  return GST_FLOW_OK;
}

uint64_t who_am_i(GstBreakout* breakout) {
  return (uint64_t)breakout;
}

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

