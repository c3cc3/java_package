#define FQ_POSIXLIB_H_VERSION "1.0,0"

#ifndef _FQ_POSIXLIB_H
#define _FQ_POSIXLIB_H

#include "config.h"

#ifdef __cplusplus
extern "C"
{
#endif

#ifdef HAVE_BASENAME
char *getLastElement(char *path);
#endif

#ifdef HAVE_DIFFTIME
ong DiffTime(time_t t1, time_t t2);
#endif


#ifdef __cplusplus
}
#endif

#endif /* end of FQ_POSIXLIB_H */
