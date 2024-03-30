/***************************************************************
** fq_param.h
**/
#ifndef _FQ_PARAM_H
#define _FQ_PARAM_H

#define FQ_PARAM_H_VERSION "1.0.0"

#include "fq_config.h"

/*
** Global variables
*/

#ifdef __cplusplus
extern "C" {
#endif
/*
** function prototype
*/
int load_param(config_t *_config, const char* config_filename);
int load_multi_param(config_t *_config, const char* config_filename, const char *svrname);
#ifdef __cplusplus
}
#endif

#endif
