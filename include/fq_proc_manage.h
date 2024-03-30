/* vi: set sw=4 ts=4: */
/*
 * fq_proc_manage.h
 */
#ifndef _FQ_PROC_MANAGE_H
#define _FQ_PROC_MANAGE_H

#define FQ_PROC_MANAGE_H_VERSION "1.0.3"

#include "fq_logger.h"
#include "fq_common.h"
#include "fq_typedef.h"

#ifdef __cplusplus
extern "C"
{
#endif

int regist_pid(fq_logger_t *l, int pid, char *my_process_name,  char *filename);
int pid_file_process_stop ( fq_logger_t *l, char *my_process_name,  char *pid_file );
bool_t is_alive_process(fq_logger_t *l, char *my_process_name,  char *filename, pid_t *pid);

#ifdef __cplusplus
}
#endif

#endif
