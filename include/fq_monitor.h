/* vi: set sw=4 ts=4: */
/*
 * fq_monitor.h
 */
#ifndef _FQ_MONITOR_H
#define _FQ_MONITOR_H

#include <pthread.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/msg.h>
#include <sys/sem.h>

#include "fq_logger.h"
#include "fq_semaphore.h"
#include "fq_linkedlist.h"
#include "fq_hashobj.h"

#define FQ_MONITOR_H_VERSION "1.0.0"

#ifdef __cplusplus
extern "C"
{
#endif

#define FQ_MONITORING_KEY_PATH		"/tmp"
#define FQ_MONITORING_SHM_CHAR_KEY  'm'		/* 0x6d010001 */
#define FQ_MONITORING_SEMA_CHAR_KEY  'm'	/* 0x6d010001 */

#define MAX_MON_DELAY_SECOND		20

/* semaphore key */
/* 0x6d010002 */

typedef enum { FQ_EN_ACTION=0, FQ_DE_ACTION, FQ_NO_ACTION, FQ_ERR_ACTION=-1 } action_flag_t;


#define MAX_MON_RECORDS 		500

#define MAX_SERVER_UNAME_LEN    32
#define MAX_APP_ID_LEN          32
#define MAX_PATH_NAME_LEN       128
#define MAX_DESC_LEN            128
#define MAX_QNAME_LEN       	32      /* because of opening share memory */

typedef struct _monfile_rec monfile_rec_t;
struct _monfile_rec {
	char	appID[MAX_APP_ID_LEN+1];
	char	path[MAX_PATH_NAME_LEN+1];
	char	qname[MAX_QNAME_LEN+1];
	char	appDesc[MAX_DESC_LEN+1];
	action_flag_t	action_flag; 

	time_t	heartbeat_time;
	long long	write_count;
	int		pid;
};

typedef struct _monitor_touch_thread_param {
    fq_logger_t     *l;
	char	*appID;
	char	*qpath;
	char	*qname;
	char	*desc;
	action_flag_t action_flag;
	int		period;
} monitor_touch_thread_param_t;

typedef struct _monitor_t monitor_t;
struct _monitor_t {
	int				current;
	monfile_rec_t	r[MAX_MON_RECORDS];
};

typedef struct _monitor_obj_t monitor_obj_t;
struct _monitor_obj_t {
	fq_logger_t		*l;
	monitor_t 		*m; /* shared memory pointer */
	semaphore_obj_t	*sema_obj;
	time_t	last_send_time;
	char	last_send_appID[MAX_APP_ID_LEN+1];
	int (*on_send_action_status)(fq_logger_t *, char *, char *, char *, char *, action_flag_t, monitor_obj_t *);
	int (*on_touch)(fq_logger_t *, char *, action_flag_t, monitor_obj_t *);
	int (*on_getlist_mon)(fq_logger_t *, monitor_obj_t *, int *,  char **);
	int (*on_get_app_status)( fq_logger_t *, monitor_obj_t *, char *, char *);
	int (*on_delete_app_in_monitortable)(fq_logger_t *, char *, monitor_obj_t *);
	
};

typedef struct _fqueue_list fqueue_list_t;
struct _fqueue_list {
    char qpath[128];
    char qname[16+1];
};

typedef struct _HashQueueInfo {
	char key[512];
    char qpath[128];
    char qname[16+1];
	int max_rec_cnt;
	int msglen;
	int gap;
	float usage;
	time_t	en_competition;
	time_t	de_competition;
	int	big;
	int	en_tps;
	int de_tps;
	long en_sum;
	long de_sum;
	int	status;
	int shmq_flag;
	char desc[MAX_DESC_LEN+1];
	time_t	last_en_time;
	time_t	last_de_time;
} HashQueueInfo_t;

int Shb_Start_Mon(char *Progname, char *Desc);
int RegistMon( fq_logger_t *l, char *Progname, char *Desc, char *path, char *qname, action_flag_t flag);

int open_monitor_obj( fq_logger_t *l, monitor_obj_t **obj);
int close_monitor_obj( fq_logger_t *l, monitor_obj_t **obj);
int unlink_monitor( fq_logger_t *l);
void *monitor_touch_thread(void *arg);

bool MakeLinkedList_filequeues(fq_logger_t *l, linkedlist_t *ll  );
bool del_Exclude_Queues_in_LinkedList(fq_logger_t *l, linkedlist_t *ll, char *exclude_filename );
bool Make_Array_from_Hash_QueueInfo_linkedlist( fq_logger_t *l, hashmap_obj_t *hash_obj,  linkedlist_t *ll, HashQueueInfo_t Array[]  );
HashQueueInfo_t *get_a_queue_info_in_hashmap( fq_logger_t *l, char *hash_path, char *hash_name,  char *key);

HashQueueInfo_t *get_a_queue_info_in_hashmap( fq_logger_t *l, char *hash_path, char *hash_name,  char *key);
bool get_a_queue_info_in_hashmap_2( fq_logger_t *l, char *hash_path, char *hash_name,  char *key, HashQueueInfo_t **dst);


#ifdef __cplusplus
}
#endif

#endif
