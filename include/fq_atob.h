#ifndef _FQ_ATOB_H
#define _FQ_ATOB_H

#include <time.h>
#include <sys/types.h>

#ifdef __cplusplus
extern "C" {
#endif

/* It is necessary on Solaris */
// typedef long             quad_t;
#ifdef OS_SOLARIS
typedef unsigned long       u_quad_t;
typedef unsigned int        u32;
typedef u32 u_int32_t;
// typedef unsigned long    uintptr_t;
#endif

int atob(u_int32_t *vp, char *p, int base);
int llatob(u_quad_t *vp, char *p, int base);
char *btoa(char *dst, u_int value, int base);
char *llbtoa(char *dst, u_quad_t value, int base);

#ifdef __cplusplus
}
#endif

#endif
