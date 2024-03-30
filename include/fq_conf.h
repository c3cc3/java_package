/*
** fq_conf.h
*/
#ifndef _FQ_CONF_H
#define _FQ_CONF_H

#include <stdio.h>

#include "fq_cache.h"

#define FQ_CONF_H_VERSION "1.0,0"

#ifndef TRUE
#define TRUE 1
#endif
#ifndef FALSE
#define FALSE 0
#endif

#ifdef __cplusplus
extern "C" 
{
#endif

typedef struct _conf_obj_t conf_obj_t;

struct _conf_obj_t {
	char	*filename; /* config file name with full-path */
	int		item_num;
	fqlist_t *items;

	int  (*on_get_str)( conf_obj_t *, char *keyword, char *value);
	char  (*on_get_char)( conf_obj_t *, char *keyword);
	int  (*on_get_int)( conf_obj_t *, char *keyword);
	float  (*on_get_float)( conf_obj_t *, char *keyword);
	long  (*on_get_long)( conf_obj_t *, char *keyword);
	void (*on_print_conf)( conf_obj_t *);
};

int open_conf_obj( char *filename, conf_obj_t **obj);
int close_conf_obj(conf_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif
