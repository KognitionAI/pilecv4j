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
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 */

#ifndef _GST_BREAKOUT_H_
#define _GST_BREAKOUT_H_
#include <stdint.h>

#include <gst/video/video.h>
#include <gst/video/gstvideofilter.h>

#include "kog_exports.h"

G_BEGIN_DECLS

#define GST_TYPE_BREAKOUT   (gst_breakout_get_type())
// cast the given instance to a GstBreakout assuring type safety
#define GST_BREAKOUT(obj)   (G_TYPE_CHECK_INSTANCE_CAST((obj),GST_TYPE_BREAKOUT,GstBreakout))
// cast the given class to a GstBreakoutClass assuring type safety
#define GST_BREAKOUT_CLASS(klass)   (G_TYPE_CHECK_CLASS_CAST((klass),GST_TYPE_BREAKOUT,GstBreakoutClass))
// determine if the instance passed is a GstBreakout
#define GST_IS_BREAKOUT(obj)   (G_TYPE_CHECK_INSTANCE_TYPE((obj),GST_TYPE_BREAKOUT))
// determine if the class passed is a GstBreakoutClass
#define GST_IS_BREAKOUT_CLASS(klass)   (G_TYPE_CHECK_CLASS_TYPE((klass),GST_TYPE_BREAKOUT))
// Get the class from the instance
#define GST_BREAKOUT_GET_CLASS(obj) \
  (G_TYPE_INSTANCE_GET_CLASS((obj), GST_TYPE_BREAKOUT, GstBreakoutClass))

typedef struct _GstBreakout GstBreakout;
typedef struct _GstBreakoutClass GstBreakoutClass;
typedef struct _GstBreakoutPrivate GstBreakoutPrivate;


struct _GstBreakout
{
  GstVideoFilter base_breakout;

  GstVideoFrame* cur;
  GstPad*        sink;

  GstBreakoutPrivate* priv;
};

struct _GstBreakoutClass
{
  GstVideoFilterClass base_breakout_class;

  /* signals */
  void          (*new_sample)        (GstBreakout *breakout);

  /* actions */
  GstSample*    (*pull_sample)       (GstBreakout *breakout);

  /* eventually private */
};

KAI_EXPORT GType gst_breakout_get_type (void);

// get data about the current frame.
KAI_EXPORT uint64_t who_am_i                            (GstBreakout* breakout);

G_END_DECLS

#endif
