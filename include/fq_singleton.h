/* vi: set sw=4 ts=4: */
/*
 * fq_singleton.h
 */
#ifndef _FQ_SINGLETON_H
#define _FQ_SINGLETON_H

#include <pthread.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/msg.h>

#include "fq_logger.h"
#include "fqueue.h"

#define FQ_SINGLETON_H_VERSION "1.0.0"

#ifdef __cplusplus
extern "C"
{
#endif

#define SINGLETON_LOCK_LIMIT_TIME 	8	/* second */
#define FQ_SINGLETON_KEY_PATH 		"/tmp"

/* #define FQ_LOCK_TIMEOUT_RETURN_VALUE -99 */

/* kind of locking methods */
#define FQ_ROBUST_NP_LOCK   0
#define FQ_TIMED_LOCK       1
#define FQ_TRY_LOCK         2

typedef struct _singleton_shmlock_t singleton_shmlock_t;
struct _singleton_shmlock_t {
    pthread_mutex_t lock;
    time_t      lock_time;
};

/* function prototypes */
singleton_shmlock_t *READY_singleton(fq_logger_t *l, unsigned char key);
int SAFE_lock_singleton( fq_logger_t *l, int lock_kind, singleton_shmlock_t *m);
int SAFE_unlock_singleton(fq_logger_t *l, singleton_shmlock_t *m);
int SAFE_close_singleton(fq_logger_t *l, void *p);
int SAFE_recovery_singleton( fq_logger_t *l, unsigned char key);
int DELETE_singleton(fq_logger_t *l, unsigned char key);
int getLockedTime_singletone(singleton_shmlock_t *m);

#ifdef __cplusplus
}
#endif

#endif
