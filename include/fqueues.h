/* vi: set sw=4 ts=4: */
/*
 * fqueues.h
 */
#ifndef _FQUEUES_H
#define _FQUEUES_H

/* version history
** 1.0.1 : 2013/05/19 : add on_view() function.
** 1.0.2 : 2013/11/08 : add  ForceSkipFileQueue().
** 1.0.3 : 2014/03/27 : addd ExtendFileQueue().
** 1.0.4 : 2016/05/19 : add manager area
*/
#include <stdbool.h>

#include <pthread.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/msg.h>

#include "fq_flock.h"
#include "fq_logger.h"
#include "fq_unixQ.h"
#include "fq_eventlog.h"
#include "fq_external_env.h"
#include "fq_monitor.h"
#include "fq_heartbeat.h"
#include "fq_typedef.h"
#include "fq_conf.h"
#include "fq_linkedlist.h"
#include "fq_manage.h"
#include "fq_file_list.h"
#include "fq_codemap.h"
#include "fq_delimiter_list.h"

#ifdef __cplusplus
extern "C"
{
#endif

typedef struct _fq_objs fq_objs_t;
struct _fq_objs {
	fq_logger_t *l;
	int length;
	char list_filename[256];
	linkedlist_t *ll;
	fqueue_obj_t * (*on_get_key_qobj)( fq_objs_t *, char );
	fqueue_obj_t * (*on_get_least_qobj)( fq_objs_t * );
	fqueue_obj_t * (*on_get_key_least_qobj)( fq_objs_t *, char );
	fqueue_obj_t * (*on_find_qobj)( fq_objs_t *, char *, char *);
};

typedef struct _group_queue_t group_queue_t;
struct _group_queue_t {
	char initial; /* key */
	char kind; /* kind of queue, 'D' de, 'E' en */
	fqueue_obj_t	*obj;
};

bool OpenFileQueues(fq_logger_t *l, char *list_file, fq_objs_t **objs);
bool CloseFileQueues(fq_logger_t *l, fq_objs_t *objs);


#ifdef __cplusplus
}
#endif

#endif
