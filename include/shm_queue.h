/*
 * shm_queue.h
 */
#ifndef _SHM_QUEUE_H
#define _SHM_QUEUE_H

#include "fqueue.h"
#include "fq_logger.h"
#define SHM_QUEUE_H_VERSION "1.0.0"

#ifdef __cplusplus
extern "C"
{
#endif

#ifndef NAME_MAX
#define NAME_MAX 255
#endif

int CreateShmQueue(fq_logger_t *l, char *path, char *qname);
int UnlinkShmQueue(fq_logger_t *l, char *path, char *qname);

int OpenShmQueue(fq_logger_t *l, char *path, char *qname, fqueue_obj_t **obj);
int CloseShmQueue(fq_logger_t *l, fqueue_obj_t **obj);
int ResetShmQueue(fq_logger_t *l, char *path, char *qname);
int FlushShmQueue(fq_logger_t *l, char *path, char *qname);

#ifdef __cplusplus
}
#endif

#endif
