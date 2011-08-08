#include <math.h>
#include "nrutil.h"
#ifdef __cplusplus
extern "C" {
#endif

#define ITMAX 100
#define ZEPS 1.0e-10
#define MOV3(a,b,c, d,e,f) (a)=(d);(b)=(e);(c)=(f);
float dbrent(float ax, float bx, float cx, float (*f)(float),
             float (*df)(float), float tol, float *xmin)
/**Given a function f and its derivative function df, and given a bracketing triplet of abscissas ax,
   bx, cx [such that bx is between ax and cx, and f(bx) is less than both f(ax) and f(cx)],
   this routine isolates the minimum to a fractional precision of about tol using a modi.cation of
   Brent’s method that uses derivatives. The abscissa of the minimum is returned as xmin, and
   the minimum function value is returned as dbrent, the returned function value.*/
{
   int iter,ok1,ok2; /*Will be used as .ags for whether proposed*/
                     /*steps are acceptable or not. */
   float a,b,d,d1,d2,du,dv,dw,dx,e=0.0;
   float fu,fv,fw,fx,olde,tol1,tol2,u,u1,u2,v,w,x,xm;
   /*Comments following will point out only di.erences from the routine brent. Read that
     routine .rst.*/
   a=(ax < cx ? ax : cx);
   b=(ax > cx ? ax : cx);
   x=w=v=bx;
   fw=fv=fx=(*f)(x);
   dw=dv=dx=(*df)(x); //All our housekeeping chores are doubled
                      // by the necessity of moving
                      //derivative values around as well
                      //as function values.
   for (iter=1;iter<=ITMAX;iter++) {
      xm=0.5*(a+b);
      tol1=tol*fabs(x)+ZEPS;
      tol2=2.0*tol1;
      if (fabs(x-xm) <= (tol2-0.5*(b-a))) {
         *xmin=x;
         return fx;
      }
      if (fabs(e) > tol1) {
         d1=2.0*(b-a); //Initialize these d’s to an out-of-bracket value. 
         d2=d1;
         if (dw != dx) d1=(w-x)*dx/(dx-dw); //Secant method with one point.
         if (dv != dx) d2=(v-x)*dx/(dx-dv); //And the other.
         /*Which of these two estimates of d shall we take? We will insist that they be within
           the bracket, and on the side pointed to by the derivative at x:*/
         u1=x+d1;
         u2=x+d2;
         ok1 = (a-u1)*(u1-b) > 0.0 && dx*d1 <= 0.0;
         ok2 = (a-u2)*(u2-b) > 0.0 && dx*d2 <= 0.0;
         olde=e; //Movement on the step before last.
         e=d;
         if (ok1 || ok2) { //Take only an acceptable d, and if
                           //both are acceptable, then take
                           //the smallest one.
            if (ok1 && ok2)
               d=(fabs(d1) < fabs(d2) ? d1 : d2);
            else if (ok1)
               d=d1;
            else
               d=d2;
            if (fabs(d) <= fabs(0.5*olde)) {
               u=x+d;
               if (u-a < tol2 || b-u < tol2)
                  d=SIGN(tol1,xm-x);
            } else { //Bisect, not golden section.
               d=0.5*(e=(dx >= 0.0 ? a-x : b-x));
               //Decide which segment by the sign of the derivative.
            }
         } else {
            d=0.5*(e=(dx >= 0.0 ? a-x : b-x));
         }
      } else {
         d=0.5*(e=(dx >= 0.0 ? a-x : b-x));
      }
      if (fabs(d) >= tol1) {
         u=x+d;
         fu=(*f)(u);
      } else {
         u=x+SIGN(tol1,d);
         fu=(*f)(u);
         if (fu > fx) { //If the minimum step in the downhill
                        //direction takes us uphill, then
                        //we are done.
            *xmin=x;
            return fx;
         }
      }
      du=(*df)(u); //Now all the housekeeping, sigh.
      if (fu <= fx) {
         if (u >= x) a=x; else b=x;
         MOV3(v,fv,dv, w,fw,dw)
         MOV3(w,fw,dw, x,fx,dx)
         MOV3(x,fx,dx, u,fu,du)
      } else {
         if (u < x) a=u; else b=u;
         if (fu <= fw || w == x) {
            MOV3(v,fv,dv, w,fw,dw)
            MOV3(w,fw,dw, u,fu,du)
         } else if (fu < fv || v == x || v == w) {
            MOV3(v,fv,dv, u,fu,du)
         }
      }
   }
   nrerror("Too many iterations in routine dbrent");
   return 0.0; //Never get here.
}
#ifdef __cplusplus
}
#endif
