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


#include <stdio.h>
#include <math.h>
#include "com_jiminger_image_canny_EdgeDetectorOpImage.h"

extern "C" {
   void canny(unsigned char *image, int rows, int cols, float sigma,
              float tlow, float thigh, unsigned char *edge,
              unsigned char* gradientDirImage
//              float* gradientDirImage
              );

   extern unsigned char NOEDGE;
   extern unsigned char POSSIBLE_EDGE;
   extern unsigned char EDGE;
}


JNIEXPORT void JNICALL Java_com_jiminger_image_canny_EdgeDetectorOpImage_canny
(JNIEnv * env, jobject /*this*/, jint destwidth, jint destheight, 
 jbyteArray dest, jint /*destOffset*/, jint /*destPixelStride*/, jint /*destScanlineStride*/,
 jbyteArray src, jint /*srcOffset*/, jint /*srcPixelStride*/, jint /*srcScanLineStride*/,
 jfloat lowThreshold, jfloat highThreshold, jfloat sigma, jbyte noedgeval,
 jbyte edgeval, jbyte possibleEdge,
 jbyteArray gradDirImData
// jfloatArray gradDirImData
 )
{
//   printf(" destwidth = %ld\n", destwidth);
//   printf(" destheight = %ld\n", destheight);
//   printf(" destOffset = %ld\n",destOffset);
//   printf(" destPixelStride = %ld\n", destPixelStride);
//   printf(" destScanlineStride = %ld\n", destScanlineStride);
//   printf(" srcOffset = %ld\n",srcOffset);
//   printf(" srcPixelStride = %ld\n", srcPixelStride);
//   printf(" srcScanLineStride = %ld\n", srcScanLineStride);

   jboolean isCopy;
   jbyte* srcData = 
      (jbyte*)(env->GetPrimitiveArrayCritical(src, &isCopy));

   jbyte* destData = 
      (jbyte*)(env->GetPrimitiveArrayCritical(dest, &isCopy));

   jbyte* gradDirIm =
      (gradDirImData != NULL) ? 
      (jbyte*)(env->GetPrimitiveArrayCritical(gradDirImData, &isCopy)) :
      NULL;

//   jfloat* gradDirIm =
//      (gradDirImData != NULL) ? 
//      (jfloat*)(env->GetPrimitiveArrayCritical(gradDirImData, &isCopy)) :
//      NULL;

   NOEDGE = (unsigned char)noedgeval;
   EDGE = (unsigned char)edgeval;
   POSSIBLE_EDGE = (unsigned char)possibleEdge;

   canny((unsigned char*)srcData, destheight, destwidth, (float)sigma,
         (float)lowThreshold, (float)highThreshold, 
         (unsigned char*)destData, (unsigned char*)gradDirIm);


   if (gradDirIm != NULL)
      env->ReleasePrimitiveArrayCritical(gradDirImData,gradDirIm,0);

   env->ReleasePrimitiveArrayCritical(dest,destData,0);
   env->ReleasePrimitiveArrayCritical(src,srcData,0);

}

