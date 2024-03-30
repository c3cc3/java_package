/*
** mem_queue.h
*/
#ifndef _MEM_QUEUE_H
#define _MEM_QUEUE_H

#define MEM_QUEUE_H_VERSION "1.0,0"

#ifndef TRUE
#define TRUE 1
#endif
#ifndef FALSE
#define FALSE 0
#endif

#ifdef __cplusplus
extern "C" 
{
#endif

/* getItem(), putItem() return values */
#define MEM_QUEUE_FULL	0
#define MEM_QUEUE_EMPTY 0

#define MAX_ITEMS    1000
#define MAX_MSG_LEN  4096

typedef struct _queue_message
{
	size_t	len;
	unsigned char msg[MAX_MSG_LEN];
} queue_message_t;

typedef struct circularQueue_s
{
     int     first;
     int     last;
     int     validItems;
	 queue_message_t	qm[MAX_ITEMS]; 
     // int     data[MAX_ITEMS];
} circularQueue_t;

typedef struct _mem_queue_obj_t mem_queue_obj_t;
struct _mem_queue_obj_t {
	circularQueue_t Queue;

	pthread_condattr_t cond_attr;
	pthread_cond_t Buffer_Not_Full;
	pthread_cond_t Buffer_Not_Empty;

	pthread_mutexattr_t mtx_attr;
	pthread_mutex_t mtx;

	int		(* on_isEmpty)(mem_queue_obj_t *);
	int		(* on_isFull)(mem_queue_obj_t *);
	int		(* on_putItem)( mem_queue_obj_t *, queue_message_t *);
	int		(* on_getItem)( mem_queue_obj_t *, queue_message_t *);
	void	(* on_printQueue)( mem_queue_obj_t *);
};

int open_mem_queue_obj( mem_queue_obj_t **obj);
int close_mem_queue_obj( mem_queue_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif
