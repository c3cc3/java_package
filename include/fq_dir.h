#ifndef _COMMON_H
#define _COMMON_H

#include <time.h>
#include "fq_logger.h"
#include "config.h"

#ifdef __cplusplus
extern "C" {
#endif

int auto_delete(const char *s_path, int sure_flag, time_t last_time);
typedef int (*userCBtype)(fq_logger_t *, char *);
int recursive_dir_CB(fq_logger_t *l, const char *s_path, userCBtype userFP );

#ifdef __cplusplus
}
#endif

#endif
