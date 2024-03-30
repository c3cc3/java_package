/*
** fq_flock.h
*/
#define FQ_FLOCK_H_VERSION "1.0.0"

#ifndef _FQ_FLOCK_H
#define _FQ_FLOCK_H

#include "fq_logger.h"

#define FLOCK_FILENAME	".FQ_singleton.flock"

#define EN_FLOCK 1
#define DE_FLOCK 2
#define ETC_FLOCK 3

#ifdef __cplusplus
extern "C"
{
#endif

typedef struct _flock_obj_t flock_obj_t;
struct _flock_obj_t {
	char *path;
	char *qname;
	char pathfile[128];
	int	 fd;
	fq_logger_t *l;
	struct flock fl;

	pthread_mutexattr_t	mtx_attr;
	pthread_mutex_t mtx;

	int (*on_flock)(flock_obj_t *);
	int (*on_funlock)(flock_obj_t *);
	int (*on_flock2)(flock_obj_t *);
	int (*on_funlock2)(flock_obj_t *);
};

int open_flock_obj( fq_logger_t *l, char *path, char *qname, int en_de_flag, flock_obj_t **obj);
int close_flock_obj( fq_logger_t *l, flock_obj_t **obj);
int unlink_flock( fq_logger_t *l, char *pathfile );
#ifdef __cplusplus
}
#endif

#endif
