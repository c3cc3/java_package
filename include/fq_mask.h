/*
** fq_mask.h
*/
#ifndef _FQ_MASK_H
#define _FQ_MASK_H

#include <stdio.h>

#define FQ_MASK_H_VERSION "1.0,0"

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

typedef struct _mask_obj_t mask_obj_t;
struct _mask_obj_t {
	char	*name;
	int		bit; /* operating bit */
	int		mask;
	char	*yesno;

	int  (*on_set_mask)( mask_obj_t *);
	int  (*on_get_mask)( mask_obj_t *);
	void (*on_print_mask)( mask_obj_t *);
};

int open_mask_obj( char *name, mask_obj_t **obj);
int close_mask_obj(mask_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif


