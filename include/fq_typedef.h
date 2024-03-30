#ifndef __TYPEDEFS_H
#  define __TYPEDEFS_H

#include <sys/types.h>

#ifndef LOCAL
#  define LOCAL                                 static
#endif  /* LOCAL */

#ifndef EXTRN
#  ifdef __cplusplus
#     define EXTRN      extern "C"
#  else
#    define EXTRN       extern
#  endif
#endif  /* EXTRN */

#include <stdio.h>
#ifndef __stderr
#   define __stderr                                     stderr
#else
#   ifdef __DEBUG__
EXTRN FILE *__stderr;
#   endif
#endif

#if defined(__linux__) || defined(__EXTENSIONS__) || \
                (!defined(_POSIX_C_SOURCE) && !defined(_XOPEN_SOURCE))
#  ifndef __uchar__
#    define __uchar__
typedef unsigned char fq_uchar;
#  endif        /* __uchar__ */
#else
#  ifndef __alpha__
typedef unsigned short ushort;
typedef unsigned int  uint;
#  endif
#endif

#ifdef _USE_SUN
#ifndef __64BIT__
typedef long long int64;
typedef unsigned long long uint64;
#else
#  define int64                         long
#  define uint64                        unsigned long
#endif
#endif

#ifndef __bool__
#  define __bool__
typedef enum { False = 0, True } bool_t;
#endif

#define ALIGN                           sizeof(int)
#define DOALIGN(__s)            ((__s) + (ALIGN - ((long)(__s) % ALIGN)))

#ifndef __P
#  define __P(__args)           __args
#endif

#ifndef IN
#  define IN
#endif

#ifndef OUT
#  define OUT
#endif

#ifndef INOUT
#  define INOUT
#endif

#endif
