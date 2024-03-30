#ifndef _FQ_SCANF_H
#define _FQ_SCANF_H

#include <time.h>

#ifdef __cplusplus
extern "C" {
#endif

int fq_scanf (int delimiter, const char *fmt, ...);
int fq_sscanf (int delimiter, const char *buf, const char *fmt, ...);
int fq_fscanf (int delimiter, FILE *fp, const char *fmt, ...);

#ifdef __cplusplus
}
#endif

#endif
