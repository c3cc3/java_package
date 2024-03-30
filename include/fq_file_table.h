/*
** fq_file_table.h
** Include this header file for using record locking library.
*/
#define FQ_FILE_TABLE_H_VERSION "1.0.0"

#ifndef _FQ_FILE_TABLE_H
#define _FQ_FILE_TABLE_H

#include "fq_logger.h"

#define MAX_LINE_SIZE (1024)
#define SEPARATOR       "\t"

#ifdef __cplusplus
extern "C"
{
#endif

/* This is a structure of file table */
typedef struct cron_entry {
  struct cron_entry     *next;

  /* structure of crontab file */
  char                  *mn; /* min */
  char                  *hr; /* hour */
  char                  *day; /* day */
  char                  *mon; /* month */
  char                  *wkd; /* weekend */
  char                  *cmd; /* command */
} CRON;

typedef struct _file_table_obj_t file_table_obj_t;
struct _file_table_obj_t {
	fq_logger_t *l;
	char    *filename; /* config file name with full-path */
	char 	line_buf[MAX_LINE_SIZE];
	time_t 	prv_time; /* previous loading time */
	CRON 	*head;
	CRON	*entry_ptr;
	int  (*on_print)( file_table_obj_t *obj);
};

int open_file_table( fq_logger_t *l, file_table_obj_t **obj, char *path_file);
int close_file_table( fq_logger_t *l, file_table_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif
