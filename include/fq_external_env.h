/* vi: set sw=4 ts=4: */
/*
 * fq_external_env.h
 */
#ifndef _FQ_EXTERNAL_ENV_H
#define _FQ_EXTERNAL_ENV_H

#include <pthread.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/msg.h>
#include <sys/sem.h>

#include "fq_logger.h"

#if 0
#define _USE_EXTERNAL_ENV
#else
#undef _USE_EXTERNAL_ENV
#endif

#define FQ_EXTERNAL_ENV_H_VERSION "1.0.0"

#ifdef __cplusplus
extern "C"
{
#endif

#define MAX_RETRIES 10
#define FQ_SEMAPHORE_KEY_CHAR	's'

typedef struct _external_env_obj_t external_env_obj_t;
struct _external_env_obj_t {
	char	*path;
	char	*env_fname;
	char	*contents;
	int		contents_length;
	fq_logger_t	*l;

	int (*on_get_extenv)(fq_logger_t *, external_env_obj_t *, char *, char *);
};

int open_external_env_obj( fq_logger_t *l, char *path, external_env_obj_t **obj);
int close_external_env_obj( fq_logger_t *l, external_env_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif
