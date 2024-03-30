/*
** fq_map_db.h
*/
#ifndef _FQ_MAP_DB_H
#define _FQ_MAP_DB_H

#include <pthread.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/msg.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/ioctl.h>

#include "fq_logger.h"
#include "fq_common.h"
#include "fq_flock.h"

#ifdef __cplusplus
extern "C"
{
#endif

#define MAX_KEY_LEN		32
#define MAX_VALUE_LEN	256

#define MAX_RECORDS	1000

typedef struct _map_db_record {
	char key[MAX_KEY_LEN+1]; /* key value */
	char value[MAX_VALUE_LEN+1];
} map_db_record_t;

typedef struct _map_db_table {
	map_db_record_t	R[MAX_RECORDS];
} map_db_table_t;

typedef struct _map_db_obj_t map_db_obj_t;
struct _map_db_obj_t {
	char	*path;
	char	*name;
	char	pathfile[128]; /* Full path of  Data file. */
	int		fd; /* File Descriptor of Data File */
	fq_logger_t	*l;
	map_db_table_t	*t; /* mmap pointer */
	size_t	mmap_len;
	flock_obj_t	*flobj; /* flock obect */

	int (*on_insert)(fq_logger_t *, map_db_obj_t *, char *, char *);
	int (*on_delete)(fq_logger_t *, map_db_obj_t *, char *);
	int (*on_update)(fq_logger_t *, map_db_obj_t *, char *, char *);
	int (*on_retrieve)(fq_logger_t *, map_db_obj_t *, char *, char *);
	int (*on_getlist)(fq_logger_t *, map_db_obj_t *, int *, char **);
	int (*on_show)(fq_logger_t *, map_db_obj_t *);
};

int open_map_db_obj( fq_logger_t *l, char *path, char *db_filename, map_db_obj_t **obj);
int close_map_db_obj( fq_logger_t *l,  map_db_obj_t **obj);
int unlink_map_obj( fq_logger_t *l,  char *path, char *name);

#ifdef __cplusplus
}
#endif

#endif
