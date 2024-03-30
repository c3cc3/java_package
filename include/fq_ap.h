#ifndef FQ_AP_H
#define FQ_AP_H

#include "fq_defs.h"
#include <stdarg.h>

#define ap_isalnum(c) (isalnum(((unsigned char)(c))))
#define ap_isalpha(c) (isalpha(((unsigned char)(c))))
#define ap_iscntrl(c) (iscntrl(((unsigned char)(c))))
#define ap_isdigit(c) (isdigit(((unsigned char)(c))))
#define ap_isgraph(c) (isgraph(((unsigned char)(c))))
#define ap_islower(c) (islower(((unsigned char)(c))))
#define ap_isprint(c) (isprint(((unsigned char)(c))))
#define ap_ispunct(c) (ispunct(((unsigned char)(c))))
#define ap_isspace(c) (isspace(((unsigned char)(c))))
#define ap_isupper(c) (isupper(((unsigned char)(c))))
#define ap_isxdigit(c) (isxdigit(((unsigned char)(c))))
#define ap_tolower(c) (tolower(((unsigned char)(c))))
#define ap_toupper(c) (toupper(((unsigned char)(c))))

typedef struct {
    char *curpos;
    char *endpos;
} ap_vformatter_buff;

#ifdef __cplusplus
extern "C" {

#endif
int ap_snprintf(char *buf, size_t len, const char *format,...);
int ap_vsnprintf(char *buf, size_t len, const char *format, va_list ap);
int ap_strncpy(char *dst, size_t bufsize,  char* src, char* srcfile, int srcline);

#ifdef __cplusplus
}
#endif

#endif
