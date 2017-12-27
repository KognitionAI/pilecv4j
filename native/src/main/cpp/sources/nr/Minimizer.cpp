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
#include "nrutil.h"
#include "com_jiminger_nr_Minimizer.h"

extern "C" {
   void powell(float p[], float **xi, int n, float ftol, int *iter, float *fret,
               float (*func)(float []));

   float bridgefunc(float []);
}

void JNU_ThrowByName(JNIEnv* env, const char *name, const char *msg);

void print_vector(const char* hdr, float* v, int start, int end)
{
   printf ("%s { %f",hdr,v[start]);
   for (int i = start + 1; i <= end; i++)
      printf(", %f",v[i]);
   printf ("}\n");
}

void print_matix(const char* hdr, float** m, int startrow, int endrow, int startcol, int endcol )
{
   printf ("%s {",hdr);
   for (int i = startrow; i <= endrow; i++)
      print_vector("    ",m[i],startcol,endcol);
   printf ("}\n");
}

static jobject gjfunc;
static JNIEnv* genv;
static jclass gFuncClass;
static jmethodID gFuncMeth;
static jsize start;
static jsize end;
static jdoubleArray gjvArray;
static jboolean exceptionHappens;

JNIEXPORT jdouble JNICALL Java_com_jiminger_nr_Minimizer_dominimize
  (JNIEnv * env, jclass, jobject jfunc, jdoubleArray p, jobjectArray xi, jdouble jftol, jdoubleArray minVal)
{

   int n = (int)env->GetArrayLength(p);
   jdouble* pd = new jdouble[n];
   jdouble* xirowjd = new jdouble[n];

   env->GetDoubleArrayRegion(p,0,(jsize)n,pd);

   float* pf = vector(1,n);
   float** xif = matrix(1,n,1,n);

   for (int i = 0; i < n; i++)
   {
      pf[i + 1] = (float)pd[i];

      jdoubleArray tmpDoubleArray = 
         (jdoubleArray)env->GetObjectArrayElement(xi,i);

      env->GetDoubleArrayRegion(tmpDoubleArray,0,n,xirowjd);

      for (int j = 0; j < n; j++)
         xif[i + 1][j + 1] = (float)xirowjd[j];
   }

   float ftol = (float)jftol;
   int iter;
   float fret;

   // set side affect globals
   gjfunc = jfunc;
   genv = env;
   gFuncClass = env->GetObjectClass(jfunc);
   gFuncMeth = env->GetMethodID(gFuncClass,"func","([D)D");
   start = 1;
   end = n;
   gjvArray = genv->NewDoubleArray(n);
   exceptionHappens = false;

   /// now i have xif, pf, ftol, 
   powell(pf, xif, n, ftol, &iter, &fret, bridgefunc);
   if (nrIsError())
   {
      JNU_ThrowByName(env,"com/jiminger/nr/MinimizerException",nrGetErrorMessage());
      return -1.0;
   }

//   print_vector("p:",pf,1,n);
//   print_matix("xi:",xif,1,n,1,n);

   // copy pf (the result) back into pd
   for (int i = 1; i <= n; i++)
      pd[i - 1] = (jdouble)pf[i];
   // now set minVal for the return
   env->SetDoubleArrayRegion(minVal,0,n,pd);

   // clear side affect globals
   env->DeleteLocalRef(gjvArray);
   gjvArray = NULL;
   gjfunc = NULL;
   genv = NULL;
   gFuncClass = NULL;
   gFuncMeth = NULL;
   start=0;
   end=0;

   free_matrix(xif,1,n,1,n);
   free_vector(pf,1,n);
   delete [] xirowjd;
   delete [] pd;

   return (jdouble)fret;
}

float bridgefunc(float v[])
{
   if (exceptionHappens)
      return 0.0f;

   jsize len = end - start + 1;
   jdouble* tmp = new jdouble[len];
   for (int i = start; i <= end; i++)
      tmp[i - start] = (jdouble)v[i];
   genv->SetDoubleArrayRegion(gjvArray,0,len,tmp);
   jdouble ret = genv->CallDoubleMethod(gjfunc,gFuncMeth,gjvArray);
   if (genv->ExceptionOccurred() != NULL)
      exceptionHappens = true;

   delete [] tmp;
   return (float)ret;
}

void JNU_ThrowByName(JNIEnv* env, const char *name, const char *msg)
{
   jclass cls = env->FindClass(name);
   if (cls != NULL)
      env->ThrowNew(cls,msg);
   env->DeleteLocalRef(cls);
}

