/* vi: set sw=4 ts=4: */
/*
 * fq_sec_counter.h
 */
#ifndef _FQ_SEC_COUNTER_H
#define _FQ_SEC_COUNTER_H

#include <time.h>
#include "fq_logger.h"

#define FQ_SEC_COUNTER_H_VERSION "1.0.0"

#define TEST_STOP 1
#define TEST_CONTINUE 0

#ifdef __cplusplus
extern "C"
{
#endif

typedef struct _second_counter_obj second_counter_obj_t;
struct _second_counter_obj {
	time_t tval;
	struct tm t;
	
	int min_th;
	int sec_th;

	time_t	start;
	time_t	end;
	int test_cnt;
	int tot_cnt;

	int cur_min_th;
	char *path;
	char *file;
	char	path_file[128];
	FILE *fp;

	int sec_cnt[60];
	int (*on_second_count) (second_counter_obj_t *);
};

int open_second_counter_obj(char *path, char *file, int test_count, second_counter_obj_t **obj);
void close_second_counter_obj( second_counter_obj_t **obj);

#ifdef __cplusplus
}
#endif

#endif
