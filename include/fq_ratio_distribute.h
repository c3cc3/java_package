/* vi: set sw=4 ts=4: */
/*
 * fq_ratio_distribute.h
 */

#ifndef _FQ_RATIO_DISTRIBUTE_H
#define _FQ_RATIO_DISTRIBUTE_H
#include <pthread.h>

#include "fq_logger.h"
#include "fq_common.h"
#include "fq_linkedlist.h"
#include "fq_delimiter_list.h"

#ifdef __cplusplus
extern "C"
{
#endif


typedef struct _ratio_obj_t ratio_obj_t;
struct _ratio_obj_t {
	fq_logger_t	*l;
	linkedlist_t	*ll;
	char *ratio_string; /* K=5,L=10,S=20,N=65 */
	char *rand_seed_string; /* KLLSSSSNNNNNNNNNNNNN */
	char (*on_select)(fq_logger_t *,  ratio_obj_t *);
	bool (*on_change_ratio_seed)(fq_logger_t *,  ratio_obj_t *, char *new_ratio_str);
};

typedef struct _ratio_node_t ratio_node_t;
struct _ratio_node_t {
	char initial;
	int	 ratio;
};

/* function prototypes */
bool open_ratio_distributor( fq_logger_t *l, char *ratio_string, char delimiter, char sub_delimiter, ratio_obj_t **obj);

bool close_ratio_distributor( fq_logger_t *l, ratio_obj_t **obj);
linkedlist_t *make_a_ratio_linkedlist(fq_logger_t *l, char *ratio_string, char delimiter, char sub_delimiter);
char *make_seed_string( fq_logger_t *l, ratio_obj_t *obj, char *ratio_src, char delimiter, char sub_delimiter );
bool co_down_new_ratio(  fq_logger_t *l,ratio_obj_t *obj, char donw_co_initial);
float get_co_ratio_by_co_initial( fq_logger_t *l, ratio_obj_t *obj, char co_initial);
void redistributeValues(int *A, int *B, int *C, int redistribute_value); 



#ifdef __cplusplus
}
#endif

#endif
