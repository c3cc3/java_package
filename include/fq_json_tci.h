
#ifndef FQ_JSON_TCI_H
#define FQ_JSON_TCI_H

#include "fq_logger.h"
#include "fq_linkedlist.h"
#include "fq_cache.h"
#include "parson.h"

#ifdef __cplusplus
extern "C"
{
#endif

typedef struct _json_tci_t json_tci_t;
struct _json_tci_t {
	char find_key[128];
	char target_key[128];
	char source[16]; 
	int	length;
	char datatype[16];
	char fixed[64]; // compulsary  Y/N
	bool mandatory;
};

bool Load_json_make_rule( fq_logger_t *l, char *mapfile, char delimiter, linkedlist_t   *ll);
bool make_json_input_sample_data( JSON_Value **json, JSON_Object **root);
bool make_serialized_json_by_rule( fq_logger_t *l, linkedlist_t *json_tci_ll, cache_t *cache, JSON_Object *in_json, char **dst, bool pretty_flag );


#ifdef __cpluscplus
}
#endif
#endif
