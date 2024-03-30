/*
** fq_delimiter_list.h
** Include this header file for using record locking library.
*/
#define FQ_DELIMITER_LIST_H_VERSION "1.0.0"

#ifndef _FQ_DELIMITER_LIST_H
#define _FQ_DELIMITER_LIST_H

#include "fq_logger.h"
#include <stdbool.h>

#define MAX_SRC_SIZE (8192)

#ifdef __cplusplus
extern "C"
{
#endif

/* This is a structure of linked_list */
typedef struct delimiter_list {
  struct delimiter_list     	*next;
  char                  		*value; 
} delimiter_list_t;

typedef bool (*ListCB)(char *);

typedef struct _delimiter_list_obj_t delimiter_list_obj_t;
struct _delimiter_list_obj_t {
	fq_logger_t *l;
	int		count; 	/* count of elements  */
	delimiter_list_t 	*head;
	delimiter_list_t	*entry_ptr;
	int  (*on_print)( delimiter_list_obj_t *obj);
};

int open_delimiter_list( fq_logger_t *l, delimiter_list_obj_t **obj, char *src, char delimiter);
int close_delimiter_list( fq_logger_t *l, delimiter_list_obj_t **obj);
bool ListCallback( delimiter_list_obj_t *obj, ListCB usrFP);

#ifdef __cplusplus
}
#endif

#endif
