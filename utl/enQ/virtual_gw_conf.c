/* 
** virtual_gw_conf.c
*/

#include "fq_config.h"
#include "fq_param.h"
#include "fq_logger.h"
#include "fq_common.h"
#include <stdio.h>

#include "virtual_gw_conf.h"

bool LoadConf(char *filename, virtual_gw_conf_t **my_conf, char **ptr_errmsg)
{
	virtual_gw_conf_t *c = NULL;
	config_t *_config=NULL;
	char ErrMsg[512];
	char buffer[256];

	_config = new_config(filename);
	c = (virtual_gw_conf_t *)calloc(1, sizeof(virtual_gw_conf_t));

	if(load_param(_config, filename) < 0 ) {
		sprintf(ErrMsg, "Can't load '%s'.", filename);
		*ptr_errmsg = strdup(ErrMsg);
		return false;
	}

	/* Check whether essential items(mandatory) are omitted. */

	if(get_config(_config, "LOG_FILENAME", buffer) == NULL) {
		sprintf(ErrMsg, "LOG_FILENAME is undefined in your config file.");
		*ptr_errmsg = strdup(ErrMsg);
		return false;
	}
	c->log_filename = strdup(buffer);

	if(get_config(_config, "LOG_LEVEL", buffer) == NULL) {
		sprintf(ErrMsg, "LOG_LEVEL is undefined in your config file.");
		*ptr_errmsg = strdup(ErrMsg);
		return false;
	}
	c->log_level = strdup(buffer);
	c->i_log_level = get_log_level(c->log_level);

	if(get_config(_config, "ENQ_PATH", buffer) == NULL) {
		sprintf(ErrMsg, "ENQ_PATH is undefined in your config file.");
		*ptr_errmsg = strdup(ErrMsg);
		return false;
	}
	c->qpath = strdup(buffer);

	if(get_config(_config, "ENQ_NAME", buffer) == NULL) {
		sprintf(ErrMsg, "ENQ_NAME is undefined in your config file.");
		*ptr_errmsg = strdup(ErrMsg);
		return false;
	}
	c->qname = strdup(buffer);

	if(get_config(_config, "TEST_COUNT", buffer) == NULL) {
		sprintf(ErrMsg, "TEST_COUNT is undefined in your config file.");
		*ptr_errmsg = strdup(ErrMsg);
		return false;
	}
	c->test_count = atoi(buffer);

	if(get_config(_config, "CHANNEL", buffer) == NULL) {
		sprintf(ErrMsg, "CHANNEL is undefined in your config file.");
		*ptr_errmsg = strdup(ErrMsg);
		return false;
	}
	c->channel = strdup(buffer);

	if(get_config(_config, "JSON_TEMPLATE_PATHFILE", buffer) == NULL) {
		sprintf(ErrMsg, "JSON_TEMPLATE_PATHFILE is undefined in your config file.");
		*ptr_errmsg = strdup(ErrMsg);
		return false;
	}
	c->json_template_pathfile = strdup(buffer);

	if(get_config(_config, "USLEEP", buffer) == NULL) {
		sprintf(ErrMsg, "USLEEP is undefined in your config file.");
		*ptr_errmsg = strdup(ErrMsg);
		return false;
	}
	c->usleep = atoi(buffer);

	*my_conf = c;

	if(_config) free_config(&_config);

	return true;
}

void FreeConf( virtual_gw_conf_t *t)
{
	SAFE_FREE( t->log_filename);
	SAFE_FREE( t->log_level);
	SAFE_FREE( t->qpath);
	SAFE_FREE( t->qname);
	SAFE_FREE( t->channel);
	SAFE_FREE( t->json_template_pathfile);

	SAFE_FREE( t );
}
