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

#include <cstdint>
#include <stdio.h>
#include <list>
#include "jfloats.h"
#include "kog_exports.h"

using namespace std;

typedef int32_t (*AddHoughSpaceEntryContributorFunc)(int32_t orow, int32_t ocol,int32_t hsr, int32_t hsc, int32_t hscount);

extern "C" {
KAI_EXPORT void Transform_houghTransformNative(uint64_t imageA, int32_t width, int32_t /*height*/, uint64_t gradientDirImage,
 void* mask, int32_t maskw, int32_t maskh, int32_t maskcr, int32_t maskcc,
 void* gradientDirMask, int32_t gdmaskw, int32_t gdmaskh, int32_t gdmaskcr, int32_t gdmaskcc,
 float64_t gradientDirSlopDeg, float64_t quantFactor, int16_t* ret, int32_t hswidth, int32_t hsheight,
 AddHoughSpaceEntryContributorFunc hsem, int32_t houghThreshold, int32_t rowstart, int32_t rowend, int32_t colstart, int32_t colend,
 unsigned char EDGE);
}


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


static void sweep(int32_t orow, int32_t ocol, int32_t row, int32_t col, int16_t* houghSpace, int32_t width, int32_t height,
           unsigned char* mask, int32_t maskw, int32_t maskh, int32_t maskcr, int32_t maskcc,
           unsigned char* gradientDirMask, int32_t gdmaskw, int32_t gdmaskh, int32_t gdmaskcr, int32_t gdmaskcc,
           short gradientDirByte, short gradientDirSlopBytePM, double quantFactor, unsigned char EDGE,
           int houghThreshold = -1, short* interimHoughSpace = NULL, list<BackMapPtr>** backMapList = NULL);

static int maskcheck(unsigned char * mask, int32_t maskw, int32_t maskh, int r, int c, int isBackMapping, unsigned char EDGE);

void Transform_houghTransformNative(uint64_t imageA, int32_t width, int32_t /*height*/, uint64_t gradientDirImageA,
 void* mask, int32_t maskw, int32_t maskh, int32_t maskcr, int32_t maskcc,
 void* gradientDirMask, int32_t gdmaskw, int32_t gdmaskh, int32_t gdmaskcr, int32_t gdmaskcc,
 float64_t gradientDirSlopDeg, float64_t quantFactor, int16_t* ret, int32_t hswidth, int32_t hsheight,
 AddHoughSpaceEntryContributorFunc hsem, int32_t houghThreshold, int32_t rowstart, int32_t rowend, int32_t colstart, int32_t colend,
 unsigned char EDGE)
{
  short gradientDirSlopBytePM = (short)((1.0 + gradientDirSlopDeg * (256.0/360.0))/2.0);

  int32_t hssize = hswidth * hsheight;
  short * interimht = new short[ hssize ];
  list<BackMapPtr>** backMapListSpace = new list<BackMapPtr>*[ hssize ];

  void* image = (void*) imageA;
  void* gradientDirImage = (void*) gradientDirImageA;

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
              (unsigned char*)gradientDirMask,gdmaskw,gdmaskh,gdmaskcr,gdmaskcc,
              (short)(((unsigned char*)gradientDirImage)[pos]),
              gradientDirSlopBytePM, quantFactor, EDGE);
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
              (unsigned char*)gradientDirMask,gdmaskw,gdmaskh,gdmaskcr,gdmaskcc,
              (short)(((unsigned char*)gradientDirImage)[pos]),
              gradientDirSlopBytePM,quantFactor, EDGE,-1,interimht,backMapListSpace);
      }
    }
  }

  bool exceptionHappens = false;
  int bmlistcount = 0;
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

        if (hscount != listcount) {
          fprintf(stderr, " Problem: hough space entry %d,%d has %d backmapped entries yet %d entrie\n",
                 hsr,hsc,listcount,hscount);
          fflush(stderr);
        }

        if (listcount >= houghThreshold)
        {
          list<BackMapPtr>::const_iterator iter;
          for (iter = bmlist->begin(); iter != bmlist->end() && !exceptionHappens; iter++)
          {
            BackMap* bm = (*iter);
            exceptionHappens = !((*hsem)((int32_t)bm->orow,(int32_t)bm->ocol,(int32_t)hsr,(int32_t)hsc,(int32_t)hscount));
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
}

static void sweep(int32_t orow, int32_t ocol, int32_t row, int32_t col, int16_t* houghSpace, int32_t width, int32_t height,
           unsigned char* mask, int32_t maskw, int32_t maskh, int32_t maskcr, int32_t maskcc,
           unsigned char* gradDirMask, int32_t /*gdmaskw*/, int32_t /*gdmaskh*/, int32_t /*gdmaskcr*/, int32_t /*gdmaskcc*/,
           short gradientDirByte, short gradientDirSlopBytePM,
           double /*quantFactor*/, unsigned char EDGE, int houghThreshold,short* interimHoughSpace,
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
                      interimHoughSpace != NULL,  //  and has the side affect of smearing the transform
                      EDGE))
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


static inline int smaskcheck(unsigned char * mask, int32_t maskw, int32_t maskh, int r, int c, unsigned char EDGE)
{
  return (r >= 0 && r < maskh && c >= 0 && c < maskw) ? (mask[(r * maskw) + c] == EDGE) : 0;
}

static int maskcheck(unsigned char * mask, int32_t maskw, int32_t maskh, int r, int c, int isBackMapping, unsigned char EDGE)
{
  return isBackMapping ?
    (smaskcheck(mask,maskw,maskh,r,c,EDGE) || smaskcheck(mask,maskw,maskh,r+1,c,EDGE) ||
     smaskcheck(mask,maskw,maskh,r-1,c,EDGE) || smaskcheck(mask,maskw,maskh,r,c+1,EDGE) ||
     smaskcheck(mask,maskw,maskh,r,c-1,EDGE)) : smaskcheck(mask,maskw,maskh,r,c,EDGE);
}

