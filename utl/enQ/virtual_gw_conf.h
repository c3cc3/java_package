/*
** ratio_dist_conf.h
*/
#ifndef _RATIO_DIST_CONF_H
#define _RATIO_DIST_CONF_H

#include <stdbool.h>
#include "fq_config.h"

#ifdef __cpluscplus
extern "C"
{
#endif 

typedef struct _virtual_gw_conf virtual_gw_conf_t;
struct _virtual_gw_conf {
	char	*log_filename;
	char	*log_level;
	int		i_log_level;
	char	*qpath; /* file queue for reading */
	char	*qname;
	int		test_count;
	char	*channel;
	int		usleep;
	char	*json_template_pathfile;
};

bool LoadConf(char *filename, virtual_gw_conf_t **c, char **ptr_errmsg);
void FreeConf(virtual_gw_conf_t *c);

#ifdef __cpluscplus
}
#endif
#endif
