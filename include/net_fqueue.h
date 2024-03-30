/* vi: set sw=4 ts=4: */
/*
 * net_fqueue.h
 */
#ifndef _NET_FQUEUE_H
#define _NET_FQUEUE_H

#include <pthread.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/msg.h>
#include <sys/sem.h>

#include "fq_logger.h"
#include "fq_semaphore.h"
#include "fq_unixQ.h"
#include "fq_eventlog.h"

#ifdef __cplusplus
extern "C"
{
#endif

/*
typedef enum { EN_NORMAL_MODE=0, EN_CIRCULAR_MODE } enQ_mode_t;
*/
typedef enum { EN_ACTION, DE_ACTION } net_action_t;
typedef enum { ACK_MODE, NO_ACK_MODE } ack_mode_t;

typedef struct _net_fqueue_obj_t net_fqueue_obj_t;
struct _net_fqueue_obj_t {
	char		*path;
	char		*qname;
	int			sockfd;
	net_action_t	action;
	ack_mode_t		ack_mode;
	tcp_client_obj_t		*obj; /* unix queue object */

	int (*on_en_net)(fq_logger_t *, net_fqueue_obj_t *, enQ_mode_t, const unsigned char *, int, size_t, long *);
	int (*on_de_net)(fq_logger_t *, net_fqueue_obj_t *, unsigned char *, int, long *);
};

int NetOpenFileQueue(fq_logger_t *l, char *ip, char *port, char *path, char *qname, net_fqueue_obj_t **obj);
int NetCloseFileQueue(fq_logger_t *l, net_fqueue_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif
