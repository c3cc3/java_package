/* vi: set sw=4 ts=4: */
/***************************************************************
 ** hash_manage.h
 **/

#ifndef _HASH_MANAGE_H
#define _HASH_MANAGE_H
#include <pthread.h>

#include "fq_cache.h"
#include "fq_logger.h"

#define HASH_MANAGE_H_VERSION "1.0.0"

#define HASH_LIST_FILE_NAME "ListHash.info"

#define UNLINK	0

typedef enum { HASH_CREATE=0, HASH_UNLINK, HASH_CLEAN, HASH_EXTEND, HASH_VIEW, HASH_SCAN } hash_cmd_t;

int hash_manage_one(fq_logger_t *l, char *path, char *hashname, hash_cmd_t cmd );
int hash_manage_all(fq_logger_t *l, char *path, hash_cmd_t cmd );

#ifdef __cplusplus
}
#endif

#endif
