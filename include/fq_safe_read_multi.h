/*
** fq_safe_read_multi.h
** Copyrigth: CLANG Commpany
** version 1.0
** create date: 2020/04/15
*/
#ifndef _fq_safe_read_multi_h
#define _fq_safe_read_multi_h

#include "config.h"
#ifdef HAVE_SYS_TYPES_H
#include <sys/types.h>
#endif

#ifdef HAVE_STRING_H
#include <string.h>
#endif
#include <stdbool.h>
#include "fq_defs.h"
#include "fq_logger.h"
#include "fq_types.h"
#include "fq_common.h"
#include "fq_flock.h"

#ifdef __cplusplus
extern "C"
{
#endif

#define MAX_MSG_LEN	65535

/* object on_read() return values */
#define FILE_NOT_EXIST 0
#define FILE_READ_SUCCESS 1
#define FILE_READ_FATAL 2
#define FILE_NO_CHANGE 3
#define FILE_READ_RETRY 4

typedef struct _safe_read_file_multi_obj_t safe_read_file_multi_obj_t;
struct _safe_read_file_multi_obj_t {
	fq_logger_t *l;
	bool	binary_flag;
	char *filename;
	FILE	*fp;
	char *offset_filename;
	FILE	*offset_fp;
	off_t	offset;
	flock_obj_t *lock_obj;
	flock_obj_t *en_lock_obj; // for deleting
	int	open_flag;
	int offset_open_flag;
	int (*on_read)(safe_read_file_multi_obj_t *obj, char **dst);
	int (*on_read_b)(safe_read_file_multi_obj_t *obj, char **dst, int* ptr_msglen);
	off_t (*on_get_offset)(safe_read_file_multi_obj_t *obj);
	float (*on_progress)(safe_read_file_multi_obj_t *obj);
};

int open_file_4_read_safe_multi( fq_logger_t *l, char *filename, char *process_unique_id,  bool binary_flag, safe_read_file_multi_obj_t **obj);
int close_file_4_read_safe_multi( fq_logger_t *l, safe_read_file_multi_obj_t **obj);

#ifdef __cplusplus
}
#endif
#endif
