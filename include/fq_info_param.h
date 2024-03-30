/* vi: set sw=4 ts=4: */
/***************************************************************
** fq_info_param.h
**/

#ifndef _FQ_INFO_PARAM_H
#define _FQ_INFO_PARAM_H

#include "fq_info.h"
#include "fq_logger.h"

#define FQ_INFO_PARAM_H_VERSION "1.0.0"

/*
** Global variables
*/

#ifdef __cplusplus
extern "C" {
#endif

/*
** function prototype
*/
int load_info_param(fq_logger_t *l, fq_info_t *_info, const char* fq_info_filename);

#ifdef __cplusplus
}
#endif

#endif
