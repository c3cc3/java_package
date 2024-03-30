/*
** fq_codemap.h
*/
#ifndef _FQ_CODEMAP_H
#define _FQ_CODEMAP_H

#include <stdio.h>
#include <pthread.h>
#include <sys/types.h>
#include <sys/stat.h>

#include "fq_cache.h"
#include "fq_logger.h"

#ifdef __cplusplus
extern "C"
{
#endif

typedef struct _codemap_t {
        char*           name;
        fqlist_t*        map;
        time_t          mtime;
        pthread_mutex_t lock;
} codemap_t;

codemap_t *new_codemap(fq_logger_t *l, char* name);
void free_codemap(fq_logger_t *l, codemap_t **ptrm);
void clear_codemap(fq_logger_t *l, codemap_t *m);
int read_codemap(fq_logger_t *l, codemap_t *code_map, char* filename);
int get_codeval(fq_logger_t *l, codemap_t *m, char* code, char* dst);
int get_codekey(fq_logger_t *l, codemap_t *m, char* value, char* dst);
int get_codekeyflag(fq_logger_t *l, codemap_t *m, char* value, char* dst, int* flag);
void print_codemap(fq_logger_t *l, codemap_t* m, FILE* fp);
int get_num_items( fq_logger_t *l, codemap_t* m );
int get_value_codemap_file(fq_logger_t *l, char *path, char* filename, char* src, char* dst);

#ifdef __cplusplus
}
#endif


#endif
