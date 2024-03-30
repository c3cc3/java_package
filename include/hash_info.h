/* vi: set sw=4 ts=4: */
/***************************************************************
 ** hash_info.h
 **/

#ifndef _HASH_INFO_H
#define _HASH_INFO_H
#include <pthread.h>

#include "fq_cache.h"
#include "fq_logger.h"

#define HASH_INFO_H_VERSION "1.0.0"

#define D_MAX_KEY_LENGTH "256"

/*
** Configuration Items
*/
enum fq_info_items {
	HASHNAME=0,
	PATH, 				/* 1 */
	DESC,				/* 2 */
	TABLE_LENGTH,		/* 3 */
	MAX_KEY_LENGTH,		/* 4 */
	MAX_DATA_LENGTH,	/* 5 */
	NUM_HASH_INFO_ITEM = MAX_DATA_LENGTH+1
};

typedef struct {
        char*   str_value;
        int     int_value;
} hash_info_item_t;

typedef struct {
        char* name;
        hash_info_item_t item[NUM_HASH_INFO_ITEM];
        fqlist_t* options;
        time_t  mtime;
        pthread_mutex_t lock;
} hash_info_t;

#ifdef __cplusplus
extern "C" {
#endif

/*
** Prototypes
*/
long set_mask(long mask, int bit, char* yesno);
int get_mask(long mask, int bit);

hash_info_t* new_hash_info(const char* name);
void free_hash_info_unlocked(hash_info_t **ptrt);
void free_hash_info(hash_info_t **ptrt);
void clear_hash_info_unlocked(hash_info_t *t);
void clear_hash_info(hash_info_t *t);
char* get_infokeyword(int id);
int get_infoindex(char* keyword);
void lock_hash_info(hash_info_t *t);
void unlock_hash_info(hash_info_t *t);
void set_hash_info_item_unlocked(hash_info_t *t, int id, char* value);
char* 	hash_info_s(hash_info_t *t, int id, char* dst);
int 	hash_info_i(hash_info_t *t, int id);
char* 	get_info(hash_info_t *t, char* keyword, char* dst);
void hash_info_setdefault_unlocked(hash_info_t *t);
void hash_info_setdefault(hash_info_t* t);
int check_hash_info_unlocked(hash_info_t *t);
int read_hash_info(hash_info_t *t, const char* filename);
int read_multi_hash_info(hash_info_t *t, const char* filename, const char *svrname);
void print_hash_info(hash_info_t *t, FILE* fp);
int _hash_info_isyes(hash_info_t *_hash_info, int item);
char* get_hash_info(hash_info_t *t, char* keyword, char* dst);

#ifdef __cplusplus
}
#endif

#endif
