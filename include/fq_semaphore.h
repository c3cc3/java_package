/* vi: set sw=4 ts=4: */
/*
 * fq_semaphore.h
 */
#ifndef _FQ_SEMAPHORE_H
#define _FQ_SEMAPHORE_H

#include <pthread.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/msg.h>
#include <sys/sem.h>

#include "fq_logger.h"

#define FQ_SEMAPHORE_H_VERSION "1.0.0"

#ifdef __cplusplus
extern "C"
{
#endif

#define MAX_RETRIES 10
#define FQ_SEMAPHORE_KEY_CHAR	's'

typedef struct _semaphore_obj_t semaphore_obj_t;
struct _semaphore_obj_t {
	char	*path;
	char	key_char;
	key_t	key;
	int 	semid;
	struct sembuf sb;
	fq_logger_t	*l;

	int (*on_sem_lock)(semaphore_obj_t *);
	int (*on_sem_unlock)(semaphore_obj_t *);
};

union fq_semun {
    int val;
    struct semid_ds *buf;
    ushort *array;
};

int open_semaphore_obj( fq_logger_t *l, char *path, char key_char, semaphore_obj_t **obj);
int close_semaphore_obj( fq_logger_t *l, semaphore_obj_t **obj);
int unlink_semaphore( fq_logger_t *l, char *path, char key_char);

#ifdef __cplusplus
}
#endif

#endif
