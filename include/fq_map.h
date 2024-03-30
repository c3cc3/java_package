/***************************************************************
 ** fq_map.h
 **/
#define FQ_MAP_H_VERSION "1,0,0"

#ifndef _FQ_MAP_H
#define _FQ_MAP_H
#include <pthread.h>

#define MAX_MAP_LINE 200

#ifdef __cplusplus
extern "C"
{
#endif

typedef struct {
	char	*key;
	char	*group_id;
    char    *grep_string;  
	char	*run_cmd;
    char    *option_string;
	char	*description;
    int		pid_index;
	int		pid;
	char	*bin_dir;
} map_record_t;

typedef struct {
        char    *name;
        int     count;
        map_record_t *s[MAX_MAP_LINE];
} map_format_t;

map_format_t *new_map_format(const char *name);
void 	free_map_format(map_format_t **q);
int 	read_map_format(map_format_t *w, const char* filename);
void 	print_map_format(map_format_t* w);

#ifdef __cplusplus
}
#endif

#endif
