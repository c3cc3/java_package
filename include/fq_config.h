/***************************************************************
 ** fq_config.h
 **/

#ifndef _FQ_CONFIG_H
#define _FQ_CONFIG_H

#define FQ_CONFIG_H_VERSION "1.0.0"

#include <pthread.h>

#include "fq_cache.h"
#include "fq_global.h"
#include "fq_logger.h"

/*
** Configuration Items
*/
enum config_items {
	/* Server Define Part. These items are mandatories. */
	SVR_PROG_NAME=0,
	SVR_HOME_DIR, 			/* 1 */
	SVR_LOG_DIR,			/* 2 */

	/* Queue define Part */
	EN_QUEUE_PATH,			/* 3 */
	EN_QUEUE_NAME,			/* 4 */
	DE_QUEUE_PATH,			/* 5 */
	DE_QUEUE_NAME,			/* 6 */
	QUEUE_MSG_LEN,			/* 7 */
	QUEUE_RECORDS,			/* 8 */
	QUEUE_WAIT_MODE,		/* 9 */
	
	/* TCP common Part */
	TCP_HEADER_TYPE,		/* 10 */
	TCP_HEADER_LEN,			/* 11 */

	/* Listening Server Part */
	TCP_LISTEN_IP,			/* 12 */
	TCP_LISTEN_PORT,		/* 13 */
	TCP_LISTEN_TIMEOUT,		/* 14 */
	TCP_WAIT_TIMEOUT,		/* 15 */

	/* Connecting Client Part */
	TCP_CONNECT_IP,			/* 16 */
	TCP_CONNECT_PORT,		/* 17 */
	TCP_CONNECT_TIMEOUT,	/* 18 */
	TCP_CONNECT_RETRY_COUNT,/* 19 */
	
	/* TCI Formating Part */
	FORMAT_FILE_PATH,		/* 20 */
	FORMAT_FILE_NAME,		/* 21 */

	NUM_CONFIG_ITEM=FORMAT_FILE_NAME+1
};

typedef struct {
        char*   str_value;
        int     int_value;
} configitem_t;

typedef struct {
        char* name;
        configitem_t item[NUM_CONFIG_ITEM];
        fqlist_t* options;
        long traceflag;
        time_t  mtime;
        pthread_mutex_t lock;
} config_t;


/*
** default settings
*/
#define D_TCP_LISTEN_TIMEOUT      	"60"
#define D_TCP_WAIT_TIMEOUT      	"30"
#define D_TCP_CONNECT_TIMEOUT      	"5"

/*
** Trace masks
*/
enum trace_levels { _ERROR=0, _DEBUG, _QUEUE, _SOCKET, _INFO, _TRACE, NUM_TRACE_FLAG=_TRACE+1 };

#if 0
#define _ERROR          0
#define _DEBUG         	1
#define _QUEUE         	2
#define _SOCKET         3
#define _INFO         	4
#define _TRACE         	5
#define NUM_TRACE_FLAG  6
#endif

#ifdef __cplusplus
extern "C" {
#endif
/*
** Prototypes
*/
long set_mask(long mask, int bit, char* yesno);
int get_mask(long mask, int bit);
config_t* new_config(const char* name);
void free_config_unlocked(config_t **ptrt);
void free_config(config_t **ptrt);
void clear_config_unlocked(config_t *t);
void clear_config(config_t *t);
char* get_cfgkeyword(int id);
char* get_tracekeyword(int id);
int get_cfgindex(char* keyword);
void lock_config(config_t *t);
void unlock_config(config_t *t);
void set_configitem_unlocked(config_t *t, int id, char* value);
char* configs(config_t *t, int id, char* dst);
char* get_config(config_t *t, char* keyword, char* dst);
int configi(config_t *t, int id);
void config_setdefault_unlocked(config_t *t);
void config_setdefault(config_t* t);
int check_config_unlocked(config_t *t);
int read_config(config_t *t, const char* filename);
int read_multi_config(config_t *t, const char* filename, const char *svrname);
void print_config(config_t *t, FILE* fp);
int _config_isyes(config_t *_config, int item);

#ifdef __cplusplus
}
#endif

#endif
