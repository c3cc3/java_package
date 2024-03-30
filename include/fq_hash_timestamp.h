/*
** fq_hash_timestamp.h
*/
#ifndef _FQ_HASH_TIMESTAMP_H
#define _FQ_HASH_TIMESTAMP_H

#include <time.h>
#include <sys/time.h>
#include "fq_hashobj.h"
#include "fq_logger.h"

#define FQ_HASH_TIMESTAMP_H_VERSION "1.0,0"

#ifdef __cplusplus
extern "C" 
{
#endif

typedef struct _timestamp_obj_t timestamp_obj_t;
struct _timestamp_obj_t {
	int		cycle_sec;
	char	*hash_key;
	time_t 	last_stamp;
	hashmap_obj_t *hash_obj;

	int  (*on_stamp)( timestamp_obj_t *);
};

int open_timestamp_obj( fq_logger_t *l, timestamp_obj_t **obj, char *hash_path, char *hash_name, char *hash_key, int cycle_sec);
int close_timestamp_obj( fq_logger_t *l, timestamp_obj_t **obj);

int opened_timestamp_obj(  fq_logger_t *l, timestamp_obj_t **obj, hashmap_obj_t *hash_obj, char *hash_key, int cycle_sec);
int closed_timestamp_obj(  fq_logger_t *l, timestamp_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif
