/*
** fq_heartbeat.h
*/
#ifndef _FQ_HEARTBEAT_H
#define _FQ_HEARTBEAT_H

#include <pthread.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/msg.h>
#include <sys/sem.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/ioctl.h>

#include "fq_logger.h"
#include "fq_common.h"

#ifdef __cplusplus
extern "C"
{
#endif

#if 1
#define _USE_HEARTBEAT
#else
#undef _USE_HEARTBEAT 
#endif

#if 0
#define _USE_PROCESS_CONTROL
#else
#undef _USE_PROCESS_CONTROL 
#endif

#define HB_DB_PATH "/home/pi/data" /* Use shared dist for HEARTBEAT processing */
#define HB_DB_NAME "heartbeat.DB"
#define HB_LOCK_FILENAME "heartbeat" /* automatically prefix . and extension .lock are attached in library. */

#define MAX_HEARTBEAT_NODES       500
#define MAX_KILL_LIST	MAX_HEARTBEAT_NODES

#define FQ_HEARTBEAT_HOSTNAME_1 "server-1"
#define FQ_HEARTBEAT_HOSTNAME_2 "server-2"

/* Decide_Master_Slave return values */
typedef enum { BE_MASTER=0, BE_SLAVE, ALREADY_MASTER_EXIST, ALREADY_SLAVE_EXIST, DUP_PROCESS } decide_returns_t;

#define CHANGING_WAIT_TIME 10

#define DIST_NAME_LEN	16
#define PROG_NAME_LEN	32
#define HOST_NAME_LEN	32

/* kinds of status in memory */
typedef enum { INIT_STATUS=0, READY_STATUS, WORK_STATUS, HANG_STATUS, STOP_STATUS, PASS_STATUS, MAX_STATUS } p_status_t;

#define MASTER_TIME_STAMP 	1
#define SLAVE_TIME_STAMP	2

typedef struct _heartbeat_controller {
	char progname[PROG_NAME_LEN+1];
	pid_t pid;
} heartbeat_controller_t;
	
typedef struct _kill_list {
	char hostname[HOST_NAME_LEN+1];
	pid_t	pid;
} kill_list_t;

typedef struct _sub_node {
	char    hostname[HOST_NAME_LEN+1];
    char    ip[16];
    pid_t   pid; 	/* process id */
    time_t  time; /* touch time */
	p_status_t status;
} sub_node_t;

typedef struct _heaarbeat_node {
	/* common area */
	char	dist_name[DIST_NAME_LEN+1];
	char	prog_name[PROG_NAME_LEN+1];
	char	master_host_name[HOST_NAME_LEN+1];

	sub_node_t	m;	/* master info */
	sub_node_t  s;	/* slave info */
} hearbeat_node_t;

typedef struct  _heartbeat {
	int registed_process_cnt;
    hearbeat_node_t nodes[MAX_HEARTBEAT_NODES];
	kill_list_t	k[MAX_KILL_LIST];
	heartbeat_controller_t c1; /* controller #1 */
	heartbeat_controller_t c2; /* controller #2 */
} heartbeat_t;

typedef struct _heartbeat_obj_t heartbeat_obj_t;
struct _heartbeat_obj_t {
	char	*path;
	char	*db_filename;
	char	*db_pathfile;
	char	*lock_filename;
	char	*lock_pathfile;

	char	*hostname;
	int		my_index;
	int		fd;
	time_t	last_timestamp;
	fq_logger_t	*l;
	heartbeat_t *h; // mapping pointer , fiexed size.
	int lockfd;
	decide_returns_t (*on_decide_Master_Slave)(fq_logger_t *, heartbeat_obj_t *);
	int (*on_timestamp)(fq_logger_t *, heartbeat_obj_t *, int );
	int (*on_change)(fq_logger_t *, heartbeat_obj_t *, char *, char *);
	int (*on_add_table)(fq_logger_t *, heartbeat_obj_t *, char *, char *, char *);
	int (*on_del_table)(fq_logger_t *, heartbeat_obj_t *, char *, char *, char *);
	int (*on_set_status)(fq_logger_t *, heartbeat_obj_t *, char *, char *, char *, p_status_t);
	int (*on_get_status)(fq_logger_t *, heartbeat_obj_t *, char *, char *, char *, p_status_t *);
	int (*on_print_table)(fq_logger_t *, heartbeat_obj_t *);
	int (*on_getlist)(fq_logger_t *, heartbeat_obj_t *, int *, char **);
};

typedef struct _timestamp_thread_params {
	fq_logger_t *l;
	heartbeat_obj_t *obj;
} timestamp_thread_params_t;


int init_heartbeat_table( fq_logger_t *l, fqlist_t *t, char *path, char *db_filename, char *lock_filename );
int get_heartbeat_obj( fq_logger_t *l, char *hostname, char *path, char *db_filename, char *lock_filename, heartbeat_obj_t **obj);
int open_heartbeat_obj( fq_logger_t *l, char* hostname, char *dist_name, char *prog_name, char *path, char *db_filename, char *lock_filename, heartbeat_obj_t **obj);
int close_heartbeat_obj( fq_logger_t *l,  heartbeat_obj_t **obj);
int make_timestamp_thread(fq_logger_t *l, heartbeat_obj_t *obj);

void restart_process(char **args);
int regist_controller( fq_logger_t *l, char *hostname, char *progname, int c1c2 );
void print_heartbeat_status( p_status_t status);

fqlist_t *load_process_list_from_file( char *filename);

#ifdef __cplusplus
}
#endif

#endif
