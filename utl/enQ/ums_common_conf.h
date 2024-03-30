/*
** ums_common_conf.h
*/
#ifndef _UMS_COMMON_CONF_H
#define _UMS_COMMON_CONF_H

#include <stdbool.h>
#include "fq_config.h"

#ifdef __cpluscplus
extern "C"
{
#endif

typedef struct _ums_common_conf {
	char	*ums_common_log_path;
}ums_common_conf_t;

bool Load_ums_common_conf(ums_common_conf_t **c, char **ptr_errmsg);
void Free_ums_common_conf(ums_common_conf_t *c);

#ifdef __cpluscplus
}
#endif
#endif

