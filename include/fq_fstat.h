#ifndef _FQ_FSTAT_H
#define _FQ_FSTAT_H

#define FQ_FSTAT_H_VERSION "1.0.0"

#include "fq_defs.h"
#include "fq_cache.h"
#include "fq_logger.h"

#ifdef __cplusplus
extern "C" {
#endif


int fq_scandir(const char *s_path, int sub_dir_name_include_flag);


#ifdef __cplusplus
}
#endif

#endif
