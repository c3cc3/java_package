/* vi: set sw=4 ts=4: */
/*
 * fqueue.h
 */
#ifndef _FQUEUE_H
#define _FQUEUE_H

#define FQUEUE_H_VERSION "1.0.4"
#define FQUEUE_LIB_VERSION "14.8.2"

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
#include "fq_pthread_mutex_lock.h"


#ifdef __cplusplus
extern "C"
{
#endif

#define FQ_DIRECTORIES_LIST "FQ_directories.list"
#define FQ_LIST_INFO_FILE 	"ListFQ.info"

#define MAX_FQ_NAME_LEN (16)
#define MAX_FQ_OPENS 	(32) /* open files per a process */

#define FQ_LIMIT_BIG_FILES	(1000000)

#define FQ_TIME_LENGTH  	(19) /* yyyy/mm/dd hh:mm:DD */
// #define MAX_DESC_LEN        (128)
#define DEFAULT_QOS_VALUE   (0) /* default,  The bigger the value is fast. */

#define EQ_FULL_RETURN_VALUE    	0
#define DQ_EMPTY_RETURN_VALUE   	0 
#define DQ_FATAL_SKIP_RETURN_VALUE  (-88)
#define MANAGER_STOP_RETURN_VALUE   (-99)
#define IS_NOT_MASTER				(-77)
#define EXIST_UN_COMMIT				0	

#define FQ_HEADER_SIZE      	4  /* It has length of a message */

#define STOPWATCH_ON	1
#define STOPWATCH_OFF	0

#define HOST_NAME_LEN	32

/* new locking define */
#define FQ_LOCK_ACTION 1
#define FQ_UNLOCK_ACTION 2
#define L_EN_ACTION 1
#define L_DE_ACTION 2

typedef enum { EN_NORMAL_MODE=0, EN_CIRCULAR_MODE, EN_FULL_BACKUP_MODE } enQ_mode_t;
typedef enum { NO_WAIT_MODE=0, WAIT_MODE, FORCE_DE_Q_MODE } queue_mode_t;
typedef enum { XA_OFF=0, XA_ON, XA_ERR=-1 } XA_flag_t;
typedef enum { QUEUE_ENABLE=0, QUEUE_EN_DISABLE, QUEUE_DE_DISABLE, QUEUE_ALL_DISABLE } queue_status_t;

typedef struct _mmap_header mmap_header_t;

/* If you change structure of header, you have to change it ( version ) */
#define HEADER_VERSION (3)

struct _fq_process_table {
	pid_t	pid;
	time_t	heartbit;
};
typedef struct _fq_process_table fq_process_table_t;

#define MAX_FQ_PROCESSES (128)
struct _mmap_header {
	int	q_header_version; /* header version : 1 */
	queue_mode_t 	mode; /* NO_WAIT_MODE, WAIT_MODE, FORCE_DE_Q_MODE */
	XA_flag_t		XA_flag;
	queue_status_t	status;
	size_t 	msglen; /* same to MMAP_RECORD_LEN */
	int		made_big_dir; /* whether bigdir was made */
	int		current_big_files;
	long	big_sum;
	int		max_rec_cnt;
	int		pagesize;
	long 	en_sum; /* accumulated en_total count. */
	long 	de_sum; /* accumulated de_total count. */
	size_t	mapping_num;
	size_t	mapping_len;
	size_t	multi_num;
	size_t 	file_size; /* created file/shm size */
	size_t	limit_file_size; /* If filesize reachs limit, saving is omitted. */
	char	path[256];
	char	qname[128];
	char	desc[MAX_DESC_LEN+1];
	char	qfilename[256];
	time_t	create_time;  /* bin_time format */
	time_t	last_en_time;
	time_t	last_de_time;
	long	latest_en_time; /* 최대 지연 시간 en */
	time_t	latest_en_time_date; /* 최대 지연 발생 시간 */
	long	latest_de_time; /* 최대 지연 시간 de */
	time_t	latest_de_time_date; /* 최대 지연 발생 시간 */
	int		last_en_row;
	int		oldest_en_row;
	off_t   en_available_from_offset;
	off_t   en_available_to_offset;
	off_t   de_available_from_offset;
	off_t   de_available_to_offset;

	/* Manager Area */
	int		QOS; // 임의 지연 셋팅
	int		Master_assign_flag;
	char	MasterHostName[HOST_NAME_LEN+1];

	int		XA_ing_flag;
	time_t	XA_lock_time;

	/* access process manage */
	// fq_process_table_t pt[MAX_FQ_PROCESSES];
};


typedef struct _headfile_obj_t	headfile_obj_t;
struct _headfile_obj_t {
	fq_logger_t	*l;
	char	*name;
	int		fd;
	struct stat	st;
	mmap_header_t	*h; // mmapping pointer.
	flock_obj_t	*de_flock;
	flock_obj_t	*en_flock;
	shmlock_obj_t *de_shmlock;
	shmlock_obj_t *en_shmlock;
	int (*on_head_print)(fq_logger_t *, headfile_obj_t *);
};

typedef struct _datafile_obj_t	datafile_obj_t;
struct _datafile_obj_t {
	fq_logger_t	*l;
	char	*name;
	int		fd;
	struct stat	st;
};

typedef struct _backupfile_obj_t	backupfile_obj_t;
struct _backupfile_obj_t {
	char	*name;
	FILE	*fp;
};

struct _fqdata {
	int len;
	unsigned char *data;
	long seq;
};
typedef struct _fqdata fqdata_t;

/*
 * Each process has a _fqueue_obj
 */
typedef struct _fqueue_obj_t fqueue_obj_t;
struct _fqueue_obj_t {
	char		**argv;
	char		*path;
	char		*qname;
	char		*eventlog_path;
	pid_t		pid;
	pthread_t	tid;
	fq_logger_t	*l;
	int			stop_signal;
	int			stopwatch_on_off;
	void		*en_p;
	void		*de_p;

	char		*monID;
	monitor_obj_t	*mon_obj;

	heartbeat_obj_t	*hb_obj; /* heartbeat object */
	char		*hostname; /* for testing */

	headfile_obj_t	*h_obj; /*  header file object */
	datafile_obj_t	*d_obj; /* data file object */
	backupfile_obj_t	*b_obj; /* As a queue is full, automatically we save a message to backup file */

	/* WSL(Do not support it in Windows Subsystem Linux */
	unixQ_obj_t		*uq_obj; /* unix queue object */

	eventlog_obj_t	*evt_obj; /* file eventlog object */
	external_env_obj_t	*ext_env_obj;
	pthread_mutex_t	mutex;

	int (*on_en)(fq_logger_t *, fqueue_obj_t *, enQ_mode_t, const unsigned char *, int, size_t, long *, long *);
	int (*on_en_bundle_struct)(fq_logger_t *, fqueue_obj_t *, int src_cnt, fqdata_t src[], long *);
	int (*on_de)(fq_logger_t *, fqueue_obj_t *, unsigned char *, int, long *, long *);
	int (*on_de_bundle_array)(fq_logger_t *, fqueue_obj_t *, int array_cnt, int MaxDataLen, unsigned char *array[], long *);
	int (*on_de_bundle_struct)(fq_logger_t *, fqueue_obj_t *, int array_cnt, int MaxDataLen, fqdata_t array[], long *);
	int (*on_de_XA)(fq_logger_t *, fqueue_obj_t *, unsigned char *, int, long *, long *, char *);
	int (*on_commit_XA)(fq_logger_t *, fqueue_obj_t *, long, long * , char *);
	int (*on_cancel_XA)(fq_logger_t *, fqueue_obj_t *, long, long * );
	int (*on_disable)(fq_logger_t *, fqueue_obj_t *);
	int (*on_enable)(fq_logger_t *, fqueue_obj_t *);
	int (*on_reset)(fq_logger_t *, fqueue_obj_t *);
	int (*on_force_skip)(fq_logger_t *, fqueue_obj_t *);
	int (*on_diag)(fq_logger_t *, fqueue_obj_t *);
	long (*on_get_diff)(fq_logger_t *, fqueue_obj_t *);
	int (*on_set_QOS)(fq_logger_t *, fqueue_obj_t *, int);
	int (*on_view)(fq_logger_t *, fqueue_obj_t *, unsigned char *, int, long *, long *, bool *);
	int (*on_set_master)(fq_logger_t *, fqueue_obj_t *, int on_off_flag, char *hostname );
	float (*on_get_usage)(fq_logger_t *, fqueue_obj_t *);
	bool_t (*on_do_not_flow)(fq_logger_t *, fqueue_obj_t *, time_t);
	time_t (*on_check_competition)(fq_logger_t *, fqueue_obj_t *, action_flag_t);
	int (*on_get_big)(fq_logger_t *, fqueue_obj_t *);
};
typedef struct _fqueue_info fqueue_info_t;

struct _fqueue_info {
	int		q_header_version;
    char    *path;
    char    *qname;
    char    *desc;
    char    *XA_ON_OFF;
    int     msglen;
    int     mapping_num;
    int     max_rec_cnt;
    size_t  file_size;
    long    en_sum;
    long    de_sum;
    long    diff;
    time_t    create_time;
    time_t    last_en_time;
    time_t    last_de_time;
	long	latest_en_time; /* delay micro second */
	time_t	latest_en_time_date;
	long	latest_de_time; /* delay micro second */
	time_t  latest_de_time_date;
	int		current_big_files;
	long	big_sum;
	queue_status_t  status;
};

typedef struct _queue_obj_node queue_obj_node_t;
struct _queue_obj_node {
    fqueue_obj_t *obj;
};

typedef struct _all_queue_t all_queue_t;
struct _all_queue_t {
    int sn;
    fqueue_obj_t    *obj;
    int shmq_flag;
};

bool_t check_fq_env();
char *get_fq_home( char *buf );

int GenerateInfoFileByQueuy(fq_logger_t *l, char *path, char *qname);
int CreateFileQueue(fq_logger_t *l, char *path, char *qname);
int ExtendFileQueue(fq_logger_t *l, char *path, char *qname);
int UnlinkFileQueue(fq_logger_t *l, char *path, char *qname);
int ExportFileQueue(fq_logger_t *l, char *path, char *qname);
int ImportFileQueue(fq_logger_t *l, char *path, char *qname);

int OpenFileQueue(fq_logger_t *l, char **argv, char* hostname, char *distname, char* monID,  char *path, char *qname, fqueue_obj_t **obj);
int SetOptions(fq_logger_t *l, fqueue_obj_t *obj, int stopwatch_on_off);
int CloseFileQueue(fq_logger_t *l, fqueue_obj_t **obj);
int DiagFileQueue(fq_logger_t *l, char *path, char *qname);
int MoveFileQueue( fq_logger_t *l, char *from_path, char *from_qname, char *to_path, char *to_qname, int move_req_count);
int MoveFileQueue_obj(fq_logger_t *l, fqueue_obj_t *from_obj, fqueue_obj_t *to_obj, int move_req_count);
int MoveFileQueue_XA( fq_logger_t *l, char *from_path, char *from_qname, char *to_path, char *to_qname, int move_req_count);
int MoveFileQueue_XA_obj(fq_logger_t *l, fqueue_obj_t *from_obj, fqueue_obj_t *to_obj, int move_req_count);

int ViewFileQueue(fq_logger_t *l, char *path, char *qname, char **ptr_buf, int *len, long *seq, bool *big_flag);
int FlushFileQueue(fq_logger_t *l, char *path, char *qname);
int InfoFileQueue(fq_logger_t *l, char *path, char *qname);

int GetQueueInfo(fq_logger_t *l, char *path, char *qname, fqueue_info_t *qi);
int FreeQueueInfo(fq_logger_t *l, fqueue_info_t *qi);

int DisableFileQueue(fq_logger_t *l, char *path, char *qname);
int EnableFileQueue(fq_logger_t *l, char *path, char *qname);
int ResetFileQueue(fq_logger_t *l, char *path, char *qname);
int ForceSkipFileQueue(fq_logger_t *l, char *path, char *qname);
float GetUsageFileQueue(fq_logger_t *l, char *path, char *qname);

int create_dummy_file(fq_logger_t *l, const char *path, const char *fname, size_t size);
void *fq_mmapping(fq_logger_t *l, int fd, size_t space_size, off_t from_offset);
int fq_munmap(fq_logger_t *l, void *addr, size_t length);
char * get_str_typeOfFile(mode_t mode);
char * permOfFile(mode_t mode);

int hello_fq();
int OpenFileQueue_M(fq_logger_t *l, char *monID,  char *path, char *qname, fqueue_obj_t **obj);
int CloseFileQueue_M(fq_logger_t *l, fqueue_obj_t **obj);
int alloc_info_openes(fqueue_obj_t *obj, fqueue_info_t *qi);
int free_fqueue_info( fqueue_info_t *qi);

int HowManyAccumulated(fq_logger_t *l, char *path, char *qname);
int HowManyRemained(fq_logger_t *l, char *path, char *qname);

/* Wrapper for golang */
int COpenLog(fq_logger_t **l, char *logpath, char *logname,  char *log_level);
int CCloseLog(fq_logger_t **l);

int COpenQ(fq_logger_t *l, fqueue_obj_t **obj,  char *path, char *qname);
int CCloseQ( fq_logger_t *l, fqueue_obj_t *obj);
int CenQ( fq_logger_t *l, fqueue_obj_t *obj, unsigned char *buf, int datasize);
int CdeQ( fq_logger_t *l, fqueue_obj_t *obj, unsigned char **buf, long *seq);
int CdeQ_XA(fq_logger_t *l, fqueue_obj_t *obj, unsigned char **buf, long *seq, char **filename);
int CdeQ_XA_commit( fq_logger_t *l, fqueue_obj_t *obj, long l_seq, char *unlink_filename);
int CdeQ_XA_cancel( fq_logger_t *l, fqueue_obj_t *obj, long l_seq);
int CQmsglen( fqueue_obj_t *obj );
bool COpenConf( char *filename, conf_obj_t **obj);
bool CCloseConf( conf_obj_t **obj);
bool CGetStrConf( conf_obj_t *obj, char *keyword, char **value);
/* End of Wrapper for golang */

int all_queue_scan_CB_server(int sleep_time, bool(*your_func_name)(size_t value_sz, void *value) );
int all_queue_scan_CB_n_times(int n_times, int sleep_time, bool(*your_func_name)(size_t value_sz, void *value) );

char *fqlibVersion();

bool Make_all_fq_objects_linkedlist(fq_logger_t *l, linkedlist_t **all_q_ll );
fqueue_obj_t *find_a_qobj( fq_logger_t *l, char *qpath, char *qname, linkedlist_t *ll );

bool fq_critical_section(fq_logger_t *l, fqueue_obj_t *obj,  int en_de_flag, int lock_unlock_flag);

#ifdef __cplusplus
}
#endif

#endif
