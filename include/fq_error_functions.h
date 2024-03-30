/* fq_error_functions.h
 *
 *    Header file for error_functions.c.
 *    
 */
#ifndef FQ_ERROR_FUNCTIONS_H
#define FQ_ERROR_FUNCTIONS_H

#ifdef __cplusplus
extern "C"
#endif

/* Error diagnostic routines */

void errMsg(const char *format, ...);

#ifdef __GNUC__

/* This macro stops 'gcc -Wall' complaining that "control reaches
 * end of non-void function" if we use the following functions to
 * terminate main() or some other non-void function. */

#define NORETURN __attribute__ ((__noreturn__))
#else
#define NORETURN
#endif

void errExit(const char *format, ...) NORETURN ;

void err_exit(const char *format, ...) NORETURN ;

void errExitEN(int errnum, const char *format, ...) NORETURN ;

void fatal(const char *format, ...) NORETURN ;

void usageErr(const char *format, ...) NORETURN ;

void cmdLineErr(const char *format, ...) NORETURN ;

#ifdef __cplusplus
}
#endif

#endif
