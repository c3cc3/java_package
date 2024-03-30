#ifndef json_config_h
#define json_config_h

#ifdef __cplusplus
extern "C"
{
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "parson.h"
#include "fq_logger.h"
#include "fq_common.h"
#include <stdbool.h>


#if 0
#define TEST(A) printf("%d %-72s-", __LINE__, #A);\
                if(A){puts(" OK");}\
                else{puts(" FAIL");}
#endif

typedef struct _json_config json_config_t;
struct _json_config {
	fq_logger_t *l;
	char *filename;
	char *log_filename;
	int	 log_level;
	JSON_Value *JValue;
	JSON_Object *JObject;
	int (*on_print)(json_config_t *jc);
};
bool open_json_config( json_config_t **json_config, char *config_filename);
int close_json_config( json_config_t *jc );

#ifdef __cpluscplus
}
#endif
#endif
