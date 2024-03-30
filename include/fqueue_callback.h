/*
** fqueue_callback.h
*/
#ifndef _FQUEUE_CALLBACK_H
#define _FQUEUE_CALLBACK_H

#include <stdbool.h>
#include "fq_logger.h"
#include "fqueue.h"

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

typedef struct _FQ_server_t {
	fq_logger_t *l;
	fqueue_obj_t	*qobj;
	bool	xa_flag;
	bool	multi_flag;
	int	max_threads;
	deCB fp;
} FQ_server_t;

bool deFQ_CB_server(char *qpath, char *qname, bool xa_flag, char *logname, int log_level, bool multi_flag, int max_threads, deCB userFP);

bool make_thread_fq_CB_server(char *logname, int log_level, char *qpath, char *qname, bool xa_flag, bool multi_flag, int max_threads, deCB userFP);


#ifdef __cpluscplus
}
#endif
#endif
