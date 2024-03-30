/* vi: set sw=4 ts=4: */
/*
 * fq_multi_queue.h
 */
#ifndef _FQ_MULTI_QUEUE_H
#define _FQ_MULTI_QUEUE_H

#include <pthread.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/msg.h>
#include <sys/sem.h>

#include "fqueue.h"
#include "fq_logger.h"

#define FQ_MULTI_QUEUE_H_VERSION "1.0.0"

#ifdef __cplusplus
extern "C"
{
#endif

typedef struct _Queues_obj Queues_obj_t;
struct _Queues_obj {
	pthread_mutex_t	mutex;
	fq_logger_t *l;
	int	alloced_queue_count;
	int opened_queue_count;
	fqueue_obj_t **mobj;
	fqueue_obj_t *(*on_open_queue)( Queues_obj_t *, char **,  char *, char *, char *, char *, char *, int);
	fqueue_obj_t *(*on_get_fqueue_obj_with_index)( Queues_obj_t *, int);
	fqueue_obj_t *(*on_get_fqueue_obj_with_qname)( Queues_obj_t *, char *, char *);
	int (*on_close_queue)( Queues_obj_t *, int );
};

int new_Queues_obj(fq_logger_t *l, Queues_obj_t **Qs, int alloc_queue_count);
void free_Queues_obj(fq_logger_t *l, Queues_obj_t **Qs);

#ifdef __cplusplus
}
#endif

#endif
