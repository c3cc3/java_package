/* vi: set sw=4 ts=4: */
/*
 * fq_pthread_mutex_lock.h
 */
#ifndef _FQ_PTHREAD_MUTEX_LOCK_H
#define _FQ_PTHREAD_MUTEX_LOCK_H

#include <pthread.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/msg.h>
#include <sys/sem.h>

#include "fq_logger.h"

#define FQ_PTHREAD_MUTEX_LOCK_H_VERSION "1.0.0"

#ifdef __cplusplus
extern "C"
{
#endif

typedef struct _shm_lock_struct shm_lock_struct_t;
struct _shm_lock_struct {
	pthread_mutex_t  mtx;
	time_t    lock_time;
	pthread_cond_t   cond;
};

typedef struct _shmlock_obj_t shmlock_obj_t;
struct _shmlock_obj_t {
    char    *name;
    int     fd;

	shm_lock_struct_t *p;

	pthread_mutexattr_t mtx_attr;
    pthread_condattr_t cond_attr;

    fq_logger_t *l;

    int (*on_mutex_lock)(fq_logger_t *, shmlock_obj_t *);
    int (*on_mutex_unlock)(fq_logger_t *, shmlock_obj_t *);
};

int open_shmlock_obj( fq_logger_t *l, char *name, shmlock_obj_t **obj);
int unlink_shmlock_obj(fq_logger_t *l, char *name);
int close_shmlock_obj(fq_logger_t *l, shmlock_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif
