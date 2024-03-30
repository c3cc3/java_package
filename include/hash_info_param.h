/* vi: set sw=4 ts=4: */
/***************************************************************
** hash_info_param.h
**/

#ifndef _HASH_INFO_PARAM_H
#define _HASH_INFO_PARAM_H

#include "hash_info.h"

#define HASH_INFO_PARAM_H_VERSION "1.0.0"

/*
** Global variables
*/

#ifdef __cplusplus
extern "C" {
#endif

/*
** function prototype
*/
int load_hash_info_param(hash_info_t *_info, const char* hash_info_filename);

#ifdef __cplusplus
}
#endif

#endif
