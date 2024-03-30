/*
** fq_mem.h
*/
#ifndef _FQ_MEM_H
#define _FQ_MEM_H

#include "fq_logger.h"

#ifdef __cplusplus
extern "C"
{
#endif

typedef struct _mem_obj_t mem_obj_t;
struct _mem_obj_t {
	char *p;
	int size;
	int c;

	int (*mputc)(mem_obj_t *, char );
	char (*mgetc)(mem_obj_t *);
	int (*meof)(mem_obj_t *);
	int (*mcopy)(mem_obj_t *, char *, int);
	int (*mseek)(mem_obj_t *, int);
	void (*mprint)(mem_obj_t *);
	int (*mback)(mem_obj_t *, int);
	void (*mgets)(mem_obj_t *, char *);
};

int open_mem_obj( fq_logger_t *l, mem_obj_t **obj, int);
int close_mem_obj( fq_logger_t *l, mem_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif
