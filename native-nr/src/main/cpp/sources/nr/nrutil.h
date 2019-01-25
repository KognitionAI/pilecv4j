#ifndef _NR_UTILS_H_
#define _NR_UTILS_H_

#include "kog_exports.h"

#ifdef __cplusplus
extern "C" {
#endif

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

void nrerror(const char error_text[]);
/* My custom added methods for error handling */
void nrerror2(const char error_text[]);
int nrIsError();
// this acts as a MOVE operation on the char* returned.
//  It's then owned by the called.
KAI_EXPORT char* nrGetErrorMessage();
/**********************************************/
float *vector(long nl, long nh);
int *ivector(long nl, long nh);
unsigned char *cvector(long nl, long nh);
unsigned long *lvector(long nl, long nh);
double *dvector(long nl, long nh);
float **matrix(long nrl, long nrh, long ncl, long nch);
double **dmatrix(long nrl, long nrh, long ncl, long nch);
int **imatrix(long nrl, long nrh, long ncl, long nch);
float **submatrix(float **a, long oldrl, long oldrh, long oldcl, long oldch,
long newrl, long newcl);
float **convert_matrix(float *a, long nrl, long nrh, long ncl, long nch);
float ***f3tensor(long nrl, long nrh, long ncl, long nch, long ndl, long ndh);
void free_vector(float *v, long nl, long nh);
void free_ivector(int *v, long nl, long nh);
void free_cvector(unsigned char *v, long nl, long nh);
void free_lvector(unsigned long *v, long nl, long nh);
void free_dvector(double *v, long nl, long nh);
void free_matrix(float **m, long nrl, long nrh, long ncl, long nch);
void free_dmatrix(double **m, long nrl, long nrh, long ncl, long nch);
void free_imatrix(int **m, long nrl, long nrh, long ncl, long nch);
void free_submatrix(float **b, long nrl, long nrh, long ncl, long nch);
void free_convert_matrix(float **b, long nrl, long nrh, long ncl, long nch);
void free_f3tensor(float ***t, long nrl, long nrh, long ncl, long nch,
                   long ndl, long ndh);
#ifdef __cplusplus
}
#endif
#endif /* _NR_UTILS_H_ */

