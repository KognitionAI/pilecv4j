#ifndef PILECV4J_NR_UTILS_H_
#define PILECV4J_NR_UTILS_H_

#include "kog_exports.h"

namespace pilecv4j {
namespace nr {

inline float SQR(const float sqrarg) {
  return (sqrarg == 0.0 ? 0.0 : sqrarg*sqrarg);
}

inline double DSQR(const double a) {
  return a == 0.0 ? 0.0 : a*a;
}

inline double DMAX(const double a, const double b) {
  return a > b ? a : b;
}

inline double DMIN(const double a, const double b) {
  return a < b ? a : b;
}

inline float FMAX(const float a, const float b) {
  return a > b ? a : b;
}

inline float FMIN(const float a, const float b) {
  return a < b ? a : b;
}

inline long LMAX(const long a, const long b) {
  return a > b ? a : b;
}

inline long LMIN(const long a, const long b) {
  return a < b ? a : b;
}

inline int IMAX(const int a, const int b) {
  return a > b ? a : b;
}

inline int IMIN(const int a, const int b) {
  return a < b ? a : b;
}

#define SIGN(a,b) ((b) >= 0.0 ? fabs(a) : -fabs(a))

KAI_EXPORT void nrerror(const char error_text[]);
/* My custom added methods for error handling */
KAI_EXPORT void nrerror2(const char error_text[]);
KAI_EXPORT int nrIsError();
/**********************************************/
KAI_EXPORT float *vector(long nl, long nh);
KAI_EXPORT int *ivector(long nl, long nh);
KAI_EXPORT unsigned char *cvector(long nl, long nh);
KAI_EXPORT unsigned long *lvector(long nl, long nh);
KAI_EXPORT double *dvector(long nl, long nh);
KAI_EXPORT float **matrix(long nrl, long nrh, long ncl, long nch);
KAI_EXPORT double **dmatrix(long nrl, long nrh, long ncl, long nch);
KAI_EXPORT int **imatrix(long nrl, long nrh, long ncl, long nch);
KAI_EXPORT float **submatrix(float **a, long oldrl, long oldrh, long oldcl, long oldch,
KAI_EXPORT long newrl, long newcl);
KAI_EXPORT float **convert_matrix(float *a, long nrl, long nrh, long ncl, long nch);
KAI_EXPORT float ***f3tensor(long nrl, long nrh, long ncl, long nch, long ndl, long ndh);
KAI_EXPORT void free_vector(float *v, long nl, long nh);
KAI_EXPORT void free_ivector(int *v, long nl, long nh);
KAI_EXPORT void free_cvector(unsigned char *v, long nl, long nh);
KAI_EXPORT void free_lvector(unsigned long *v, long nl, long nh);
KAI_EXPORT void free_dvector(double *v, long nl, long nh);
KAI_EXPORT void free_matrix(float **m, long nrl, long nrh, long ncl, long nch);
KAI_EXPORT void free_dmatrix(double **m, long nrl, long nrh, long ncl, long nch);
KAI_EXPORT void free_imatrix(int **m, long nrl, long nrh, long ncl, long nch);
KAI_EXPORT void free_submatrix(float **b, long nrl, long nrh, long ncl, long nch);
KAI_EXPORT void free_convert_matrix(float **b, long nrl, long nrh, long ncl, long nch);
KAI_EXPORT void free_f3tensor(float ***t, long nrl, long nrh, long ncl, long nch,
                   long ndl, long ndh);

}
}

#endif /* _NR_UTILS_H_ */

