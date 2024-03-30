/*
** fq_cache.h
*/
#ifndef _FQ_CACHE_H
#define _FQ_CACHE_H

#define FQ_CACHE_H_VERSION "1.0.0"

#include <stdio.h>
#include <pthread.h>

#define MAX_LIST_QUEUE_SIZE (100)

/*
** Data Structures and Macros
*/
typedef struct _node_t {
        char*   key;            /* name of the node */
        char*   value;          /* value for the name */
        int     flag;           /* optional flag 980617 */
        struct _node_t *next;   /* next pointer */
} node_t;

/* 
 * list structure  
 */
typedef struct _tlist_t {
        char    type;           /* type of the list */
        char*   key;            /* name of the list */
        int     length;         /* number of nodes */
        node_t *head;           /* head node */
        node_t *tail;           /* tail node */
        struct _tlist_t *next;   /* next pointer */
} tlist_t;

/* protected list structure */
typedef struct _fqlist_t {
        tlist_t  *list;
        pthread_mutex_t lock;
} fqlist_t;
typedef fqlist_t	fqcache_t;

/*------------------------------------------------------*/
/* We use fqlist_t for a stack implementation */
/* #define stack_t       fqlist_t */
#define stack_lock    fqlist_lock
#define stack_unlock  fqlist_unlock
#define stack_new     fqlist_new
#define stack_free    fqlist_free
#define stack_clear   fqlist_clear
#define stack_push    fqlist_push
#define stack_pop     fqlist_pop
#define stack_print   fqlist_print
#define stack_find_unlocked    fqlist_find_unlocked
/*------------------------------------------------------*/

/*------------------------------------------------------*/
/* We use fqlist_t for a cache implementation */
#define cache_t       fqlist_t
#define cache_lock    fqlist_lock
#define cache_unlock  fqlist_unlock
#define cache_new     fqlist_new
#define cache_free    fqlist_free
#define cache_clear   fqlist_clear
#define cache_put     fqlist_put
#define cache_add     fqlist_add
#define cache_delete  fqlist_delete
#define cache_update  fqlist_update
#define cache_getflag fqlist_getflag
#define cache_print   fqlist_print
#define cache_find_unlocked    fqlist_find_unlocked
#define cache_getkey_unlocked  fqlist_getkey_unlocked
#define cache_getkeyflag_unlocked  fqlist_getkeyflag_unlocked
#define cache_getval_unlocked  fqlist_getval_unlocked
/*------------------------------------------------------*/

/* container - protected list of list structure  */
typedef struct _container_t {
        char *name;
        int length;
        tlist_t *head;
        tlist_t *tail;
        pthread_mutex_t lock;   /* lock */
} container_t;


#ifdef __cplusplus
extern "C" {
#endif
/*
** prototypes
*/
node_t *node_new(char* key, char* value, int flag);
void node_free(node_t *t);

/* These are not safe in multi-thread. */
tlist_t* list_new(char type, char* key);
void list_Free(tlist_t **pt);
void list_clear(tlist_t *t);
node_t* list_find(tlist_t *t, char *key);
node_t* list_put(tlist_t *t, char* key, char* value, int flag);
node_t* list_push(tlist_t *t, char* key, char* value, int flag);
node_t* list_pop(tlist_t *t);
node_t* list_Add(tlist_t *t, char *key, char *value, int flag);
void list_Delete(tlist_t *t, char *key);
node_t* list_update(tlist_t *t, char *key, char* value, int flag);
char* list_getkey(tlist_t *t, char *value);
int list_getkeyflag(tlist_t *t, char *value, char** key);
char* list_getval(tlist_t *t, char *key);
int list_getflag(tlist_t *t, char *key, char** value);
void list_print(tlist_t *t, FILE* fp);

/* These are safe in multi-thread. */
void fqlist_lock(fqlist_t *t);
void fqlist_unlock(fqlist_t *t);
fqlist_t* fqlist_new(char type, char* key);
void fqlist_free(fqlist_t **pt);
void fqlist_clear(fqlist_t *t);
void fqlist_put(fqlist_t *t, char *key, char *value, int flag);
int fqlist_add(fqlist_t *t, char *key, char *value, int flag);
node_t *fqlist_push(fqlist_t *t, char *key, char *value, int flag);
node_t* fqlist_pop(fqlist_t *t);
void fqlist_delete(fqlist_t *t, char *key);
void fqlist_update(fqlist_t *t, char *key, char* value, int flag);
int fqlist_getflag(fqlist_t *t, char *key, char** value);
node_t* fqlist_find_unlocked(fqlist_t *t, char *key);
char* fqlist_getkey_unlocked(fqlist_t *t, char *value);
int fqlist_getkeyflag_unlocked(fqlist_t *t, char *value, char** key);
char* fqlist_getval_unlocked(fqlist_t *t, char *key);
void fqlist_print(fqlist_t *t, FILE* fp);

void container_lock(container_t *t);
void container_unlock(container_t *t);
container_t* container_new(char* name);
void container_free(container_t **pt);
void container_clear(container_t *t);

/* JSSONG 2000.2.22: return value void -> list_t */
tlist_t* container_put(container_t *t, char type, char* key);
tlist_t* container_add(container_t *t, char type, char* key);
/* JSSONG 2000.2.22 */

tlist_t* container_list_update(container_t *t, char type, char* key, char* itemkey, char *value, int flag);
void container_additem(container_t *t, char* key, char* itemkey, char* value, int flag);
void container_putitem(container_t *t, char* key, char* itemkey, char* value, int flag);
void container_delkey(container_t *t, char *key);
void container_deltype(container_t *t, char type);
void container_delitem(container_t *t, char* key, char* itemkey);

/* JSSONG 2000.2.22: return value void -> tlist_t */
tlist_t* container_put_unlocked(container_t *t, char type, char* key);
/* JSSONG 2000.2.22 */

tlist_t* container_find_unlocked(container_t *t, char *key);
void container_print(container_t *t, FILE* fp);
/* int get_lsof(char *path, char *qname, tlist_t *p, char** ptr_errmsg);*/

int MakeListFromDelimiterMsg( fqlist_t *li, char *a, char delimiter, int idx );

typedef int (*ListCBtype)( char *, char *, int);
int fqlist_CB( fqlist_t *li, ListCBtype userfunction );

#ifdef __cplusplus
}
#endif

#endif
