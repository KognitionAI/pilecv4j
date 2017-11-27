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
#include "com_jiminger_image_houghspace_Transform.h"
#include <list>
using namespace std;

//public short [] houghTransformNative(int width, int height, byte [] image, byte [] gradientDirImage,
//                                     byte [] mask, byte [] gradientDirImage, double gradientDirSlopDeg);

static unsigned char EDGE = -1;
static bool EDGE_set = false;

struct BackMap
{
  BackMap(int orow, int ocol)
  {
    this->orow = orow;
    this->ocol = ocol;
  }

  int orow;
  int ocol;
};

typedef BackMap* BackMapPtr;


void sweep(jint orow, jint ocol, jint row, jint col, jshort* houghSpace, jint width, jint height,
           unsigned char* mask, jint maskw, jint maskh, jint maskcr, jint maskcc,
           short* gradientDirMask, jint gdmaskw, jint gdmaskh, jint gdmaskcr, jint gdmaskcc,
           short gradientDirByte, short gradientDirSlopBytePM, double quantFactor,
           int houghThreshold = -1, short* interimHoughSpace = NULL, list<BackMapPtr>** backMapList = NULL);

int maskcheck(unsigned char * mask, jint maskw, jint maskh, int r, int c, int isBackMapping);


JNIEXPORT void JNICALL Java_com_jiminger_image_houghspace_Transform_houghTransformNative
(JNIEnv * env, jobject /*thethis*/, 
 jbyteArray imageA, jint width, jint /*height*/, jbyteArray gradientDirImageA,
 jbyteArray maskA, jint maskw, jint maskh, jint maskcr, jint maskcc,
 jshortArray gradientDirMaskA, jint gdmaskw, jint gdmaskh, jint gdmaskcr, jint gdmaskcc,
 jdouble gradientDirSlopDeg, jdouble quantFactor, jshortArray retA, jint hswidth, jint hsheight,
 jobject hsem, jint houghThreshold, jint rowstart, jint rowend, jint colstart, jint colend)
{
  if (! EDGE_set) {
    jclass maskClass = env->FindClass("com/jiminger/image/houghspace/internal/Mask");
    jfieldID fid = env->GetStaticFieldID(maskClass, "EDGE", "B");
    jbyte val = env->GetStaticByteField(maskClass, fid);
    EDGE = (unsigned char)val;
    EDGE_set = true;
  }
  
  short gradientDirSlopBytePM = (short)((1.0 + gradientDirSlopDeg * (256.0/360.0))/2.0);

  jboolean isCopy;
  jbyte* image = 
    (jbyte*)(env->GetPrimitiveArrayCritical(imageA, &isCopy));
  jbyte* gradientDirImage = 
    (jbyte*)(env->GetPrimitiveArrayCritical(gradientDirImageA, &isCopy));
  jbyte* mask = 
    (jbyte*)(env->GetPrimitiveArrayCritical(maskA, &isCopy));
  jshort* gradientDirMask = 
    (jshort*)(env->GetPrimitiveArrayCritical(gradientDirMaskA, &isCopy));
  jshort* ret = 
    (jshort*)(env->GetPrimitiveArrayCritical(retA, &isCopy));

  jint hssize = hswidth * hsheight;
  short * interimht = new short[ hssize ];
  list<BackMapPtr>** backMapListSpace = new list<BackMapPtr>*[ hssize ];

  for ( int i = 0; i < hssize; i++)
  {
    interimht[i] = ret[i] = 0;
    backMapListSpace[i] = NULL;
  }

  int pos;

  // This loop will fill in the interimht array with the houghspace calculation
  // results by sweeping the mask through every position over the original edge
  // image. Each position in interimht will then contain a count of how many edge
  // locations in the original edge image indicate a possible center at that
  // (interimht) position.
  for (int r = rowstart; r <= rowend; r++)
  {
    pos = (r * width) + colstart;
    for (int c = colstart; c <= colend; c++, pos++)
    {
      unsigned char v = ((unsigned char*)image)[pos];
      if (v == EDGE)
      {
        int hsrow = (int)(((double)r)/quantFactor);
        int hscol = (int)(((double)c)/quantFactor);

        // Calling sweep to accumulate "votes" into interim ht with the mask centered at
        // r,c. Remember, the mask contains NON-ZERO whereever the center of the model
        // can be if the center of the mask in placed over an edge in the original image.
        sweep(r,c,hsrow,hscol,interimht,hswidth,hsheight,
              (unsigned char*)mask,maskw,maskh,maskcr,maskcc,
              (short*)gradientDirMask,gdmaskw,gdmaskh,gdmaskcr,gdmaskcc,
              (short)(((unsigned char*)gradientDirImage)[pos]),
              gradientDirSlopBytePM, quantFactor);
      }
    }
  }

  // now 'back map' the current ht
  for (int r = rowstart; r <= rowend; r++)
  {
    pos = (r * width) + colstart;
    for (int c = colstart; c <= colend; c++, pos++)
    {
      unsigned char v = ((unsigned char*)image)[pos];
      if (v == EDGE)
      {
        int hsrow = (int)(((double)r)/quantFactor);
        int hscol = (int)(((double)c)/quantFactor);

        sweep(r,c,hsrow,hscol,ret,hswidth,hsheight,
              (unsigned char*)mask,maskw, maskh, maskcr, maskcc,
              (short*)gradientDirMask,gdmaskw,gdmaskh,gdmaskcr,gdmaskcc,
              (short)(((unsigned char*)gradientDirImage)[pos]),
              gradientDirSlopBytePM,quantFactor,-1,interimht,backMapListSpace);
      }
    }
  }

  jclass gFuncClass;
  jmethodID gFuncMeth;
  gFuncClass = env->GetObjectClass(hsem);
  gFuncMeth = env->GetMethodID(gFuncClass,"addHoughSpaceEntryContributor","(IIIII)V");

  jboolean exceptionHappens = false;

  for (int hsr = 0; hsr < hsheight && !exceptionHappens; hsr++)
  {
    for (int hsc = 0; hsc < hswidth && !exceptionHappens; hsc++)
    {
      int hsindex = (hsr * hswidth) + hsc;
      list<BackMapPtr>* bmlist = backMapListSpace[hsindex];

      if (bmlist)
      {
        // do a consistency check
        int listcount = bmlist->size();
        int hscount = (int)ret[hsindex];

        if (hscount != listcount)
          printf(" Problem: hough space entry %d,%d has %d backmapped entries yet %d entrie\n",
                 hsr,hsc,listcount,hscount);

        if (listcount >= houghThreshold)
        {
          list<BackMapPtr>::const_iterator iter;
          for (iter = bmlist->begin(); iter != bmlist->end() && !exceptionHappens; iter++)
          {
            BackMap* bm = (*iter);
            env->CallVoidMethod(hsem,gFuncMeth,(jint)bm->orow,(jint)bm->ocol,(jint)hsr,(jint)hsc,(jint)hscount);
            if (env->ExceptionOccurred() != NULL)
            {
              printf("Exception Happens!\n");
              exceptionHappens = true;
            }
          }
        }
      }
    }
  }

  // clean up the space
  for ( int i = 0; i < hssize; i++)
  {
    list<BackMapPtr>* bmlist = backMapListSpace[i];
    if (bmlist)
    {
      //free all of the entries
      list<BackMapPtr>::const_iterator iter;
      for (iter = bmlist->begin(); iter != bmlist->end(); iter++)
      {
        BackMap* bm = (*iter);
        delete bm;
      }

      delete bmlist;
    }
  }

  delete [] backMapListSpace;
  delete [] interimht;
   

  env->ReleasePrimitiveArrayCritical(gradientDirMaskA,gradientDirMask,0);
  env->ReleasePrimitiveArrayCritical(maskA,mask,0);
  env->ReleasePrimitiveArrayCritical(gradientDirImageA,gradientDirImage,0);
  env->ReleasePrimitiveArrayCritical(imageA,image,0);
  env->ReleasePrimitiveArrayCritical(retA,ret,0);
}

void sweep(jint orow, jint ocol, jint row, jint col, jshort* houghSpace, jint width, jint height,
           unsigned char* mask, jint maskw, jint maskh, jint maskcr, jint maskcc,
           short* gradDirMask, jint /*gdmaskw*/, jint /*gdmaskh*/, jint /*gdmaskcr*/, jint /*gdmaskcc*/,
           short gradientDirByte, short gradientDirSlopBytePM,
           double /*quantFactor*/,int houghThreshold,short* interimHoughSpace, 
           list<BackMapPtr>** backMapListSpace)
{
  int maxbincount = -1;
  int maxbinpos = -1;
  int tmpi;

  // the mask is already swept so we need to simply 
  //  plop it on the houghSpace centered at r,c
  for (int r = 0; r < maskh; r++)
  {
    int hsr = (row - maskcr) + r;
    if (hsr >= 0 && hsr < height)
    {
      for (int c = 0; c < maskw; c++)
      {
        int hsc = (col - maskcc) + c;
        if (hsc >= 0 && hsc < width &&
            maskcheck(mask,maskw,maskh,r,c,       // this mask check checks adjacent pixels also
                      interimHoughSpace != NULL)) //  and has the side affect of smearing the transform
        {
          short requiredGradDir = gradDirMask[(r * maskw) + c];
          short diff = requiredGradDir > gradientDirByte ? 
            (short)(requiredGradDir - gradientDirByte) : 
            (short)(gradientDirByte - requiredGradDir);

          if (gradientDirSlopBytePM >= diff || 
              gradientDirSlopBytePM >= ((short)256 - diff))
          {
            if (interimHoughSpace)
            {
              tmpi = (hsr * width) + hsc; // tmpi has the index into the hough space in question
              if (maxbincount < interimHoughSpace[tmpi])
              {
                maxbinpos = tmpi;
                maxbincount = interimHoughSpace[maxbinpos];
              }
            }
            else
              if (houghSpace)
                houghSpace[(hsr * width) + hsc]++;
          }
        }
      }
    }
  }

  if (interimHoughSpace)
  {
    if (maxbinpos >= 0 && (maxbincount > houghThreshold || houghThreshold <= 0))
    {
      if (houghSpace)
        houghSpace[maxbinpos]++;

      if (backMapListSpace)
      {
        list<BackMapPtr>* bmlist = backMapListSpace[maxbinpos];

        if (bmlist == NULL)
        {
          bmlist = new list<BackMapPtr>;
          backMapListSpace[maxbinpos] = bmlist;
        }

        bmlist->push_back(new BackMap(orow,ocol));
      }
    }
  }
}


inline int smaskcheck(unsigned char * mask, jint maskw, jint maskh, int r, int c)
{
  return (r >= 0 && r < maskh && c >= 0 && c < maskw) ? (mask[(r * maskw) + c] == EDGE) : 0;
}

int maskcheck(unsigned char * mask, jint maskw, jint maskh, int r, int c, int isBackMapping)
{
  return isBackMapping ? 
    (smaskcheck(mask,maskw,maskh,r,c) || smaskcheck(mask,maskw,maskh,r+1,c) ||
     smaskcheck(mask,maskw,maskh,r-1,c) || smaskcheck(mask,maskw,maskh,r,c+1) ||
     smaskcheck(mask,maskw,maskh,r,c-1)) : smaskcheck(mask,maskw,maskh,r,c);
}

