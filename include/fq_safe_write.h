/*
** fq_safe_write.h
** Copyrigth: CLANG Commpany
*/
#ifndef _fq_safe_write_h
#define _fq_safe_write_h

#include "config.h"
#ifdef HAVE_SYS_TYPES_H
#include <sys/types.h>
#endif

#ifdef HAVE_STRING_H
#include <string.h>
#include <stdbool.h>
#endif
#include "fq_defs.h"
#include "fq_logger.h"
#include "fq_types.h"
#include "fq_common.h"
#include "fq_flock.h"

#ifdef __cplusplus
extern "C"
{
#endif

typedef struct _safe_file_obj_t safe_file_obj_t;
struct _safe_file_obj_t{
	bool	binary_flag;
	flock_obj_t *lock_obj;
	char	*filename;
	FILE	*fp;
	long	w_cnt; /* write_count */
	fq_logger_t *l;
	int (*on_write)(safe_file_obj_t *obj, char *what);
	int (*on_write_b)(safe_file_obj_t *obj, unsigned char *what, size_t len);
};

int open_file_safe(fq_logger_t *l, char *filename, bool binary_flag, safe_file_obj_t **obj);
void close_file_safe( safe_file_obj_t **obj);

#ifdef __cplusplus
}
#endif
#endif
