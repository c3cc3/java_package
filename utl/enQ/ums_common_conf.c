/* 
** ums_common_conf.c
*/

#include "fq_config.h"
#include "fq_param.h"
#include "ums_common_conf.h"
#include "fq_logger.h"
#include "fq_common.h"
#include <stdio.h>

bool Load_ums_common_conf(ums_common_conf_t **common_conf, char **ptr_errmsg)
{
	ums_common_conf_t *c = NULL;
	config_t *_config=NULL;
	char ErrMsg[512];
	char buffer[256];

	char *ums_common_conf_file = getenv("UMS_COMMON_CONF_FILE");
	if( !ums_common_conf_file ) {
		sprintf(ErrMsg, "UMS_COMMON_CONF_FILE is undefined in your env.");
		*ptr_errmsg = strdup(ErrMsg);
        return false;
    }

	_config = new_config(ums_common_conf_file);

	c = (ums_common_conf_t *)calloc(1, sizeof(ums_common_conf_t));

	if(load_param(_config, ums_common_conf_file) < 0 ) {
		sprintf(ErrMsg, "Can't load '%s'.", ums_common_conf_file);
		*ptr_errmsg = strdup(ErrMsg);
		return false;
	}

	/* Check whether essential items(mandatory) are omitted. */
	if(get_config(_config, "UMS_COMMON_LOG_PATH", buffer) == NULL) {
		sprintf(ErrMsg, "UMS_COMMMON_LOG_PATH is undefined in your config file.");
		*ptr_errmsg = strdup(ErrMsg);
		return false;
	}
	c->ums_common_log_path = strdup(buffer);

	*common_conf = c;
		
	if(_config) free_config(&_config);

	return true;
}
void Free_ums_common_conf( ums_common_conf_t *t)
{
	SAFE_FREE( t->ums_common_log_path);
	SAFE_FREE( t );
}

