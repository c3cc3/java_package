#ifndef _FQ_SESSION_H
#define _FQ_SESSION_H

#define FQ_SESSION_H_VERSION "1.0.0"

#include "fq_logger.h"

#ifdef __cplusplus
extern "C" {
#endif

/*
** Session Control
*/
#define SESSION_TIMEOUT_SEC 	600
#define MAX_SESSION_LIMIT       250 /* concurent users, If you decide this value according to available memory size. */
#define SKEY_SIZE				32
#define FQ_PROTOCOL_LEN 512

typedef struct {
	char    skey[SKEY_SIZE+1];
	long	tot_req;
	char*   client_addr;
	time_t  last_access_time;
	fqueue_obj_t	*obj;
	pthread_cond_t  cond;
} sess_t;

typedef struct {
	pthread_mutex_t lock;
	sess_t		*sess;
	time_t  	set_time;
} SessionTable;

int set_skey(fq_logger_t *l, sess_t *s);
int trylock_session(fq_logger_t *l, int sid);
int lock_session(fq_logger_t *l, int sid);
int unlock_session(fq_logger_t *l, int sid);
sess_t* get_session(fq_logger_t *l, int sid);
void init_sess( fq_logger_t *l );
sess_t* sess_new(fq_logger_t *l, char *client_addr);
void sess_free(fq_logger_t *l, sess_t **s_ptr);
int sess_findskey(fq_logger_t *l, char* skey);
void sess_remove_unlocked(fq_logger_t *l, int sid, int eventid, int sn, int rc);
int sess_add_unlocked(fq_logger_t *l, char* skey, char *client_addr);
int sess_register_unlocked(fq_logger_t *l, char *skey);
int set_skey(fq_logger_t *l, sess_t *s);


#ifdef __cplusplus
}
#endif

#endif
