/* vi: set sw=4 ts=4: */
/*
 * fqueue.h
 */
#ifndef _FQUEUE_H
#define _FQUEUE_H

#include <pthread.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/msg.h>
#include <sys/sem.h>

#include "fq_logger.h"
#include "fq_semaphore.h"
#include "fq_unixQ.h"
#include "fq_eventlog.h"
#include "fq_external_env.h"

#ifdef __cplusplus
extern "C"
{
#endif

#define FQ_TIME_LENGTH  	19 /* yyyy/mm/dd hh:mm:DD */
#define MAX_DESC_LEN        128
#define DEFAULT_QOS_VALUE   0  /* default,  The bigger the value is fast. */
#define EQ_FULL_RETURN_VALUE    0
#define DQ_EMPTY_RETURN_VALUE   0 
#define MANAGER_STOP_RETURN_VALUE -99

#define FQ_HEADER_SIZE      	4

typedef enum { EN_NORMAL_MODE=0, EN_CIRCULAR_MODE } enQ_mode_t;
typedef enum { NO_WAIT_MODE=0, WAIT_MODE, FORCE_DE_Q_MODE } queue_mode_t;
typedef enum { XA_OFF=0, XA_ON, XA_ERR=-1 } XA_flag_t;
typedef enum { QUEUE_ENABLE=0, QUEUE_EN_DISABLE, QUEUE_DE_DISABLE, QUEUE_ALL_DISABLE } queue_status_t;

typedef struct _mmap_header mmap_header_t;
struct _mmap_header {
	queue_mode_t 	mode; /* NO_WAIT_MODE, WAIT_MODE, FORCE_DE_Q_MODE */
	XA_flag_t		XA_flag;
	char			key_char; /* semaphore key */
	queue_status_t	status;
	size_t 	msglen; /* same to MMAP_RECORD_LEN  */
	int		current_big_files_en;
	int		current_big_files_de;
	int		max_rec_cnt;
	int		pagesize;
	long 	en_sum; /* accumulated en_total count. */
	long 	de_sum; /* accumulated de_total count. */
	size_t	mapping_num;
	size_t	mapping_len;
	size_t	multi_num;
	size_t 	file_size; /* created file/shm size */
	size_t	limit_file_size; /* If filesize reachs limit, saving is omitted. */
	char	path[128];
	char	qname[128];
	char	desc[MAX_DESC_LEN+1];
	char	qfilename[128];
	time_t	create_time;  /* bin_time format */
	time_t	last_en_time;
	time_t	last_de_time;
	long	latest_en_time; /* 최대 지연 시간 en */
	long	latest_de_time; /* 최대 지연 시간 de */
	int		last_en_row;
	int		oldest_en_row;
	int		QOS; /* 임의 지연 셋팅 */
};

typedef struct _headfile_obj_t	headfile_obj_t;
struct _headfile_obj_t {
	fq_logger_t	*l;
	char	*name;
	int		fd;
	struct stat	st;
	mmap_header_t	*h; /* mmapping pointer. */
	semaphore_obj_t	*sema;
	int (*on_head_print)(fq_logger_t *, headfile_obj_t *);
};

typedef struct _datafile_obj_t	datafile_obj_t;
struct _datafile_obj_t {
	fq_logger_t	*l;
	char	*name;
	int		fd;
	struct stat	st;
	void	*d;  /* mapping pointer. */
	off_t   available_from_offset;
	off_t   available_to_offset;
};

typedef struct _fqueue_obj_t fqueue_obj_t;
struct _fqueue_obj_t {
	char		*path;
	char		*qname;
	char		*eventlog_path;
	pid_t		pid;
	pthread_t	tid;
	fq_logger_t	*l;
	int			stop_signal;

	headfile_obj_t	*h_obj; /*  header file object */
	datafile_obj_t	*d_obj; /* data file object */

	unixQ_obj_t		*uq_obj; /* unix queue object */

	eventlog_obj_t	*evt_obj; /* file eventlog object */
	external_env_obj_t	*ext_env_obj;
	pthread_mutex_t	mutex;

	int (*on_en)(fq_logger_t *, fqueue_obj_t *, enQ_mode_t, const unsigned char *, int, size_t, long *);
	int (*on_de)(fq_logger_t *, fqueue_obj_t *, unsigned char *, int, long *);
	int (*on_de_XA)(fq_logger_t *, fqueue_obj_t *, unsigned char *, int, long *);
	int (*on_commit_XA)(fq_logger_t *, fqueue_obj_t *, long );
	int (*on_disable)(fq_logger_t *, fqueue_obj_t *);
	int (*on_enable)(fq_logger_t *, fqueue_obj_t *);
	int (*on_reset)(fq_logger_t *, fqueue_obj_t *);
	long (*on_get_diff)(fq_logger_t *, fqueue_obj_t *);
	int (*on_set_QOS)(fq_logger_t *, fqueue_obj_t *, int);
};

typedef struct _fqueue_info fqueue_info_t;
struct _fqueue_info {
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
	long	latest_de_time; /* delay micro second */
};

int CreateFileQueue(fq_logger_t *l, char *path, char *qname);
int UnlinkFileQueue(fq_logger_t *l, char *path, char *qname);

int OpenFileQueue(fq_logger_t *l, char *path, char *qname, fqueue_obj_t **obj);
int CloseFileQueue(fq_logger_t *l, fqueue_obj_t **obj);

int GetQueueInfo(fq_logger_t *l, char *path, char *qname, fqueue_info_t *qi);
int FreeQueueInfo(fq_logger_t *l, fqueue_info_t *qi);

int DisableFileQueue(fq_logger_t *l, char *path, char *qname);
int EnableFileQueue(fq_logger_t *l, char *path, char *qname);
int ResetFileQueue(fq_logger_t *l, char *path, char *qname);

/* 
int unlink_semaphore( fq_logger_t *l, char *path, char key_char);
*/


#ifdef __cplusplus
}
#endif

#endif
