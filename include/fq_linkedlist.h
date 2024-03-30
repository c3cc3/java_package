/*
** fq_linkedlist.h
*/
#ifndef _FQ_LINKEDLIST_H
#define _FQ_LINKEDLIST_H

#include <stdio.h>
#include <stdbool.h>
#include <pthread.h>
#include "fq_logger.h"

/*
** Data Structures and Macros
*/
typedef struct _ll_node_t {
        char*   key;            /* name of the node */
        void*   value;          /* value for the name */
		size_t	value_sz;
        struct _ll_node_t *next;   /* next pointer */
} ll_node_t;

/* 
 * list structure  
 */
typedef struct _linkedlist_t {
        char*   key;            /* name of the linkedlist */
        int     length;         /* number of nodes */
        ll_node_t *head;           /* head node */
        ll_node_t *tail;           /* tail node */
        struct _linkedlist_t *next;   /* next pointer */
        pthread_mutex_t lock;
} linkedlist_t;

typedef bool (*linkedlist_CB)(size_t value_sz, void *value);

#ifdef __cplusplus
extern "C" {
#endif

/*
** prototypes
*/
// ll_node_t *ll_node_new(char* key, void* value, size_t sz_value);
// void ll_node_free(ll_node_t *t);
linkedlist_t* linkedlist_new(char* key);
void linkedlist_free(linkedlist_t **pt);
ll_node_t* linkedlist_put(linkedlist_t *t, char *key, void *value, size_t sz_value);
void linkedlist_del(linkedlist_t *t, char *key);

ll_node_t * linkedlist_find(fq_logger_t *l, linkedlist_t *t, char *key);

void linkedlist_scan(linkedlist_t *t, FILE* fp);
bool linkedlist_callback(linkedlist_t *t, linkedlist_CB usrFP);
void linkedlist_sort(linkedlist_t *t);


#ifdef __cplusplus
}
#endif

#endif
