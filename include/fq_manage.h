/* vi: set sw=4 ts=4: */
/***************************************************************
 ** fq_manage.h
 **/

#ifndef _FQ_MANAGE_H
#define _FQ_MANAGE_H
#include <pthread.h>
#include <stdbool.h>

#include "fqueue.h"
#include "fq_cache.h"
#include "fq_logger.h"
#include "parson.h"
#include "fq_linkedlist.h"

#define FQ_MANAGE_H_VERSION "1.0.2"

/*
** 1.0.1 : 2013/11/08/ add: FQ_FORCE_SKIP
** 1.0.2 : 2014/03/27  add: FQ_EXTEND
*/

#define FQ_LIST_FILE_NAME "ListFQ.info"
#define SHMQ_LIST_FILE_NAME "ListSHMQ.info"

#define UNLINK	0


/* Waring : When you add new value, Add Command string to cmd_table in fq_manage.c */
typedef enum { FQ_CREATE=0, FQ_UNLINK, FQ_DISABLE, FQ_ENABLE, FQ_RESET, FQ_FLUSH, FQ_INFO, FQ_FORCE_SKIP, FQ_DIAG, FQ_EXTEND, FQ_PRINT, SHMQ_CREATE, SHMQ_UNLINK, SHMQ_RESET, SHMQ_FLUSH, FQ_EXPORT, FQ_IMPORT, FQ_GEN_INFO, SHMQ_GEN_INFO, FQ_DATA_VIEW } fq_cmd_t;

struct _fq_node {
	char*	qname;
	int		msglen;
	int 	mapping_num;
	int		multi_num;
	char*	desc;
	int		XA_flag;
	int		Wait_mode_flag;
	int		status;
	struct	_fq_node *next;
};
typedef struct _fq_node fq_node_t;

struct _dir_list {
	char* 	dir_name;
	int		length;
	fq_node_t	*head;
	fq_node_t	*tail;
	struct	_dir_list *next;
};
typedef struct _dir_list dir_list_t;

struct _fq_container {
	char	*name;
	int		length;
	dir_list_t	*head;
	dir_list_t	*tail;
};
typedef struct _fq_container fq_container_t;

struct fq_ps_info {
	pid_t	pid;
	char *run_name;
	char *exe_name;
	char *cwd;
	char *user_cmd;
	char *arg_0;
	char *arg_1;
	long uptime_bin;
	char *uptime_ascii;
	bool isLive;
	time_t	mtime; /* last modified time of a file */
	bool restart_flag; /* true: When the process was kill and block, Automatic start  */
	int	heartbeat_sec;
	// char *run_sh;
	// char *stop_sh;
};
typedef struct fq_ps_info fq_ps_info_t;

typedef struct _ps_t {
	fq_ps_info_t *psinfo;
	struct _ps_t	*next;
} ps_t;
typedef struct _pslist_t {
	int length;
	ps_t	*head;
	ps_t	*tail;
	struct _pslist_t *next;
} pslist_t;

typedef bool (*fqCBtype)(fq_logger_t *, char *, fq_node_t *);

/* fqpm eventlog */
typedef struct _fqpm_eventlog_obj_t fqpm_eventlog_obj_t;
struct _fqpm_eventlog_obj_t {
	fq_logger_t *l;

	char *path;
	char *fn;
	time_t	opened_time;
	FILE	*fp;

	bool(*on_eventlog)(fq_logger_t *, fqpm_eventlog_obj_t *, char *, char *, char *, ...);
};

/* It uses for create and unlink with queue list file */
typedef struct _queues_list_t queues_list_t;
struct _queues_list_t {
    char    qpath[256]; 
    char    qname[256];
	int		mapping_num;
	int		multi_num;
	int		msglen;
};

bool open_fqpm_eventlog_obj( fq_logger_t *l, char *path, fqpm_eventlog_obj_t **obj);
bool close_fqpm_eventlog_obj(fq_logger_t *l, fqpm_eventlog_obj_t **obj);


int fq_manage_one(fq_logger_t *l, char *path, char *qname, fq_cmd_t cmd );
int fq_manage_all(fq_logger_t *l, char *path, fq_cmd_t cmd);

int load_fq_container( fq_logger_t *l,  fq_container_t **fq_c );
int load_shm_container( fq_logger_t *l,  fq_container_t **fq_c );
fq_container_t* fq_container_new( char *name);
void fq_container_free(fq_container_t **pt);

dir_list_t* fq_container_put(fq_container_t *t, char* key);
void dir_list_free(dir_list_t **pt);
fq_node_t* dir_list_put(dir_list_t *t, char* qname, int msglen, int mapping_num, int multi_num, char *desc, int XA_flag, int Wait_mode_flag);
dir_list_t* dir_list_new(char* dir_name);
void fq_node_free(fq_node_t *t);
fq_node_t *fq_node_new(char* qname, int msglen, int mapping_num, int multi_num, char *desc, int XA_flag, int Wait_mode_flag);
void dir_list_print(dir_list_t *t, FILE* fp);
int fq_container_search_one(fq_container_t *t, fq_node_t **fq,  char *dir, char *qname);

void fq_container_print(fq_container_t *t, FILE* fp);

int fq_container_manage( fq_logger_t *l, fq_container_t *t,  int CMD);
int fq_directory_manage( fq_logger_t *l, fq_container_t *t, char *dirname, int CMD);

int fq_queue_manage( fq_logger_t *l, fq_container_t *t, char *dirname, char *qname, int CMD);
int fq_queue_manage_with_queues_list_t( fq_logger_t *l, queues_list_t *qlist, int CMD);

int get_cmd( char *cmd );
bool fq_container_CB( fq_logger_t *l, fq_container_t *fqc, fqCBtype userCB);

/*
** manage processes in the framework.
*/
bool check_duplicate_me_on_fq_framework( fq_logger_t *l, char *config_filename);
int get_exe_for_pid(pid_t pid, char *buf, size_t bufsize);
int get_cwd_for_pid(pid_t pid, char *buf, size_t bufsize);
bool IsValidPidNumber(char *pid_string);
bool get_progname_argus_for_pid(pid_t pid, char *progname, fqlist_t *li, int *argu_count);
bool regist_me_on_fq_framework( fq_logger_t *l, const char *path, pid_t pid, bool restart_flag, int heartbeat_sec);
bool touch_me_on_fq_framework( fq_logger_t *l, const char *path, pid_t pid);
bool delete_me_on_fq_framework( fq_logger_t *l, const char *path, pid_t pid);
bool check_fq_framework_process( fq_logger_t *l, const char *path,  pid_t pid, bool all_flag );
bool get_ps_info( fq_logger_t *l, const char *path, pid_t pid, fq_ps_info_t *ps_info, bool debug_flag);

pslist_t *pslist_new();
void pslist_free(pslist_t **t);
ps_t *pslist_put(pslist_t *t, fq_ps_info_t *psinfo);
bool make_ps_list( fq_logger_t *l, char *path, pslist_t *t );
bool conv_from_pslist_to_json(fq_logger_t *l, pslist_t *ps_list, JSON_Value *jroot, JSON_Object *jobj, JSON_Array *jary_pids, char **dst, int *list_cnt, bool pretty_flag);
bool is_live_process(pid_t pid);
bool is_block_process( fq_logger_t *l, const char *path, pid_t pid, int health_check_term_sec);
void log_call_process_info( fq_logger_t *l);


#ifdef __cplusplus
}
#endif

#endif
