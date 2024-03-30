/*
** fq_replace.h
*/
#ifndef _FQ_REPLACE_H
#define _FQ_REPLACE_H

#include "fq_logger.h"

#ifdef _cplusplus
extern "C" {
#endif

int load_a_file_2_buf(fq_logger_t *l, char *path_file, char**ptr_dst, int *flen, char** ptr_errmsg);
int fq_dir_replace( fq_logger_t *l, char *dirname, char *find_filename_sub_string, char *find_str, char *replace_str);
int fq_file_replace( fq_logger_t *l, char *pathfile, char *find_str, char *replace_str);
int fq_replace( fq_logger_t *l, char *src, char *find_str,  char *replace_str,  char *dst, int dst_buf_len);

#ifdef _cplusplus
}
#endif
#endif
