/*
** fq_trace.h
*/
#ifndef _FQ_TRACE_H
#define _FQ_TRACE_H

#include "fq_mask.h"

#ifdef __cplusplus
extern "C"
{
#endif

#define MAX_LOG_LINE_SIZE 8192

typedef enum { TRACE_EVENT=0, TRACE_SESS, TRACE_SOCK, TRACE_CONFIG, TRACE_CONV, TRACE_DEBUG, TRACE_DB, TRACE_ERROR, NUM_TRACE_FLAG } loglevel_t;

char* fq_trace_keywords[NUM_TRACE_FLAG] = {
	"TRACE_EVENT",
	"TRACE_SESS",
	"TRACE_SOCK",
	"TRACE_CONFIG",
	"TRACE_CONV",
	"TRACE_DEBUG",
	"TRACE_DB",
	"TRACE_ERROR"
};

typedef struct _trace_obj_t  trace_obj_t;
struct _trace_obj_t {
	char	*path;
	char	*filename;
	char	*pathfile;
	FILE	*fp;
	char	*online_define_path_file;
	char	*trace_flags;
	int		online_flag;
	int		stdout_flag;
	int		print_max_length;
	mask_obj_t *mask_obj;
	void	(*on_trace)( trace_obj_t *, int, const char *, ...);
};
int open_trace_obj( char *path, char *filename, char *trace_flags, int online_flag, int stdout_flag, int print_max_length,  trace_obj_t **obj);
int close_trace_obj( trace_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif
