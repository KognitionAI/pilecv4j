/***********************************************************************
    Legacy Film to DVD Project
    Copyright (C) 2005 James F. Carroll

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
****************************************************************************/

#ifndef _JPEG_TO_AVI__
#define _JPEG_TO_AVI__

#include <cstdint>
#include <cstdbool>
#include "kog_exports.h"

extern "C" {
KAI_EXPORT  bool mjpeg_initializeMJPEG(const char* filename);
KAI_EXPORT  bool mjpeg_doappendFile(const char* filename, int32_t jwidth, int32_t jheight);
KAI_EXPORT  bool mjpeg_close(int32_t jfps);
KAI_EXPORT  void mjpeg_cleanUp();
}


#endif

