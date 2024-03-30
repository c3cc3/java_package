#/*
** extract_files_from_dir.h
*/
#ifndef _EXTRACT_FILES_FROM_DIR_H
#define _EXTRACT_FILES_FROM_DIR_H

#include <stdio.h>
#include <stdbool.h>
#include <pthread.h>
#include "fq_logger.h"
#include "fq_linkedlist.h"

#ifdef __cplusplus
extern "C" {
#endif

int fq_scandir_and_make_linked_list_with_sub_string(const char *s_path, int sub_dir_name_include_flag, char *find_sub_string,  linkedlist_t *ll);
bool exist_queue_in_filelist( fq_logger_t *l, char *qname, linkedlist_t *files_ll );

#ifdef __cplusplus
}
#endif

#endif
