/*
** shm_queue_callback.h
*/
#ifndef _SHM_QUEUE_CALLBACK_H
#define _SHM_QUEUE_CALLBACK_H

#include <stdbool.h>
#include "fq_logger.h"
#include "fqueue.h"
#include "shm_queue.h"

#ifdef __cpluscplus
extern "C"
{
#endif

typedef int (*deCB)(unsigned char *, int, long);

typedef struct _de_msg_t {
	char *msg;
	int msglen;
	long msgseq;
	deCB user_fp; /* user function pointer */
}de_msg_t;

typedef struct _SHMQ_server_t {
	fq_logger_t *l;
	fqueue_obj_t	*qobj;
	bool	multi_flag;
	int	max_threads;
	deCB fp;
} SHMQ_server_t;

bool deSHMQ_CB_server(char *qpath, char *qname, char *logname, int log_level, bool multi_flag, int max_threads, deCB userFP);
bool make_thread_shmq_CB_server(char *logname, int log_level, char *qpath, char *qname, bool multi_flag, int max_threads, deCB userFP);


#ifdef __cpluscplus
}
#endif
#endif
