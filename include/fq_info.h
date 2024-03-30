/* vi: set sw=4 ts=4: */
/***************************************************************
 * fq_info.h
 *
*/

#ifndef _FQ_INFO_H
#define _FQ_INFO_H
#include <pthread.h>
#include <stdbool.h>

#include "fq_cache.h"
#include "fq_logger.h"

#define FQ_INFO_H_VERSION "1.0.0"

/*
** Configuration Items
*/
enum fq_info_items {
	QNAME=0,
	QPATH, 				/* 1 */
	MSGLEN,				/* 2 */
	MMAPPING_NUM,		/* 3 */
	MULTI_NUM,			/* 4 */
	DESC,				/* 5 */
	XA_MODE_ON_OFF,			/* 6 */
	WAIT_MODE_ON_OFF,			/* 7 */
	MASTER_USE_FLAG, /* 8 */
	MASTER_HOSTNAME, /* 9 */
	NUM_FQ_INFO_ITEM = MASTER_HOSTNAME+1
};

typedef struct {
        char*   str_value;
        int     int_value;
} fq_info_item_t;

typedef struct {
        char* name;
        fq_info_item_t item[NUM_FQ_INFO_ITEM];
        fqlist_t* options;
        time_t  mtime;
        pthread_mutex_t lock;
} fq_info_t;


#define D_MSGLEN      			"4096"
#define D_XA_MODE_ON_OFF		"OFF"
#define D_WAIT_MODE_ON_OFF		"OFF"

#ifdef __cplusplus
extern "C" {
#endif

/*
** Prototypes
*/
long set_mask(long mask, int bit, char* yesno);
int get_mask(long mask, int bit);

bool generate_fq_info_file( fq_logger_t *l, char *qpath, char *qname, fq_info_t *t);
bool check_qname_in_listfile(fq_logger_t *l, char *qpath, char *qname);
bool check_qname_in_shm_listfile(fq_logger_t *l, char *qpath, char *qname);
bool append_new_qname_to_ListInfo( fq_logger_t *l, char *qpath, char *qname);
bool append_new_qname_to_shm_ListInfo( fq_logger_t *l, char *qpath, char *qname);
bool delete_qname_to_ListInfo( fq_logger_t *l, int kind, char *qpath, char *qname);

fq_info_t* new_fq_info(const char* name);
void free_fq_info_unlocked(fq_info_t **ptrt);
void free_fq_info(fq_info_t **ptrt);
void clear_fq_info_unlocked(fq_info_t *t);
void clear_fq_info(fq_info_t *t);
char* get_infokeyword(int id);
int get_infoindex(char* keyword);
void lock_fq_info(fq_info_t *t);
void unlock_fq_info(fq_info_t *t);
void set_fq_info_item_unlocked(fq_info_t *t, int id, char* value);
char* 	fq_info_s(fq_info_t *t, int id, char* dst);
int 	fq_info_i(fq_info_t *t, int id);
char* 	get_info(fq_info_t *t, char* keyword, char* dst);
void fq_info_setdefault_unlocked(fq_info_t *t);
void fq_info_setdefault(fq_info_t* t);
int check_fq_info_unlocked(fq_info_t *t);
int read_fq_info(fq_logger_t *l, fq_info_t *t, const char* filename);
int read_multi_fq_info(fq_info_t *t, const char* filename, const char *svrname);
void print_fq_info(fq_info_t *t, FILE* fp);
int _fq_info_isyes(fq_info_t *_fq_info, int item);
char* get_fq_info(fq_info_t *t, char* keyword, char* dst);
int get_fq_info_i(fq_info_t *t, char* keyword);


#ifdef __cplusplus
}
#endif

#endif
