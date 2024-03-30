/*
** fq_file_list.h
** Include this header file for using record locking library.
*/
#define FQ_FILE_LIST_H_VERSION "1.0.0"

#ifndef _FQ_FILE_LIST_H
#define _FQ_FILE_LIST_H

#include "fq_logger.h"

/*
** This value is a total size of a file.
*/
#define MAX_LINE_SIZE (1024*8)
#define SEPARATOR       "\t"

#ifdef __cplusplus
extern "C"
{
#endif

/* This is a structure of file table */
typedef struct file_entry {
  struct file_entry     *next;

  /* structure of list file */
  char                  *value; /* command */
} FILELIST;

typedef struct _file_list_obj_t file_list_obj_t;
struct _file_list_obj_t {
	fq_logger_t *l;
	char    *filename; /* file name with full-path */
	char 	line_buf[MAX_LINE_SIZE]; // temporay line buffer for reading a file.
	int		count;
	time_t 	prv_time; /* previous loading time */
	FILELIST 	*head;
	FILELIST	*entry_ptr;
	int  (*on_print)( file_list_obj_t *obj);
};

int open_file_list( fq_logger_t *l, file_list_obj_t **obj, char *path_file);
int append_file_list(fq_logger_t *l, file_list_obj_t *obj, char *new_filename);
int close_file_list( fq_logger_t *l, file_list_obj_t **obj);


#ifdef __cplusplus
}
#endif

#endif
