#ifndef _SEMA_QUEUE_H
#define _SEMA_QUEUE_H

#include <pthread.h>
#include "fq_logger.h"

#ifdef  __cplusplus
extern "C" {
#endif

/* Notice!!: important */
/* Set two values properly at memory size of your system */
#define MAX_QUEUE_RECORDS	(1000)
#define SEMA_QUEUE_MAX_MSG_LEN	(65535)

struct _semaqueue_t {
	pthread_mutex_t	lock;
	pthread_cond_t	nonzero_put;
	pthread_cond_t	nonzero_get;
	unsigned count;
	unsigned long long en_total;
	unsigned long long de_total;
	unsigned char Q[MAX_QUEUE_RECORDS][SEMA_QUEUE_MAX_MSG_LEN];
};
typedef struct _semaqueue_t semaqueue_t;

semaqueue_t	*semaqueue_create( fq_logger_t *l, char *semaphore_name);
semaqueue_t	*semaqueue_open( fq_logger_t *l, char *semaphore_name);
void semaqueue_post(fq_logger_t *l, semaqueue_t *semap, unsigned char *src, unsigned srclen);
void semaqueue_wait(fq_logger_t *l, semaqueue_t *semap, unsigned char **buf, unsigned *msglen);
void semaqueue_close(fq_logger_t *l, semaqueue_t *semap);
void semaqueue_destroy(fq_logger_t *l, char *semaphore_name);

#ifdef __cplusplus
}
#endif

#endif


