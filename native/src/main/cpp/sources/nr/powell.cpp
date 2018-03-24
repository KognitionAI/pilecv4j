#include <math.h>
#include "nrutil.h"

#ifdef __cplusplus
extern "C" {
#endif

#define TINY 1.0e-25 /*A small number.*/
#define ITMAX 100000 /*Maximum allowed iterations.*/
/** 
 * Minimization of a function func of n variables. Input consists of an initial starting point
 * p[1..n]; an initial matrix xi[1..n][1..n], whose columns contain the initial set of directions
 * (usually the n unit vectors); and ftol, the fractional tolerance in the function value
 * such that failure to decrease by more than this amount on one iteration signals doneness. On
 * output, p is set to the best point found, xi is the then-current direction set, fret is the returned
 * function value at p, and iter is the number of iterations taken. The routine linmin is used.
 */
void powell(float p[], float **xi, int n, float ftol, int *iter, float *fret,
            float (*func)(float*))
{
   int nrIsError(void);
   void linmin(float p[], float xi[], int n, float *fret,
               float (*func)(float []));
   int i,ibig,j;
   float del,fp,fptt,t,*pt,*ptt,*xit;
   nrIsError(); /* reset the global error condition */
   pt=vector(1,n);
   ptt=vector(1,n);
   xit=vector(1,n);
   *fret=(*func)(p);

   for (j=1;j<=n;j++) pt[j]=p[j]; /*Save the initial point.*/
   for (*iter=1;;++(*iter)) {
      fp=(*fret);
      ibig=0;
      del=0.0; /*Will be the biggest function decrease.*/
      for (i=1;i<=n;i++) { /*In each iteration, loop over all directions in the set.*/
         for (j=1;j<=n;j++) xit[j]=xi[j][i]; /*Copy the direction,*/
         fptt=(*fret);
         linmin(p,xit,n,fret,func); /*minimize along it,*/
         if (fptt-(*fret) > del) { /*and record it if it is the largest decrease*/
            del=fptt-(*fret);      /* so far. */
            ibig=i;
         }
      }
      if (2.0*(fp-(*fret)) <= ftol*(fabs(fp)+fabs(*fret))+TINY) {
         free_vector(xit,1,n); /*Termination criterion.*/
         free_vector(ptt,1,n);
         free_vector(pt,1,n);
         return;
      }
      if (*iter == ITMAX) {
        nrerror2((char*)"powell exceeding maximum iterations.");
         return;
      }

      for (j=1;j<=n;j++) { /*Construct the extrapolated point and the*/
                           /* average direction moved. Save the*/
                           /* old starting point.*/
         ptt[j]=2.0*p[j]-pt[j];
         xit[j]=p[j]-pt[j];
         pt[j]=p[j];
      }
      fptt=(*func)(ptt); /*Function value at extrapolated point.*/
      if (fptt < fp) {
         t=2.0*(fp-2.0*(*fret)+fptt)*SQR(fp-(*fret)-del)-del*SQR(fp-fptt);
         if (t < 0.0) {
            linmin(p,xit,n,fret,func); /*Move to the minimum of the new direction,*/
                                       /* and save the new direction.*/
            for (j=1;j<=n;j++) {
               xi[j][ibig]=xi[j][n];
               xi[j][n]=xit[j];
            }
         }
      }
   } /*Back for another iteration.*/
}

#define TOL 2.0e-4 /*Tolerance passed to brent.*/
int ncom; /*Global variables communicate with f1dim.*/
float *pcom,*xicom,(*nrfunc)(float []);
/**
 * Given an n-dimensional point p[1..n] and an n-dimensional direction xi[1..n], moves and
 * resets p to where the function func(p) takes on a minimum along the direction xi from p,
 * and replaces xi by the actual vector displacement that p was moved. Also returns as fret
 * the value of func at the returned location p. This is actually all accomplished by calling the
 * routines mnbrak and brent.*/
void linmin(float p[], float xi[], int n, float *fret, float (*func)(float []))
{
   float brent(float ax, float bx, float cx,
               float (*f)(float), float tol, float *xmin);
   float f1dim(float x);
   void mnbrak(float *ax, float *bx, float *cx, float *fa, float *fb,
               float *fc, float (*func)(float));
   int j;
   float xx,xmin,fx,fb,fa,bx,ax;
   ncom=n; /*Define the global variables.*/
   pcom=vector(1,n);
   xicom=vector(1,n);
   nrfunc=func;
   for (j=1;j<=n;j++) {
      pcom[j]=p[j];
      xicom[j]=xi[j];
   }
   ax=0.0; /*Initial guess for brackets.*/
   xx=1.0;
   mnbrak(&ax,&xx,&bx,&fa,&fx,&fb,f1dim);
   *fret=brent(ax,xx,bx,f1dim,TOL,&xmin);
   for (j=1;j<=n;j++) { /*Construct the vector results to return.*/
      xi[j] *= xmin;
      p[j] += xi[j];
   }
   free_vector(xicom,1,n);
   free_vector(pcom,1,n);
}

/**Must accompany linmin.*/
float f1dim(float x)
{
   int j;
   float f,*xt;
   xt=vector(1,ncom);
   for (j=1;j<=ncom;j++) xt[j]=pcom[j]+x*xicom[j];
   f=(*nrfunc)(xt);
   free_vector(xt,1,ncom);
   return f;
}

#ifdef __cplusplus
}
#endif
