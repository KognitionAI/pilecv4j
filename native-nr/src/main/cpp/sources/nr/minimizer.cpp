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
#include <stdint.h>
#include <stdlib.h>

#include "nrutil.h"
#include "jfloats.h"
#include "kog_exports.h"

namespace pilecv4j {
namespace nr {

typedef float32_t (*Func)(float32_t*, int32_t*);

extern void powell(float p[], float **xi, int n, float ftol, int *iter, float *fret,
		float (*func)(float*, void*), void* overallUd);

struct OverallUd {
  Func jfunc;
  int32_t g_status;
};

static float bridgefunc(float* v, void* udv)
{
  struct OverallUd* ud = (struct OverallUd*)udv;
  return (*(ud->jfunc))((float32_t*) (v + 1), &(ud->g_status));
}

#if defined(TRACE_ME)
static void print_vector(const char* hdr, float* v, int start, int end)
{
   printf ("%s { %f",hdr,v[start]);
   for (int i = start + 1; i <= end; i++)
      printf(", %f",v[i]);
   printf ("}\n");
}

static void print_matix(const char* hdr, float** m, int startrow, int endrow, int startcol, int endcol )
{
   printf ("%s {",hdr);
   for (int i = startrow; i <= endrow; i++)
      print_vector("    ",m[i],startcol,endcol);
   printf ("}\n");
}
#endif

extern "C" {
KAI_EXPORT float64_t pilecv4j_image_dominimize(Func func, uint32_t n, float64_t* pd, float64_t* xi, float64_t jftol, float64_t* minVal, int32_t* p_status)
{
   uint32_t pos;

   float* pf = vector(1,n);
   float** xif = matrix(1,n,1,n);
   struct OverallUd oud;

   oud.g_status=0;
   if (p_status) *p_status = 0;

   for (uint32_t i = 0; i < n; i++)
   {
      pf[i + 1] = (float)pd[i];

      pos = (i * n);
      for (uint32_t j = 0; j < n; j++)
         xif[i + 1][j + 1] = (float)xi[pos + j];
   }

   float ftol = (float)jftol;
   int iter;
   float fret;

   // set side affect globals
   oud.jfunc = func;

#if defined(TRACE_ME)
   print_vector("before p:",pf,1,n);
   print_matix("before xi:",xif,1,n,1,n);
#endif

   /// now i have xif, pf, ftol, 
   powell(pf, xif, n, ftol, &iter, &fret, bridgefunc, &oud);
   if (nrIsError() || oud.g_status != 0)
   {
     if (p_status) *p_status=1;
     return -1.0;
   }

#if defined(TRACE_ME)
   print_vector("after p:",pf,1,n);
   print_matix("after xi:",xif,1,n,1,n);
#endif
   
   // copy pf (the result) back into pd
   for (uint32_t i = 1; i <= n; i++) {
     const float64_t val = (float64_t)pf[i];
     pd[i - 1] = val;
     minVal[i - 1] = val;
   }

   // clear side affect globals

   free_matrix(xif,1,n,1,n);
   free_vector(pf,1,n);

   return (float64_t)fret;
}
}

}
}

