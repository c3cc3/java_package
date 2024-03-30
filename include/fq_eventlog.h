/* vi: set sw=4 ts=4: */
/*
 * fq_eventlog.h
 */
#ifndef _FQ_EVENTLOG_H
#define _FQ_EVENTLOG_H

#include <stdio.h>
#include <string.h>

#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/msg.h>
#include <errno.h>

#include "fq_logger.h"
#include "fq_unixQ.h"

#define FQ_EVENTLOG_H_VERSION "1.0.0"

#if 0
#define _USE_EVENTLOG
#else
#undef _USE_EVENTLOG
#endif

#define EVENT_LOG_PATH "/tmp"
#define UNIXQ_EVENTLOG_ID 999999

#ifdef __cplusplus
extern "C"
{
#endif

typedef enum { EVENT_FQ_CREATE=0, EVENT_FQ_OPEN, EVENT_FQ_EN, EVENT_FQ_DE_AUTO, EVENT_FQ_DE, EVENT_FQ_DE_XA, EVENT_FQ_COMMIT, EVENT_FQ_CANCEL, EVENT_FQ_CLOSE, EVENT_FQ_UNLINK, EVENT_FQ_RESET, EVENT_FQ_ENABLE, EVENT_FQ_DISABLE, EVENT_FQ_SETQOS, EVENT_FQ_EXTEND } fq_event_t;

typedef struct _eventlog_obj_t eventlog_obj_t;
struct _eventlog_obj_t {
	fq_logger_t *l;

	char *path;
	char *fn;
	time_t	opened_time;
	FILE	*fp;

	int(*on_eventlog)(fq_logger_t *, eventlog_obj_t *, fq_event_t event_id, ...);
	void(*on_en_eventlog)(fq_logger_t *, unixQ_obj_t *, fq_event_t event_id, ...);
};

#define MMAP_EVENTLOG_DIR_NAME  "/tmp"
#define MMAP_EVENTLOG_FILE_NAME "FQ_STATISTIC.DB"
#define EVENT_CODE_LEN	2

typedef enum { E_CR=0, E_OP, E_EN, E_DA, E_DN, E_DX, E_CM, E_CL, E_UL, E_RS, E_EA, E_DS, E_QO, E_EX, KINDS_OF_EVENTLOG} kind_event_t;

typedef struct _mmap_eventlog_row mmap_eventlog_row_t;
struct _mmap_eventlog_row {
	char	evt_code[EVENT_CODE_LEN+1];
	char	desc[36];
	long	count;
	long	varidation;
};

typedef struct _mmap_eventlog mmap_eventlog_t;
struct _mmap_eventlog {
	mmap_eventlog_row_t	rows[KINDS_OF_EVENTLOG];
	int		nums_event;
};

typedef struct _mmap_eventlog_obj_t	mmap_eventlog_obj_t;
struct _mmap_eventlog_obj_t {
	fq_logger_t	*l;
	char	*name;
	int		fd;
	size_t	mapping_len;
	struct stat st;
	mmap_eventlog_t	*e; /* mmapping pointer. */
	int (*on_update)(fq_logger_t *, mmap_eventlog_obj_t *, char *);
};

int eventlog(fq_logger_t *l, fq_event_t event_id, ...);
void en_eventlog(fq_logger_t *l, fq_event_t event_id, ...);
int open_mmap_eventlog(fq_logger_t *l, char *path, mmap_eventlog_obj_t **obj);
int open_eventlog_obj( fq_logger_t *l, char *path, eventlog_obj_t **obj);
int close_evenlog_obj(fq_logger_t *l, eventlog_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif

