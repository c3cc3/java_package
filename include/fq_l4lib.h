#ifndef FQ_L4LIB_H_
#define FQ_L4LIB_H_

#include "fq_logger.h"

#ifdef __cplusplus
extern "C"
{
#endif

#define MAX_PORTS 8
#define SOCK_PATH_LEN 256

#define INCREASE_COUNT 1
#define DECREASE_COUNT 2

typedef struct _touch_thread_args {
    int index;
    int shmkey;
} touch_thread_args_t;

typedef enum { STATUS_IDLE=0, STATUS_BUSY, STATUS_BAD} port_status_t;
typedef enum { LOAD_BALANCE_RR=0, LOAD_BALANCE_LC} load_balance_type_t;

typedef struct	_L4_port L4_port_t;
struct _L4_port {
	char    sock_path[128];
    pid_t   pid;
	time_t	last_balance_time;
    time_t  touch_time;
	int		connections;
};

typedef struct _Heartbeat_Table Heartbeat_Table_t;
struct _Heartbeat_Table {
	int		listen_port;
	char	L4_switch_name[32];
	pid_t	listener_pid;			/* listener process id */
	load_balance_type_t		type; /* Load Balancing type RR=0 or LC=1 */
	int		next_port;
	time_t	touch_time;
	
    // pthread_condattr_t cond_attr;
    // pthread_cond_t   svr_wait_cond;
    // pthread_cond_t   cli_wait_cond;
    // pthread_mutexattr_t mtx_attr;
    pthread_mutex_t  mtx;

	L4_port_t	L4[MAX_PORTS];
};

void SetLimit(int value);
int L4_adaptor(fq_logger_t *l, key_t shmkey, char *connection_path, Heartbeat_Table_t **ptr_p, int *index);
void L4_connection_update(Heartbeat_Table_t *p, int index_port, int method);
int	L4_get_connection_count( Heartbeat_Table_t *p, int index_port); 

#ifdef __cplusplus
}
#endif

#endif /* #ifndef FQ_L4LIB_H_ */
