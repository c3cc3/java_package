#ifndef _SVR_LIST_H
#define _SVR_LIST_H


#include <stdbool.h>
#include "fq_logger.h"

#ifdef __cpluscplus
extern "C"
{
#endif

typedef struct _svr_t {
	char *ip;
	char *port;
	struct _svr_t *next;
} svr_t;

typedef struct _svrlist_t {
	int length;
	svr_t	*head;
	svr_t	*tail;
	struct	_svrlist_t *next;
}svrlist_t;

svrlist_t *svrlist_new();
void svrlist_free(svrlist_t **t);

svr_t *svrlist_put(svrlist_t *t, char *ip, char *port);
svr_t *svrlist_pop(svrlist_t *t);
void svrlist_print(svrlist_t *t, FILE *fp);
bool make_svr_list(fq_logger_t *l, char *src, svrlist_t *t);

#ifdef __cpluscplus
#endif
#endif
