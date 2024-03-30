#ifndef _FQ_PROTOCOL_H
#define _FQ_PROTOCOL_H

#define FQ_PROTOCOL_H_VERSION "1.0.0" /* if this source is released, version starts from 1.0.0 */

#include "fq_logger.h"
#include "fq_tcp_client_obj.h"

#ifdef __cplusplus
extern "C" {
#endif

#define EQ_FULL_RETURN_VALUE    0
#define EQ_EMPTY_RETURN_VALUE    0

/*
** Fixed length
** FQP : File Queue Protocols for networking between Server and clients.
*/
#define FQP_VERSION_LEN 2
#define FQP_SESSION_ID_LEN 32 /* skey */
#define FQP_PATH_LEN	128
#define FQP_QNAME_LEN	128
#define FQP_ACTION_CODE_LEN 4
#define FQP_HEADER_SIZE	33

#define FQ_PROTOCOL_LEN 512

#define FQP_LINK_MSG 	"LINK" 	/* connect and open queue */
#define FQP_ENQU_MSG	"ENQU"	/* requeust enFQ */
#define FQP_DEQU_MSG	"DEQU"  /* requeust deFQ */
#define FQP_EXIT_MSG	"EXIT"	/* close, disconnect and exit */
#define FQP_HCHK_MSG 	"HCHK"  /* health check */

#define FQP_LINK_REQ 0
#define FQP_ENQU_REQ 1
#define FQP_DEQU_REQ 2
#define FQP_EXIT_REQ 3
#define FQP_HCHK_REQ 4

typedef struct _FQ_protocol {
	char version[FQP_VERSION_LEN+1]; /* 10, 11, 12, ... */
	char skey[FQP_SESSION_ID_LEN+1];
	char path[FQP_PATH_LEN+1];
	char qname[FQP_QNAME_LEN+1];
	char	ack_mode; /* Y: client waits ack, N: client doesn't wait ack */
	char	action_code[FQP_ACTION_CODE_LEN+1]; 
} FQ_protocol_t;

int FQ_link( fq_logger_t *l, 
				int	 max_try_count,
				char *primary_server_ip, 
				char *secondary_server_ip,
				char *port,
				tcp_client_obj_t **ptr_tcpobj,
				char	*path,
				char	*qname,
				char *result_code,
				char **session_key);

int FQ_put( fq_logger_t *l, 
				tcp_client_obj_t *tcpobj, 
				unsigned char *putdata, 
				int putdata_len, 
				char *skey, 
				char *path,
				char *qname,
				char *result_code);

int FQ_get( fq_logger_t *l, 
				tcp_client_obj_t *tcpobj, 
				char *skey, 
				char *path,
				char *qname,
				unsigned char **qdata,
				int	*qdata_len,
				char *result_code);
int FQ_link_json( fq_logger_t *l, 
				int	 max_try_count,
				char *primary_server_ip, 
				char *secondary_server_ip,
				char *port,
				tcp_client_obj_t **ptr_tcpobj,
				char	*path,
				char	*qname,
				char *result_code,
				char **session_key);

int FQ_put_json( fq_logger_t *l, 
				tcp_client_obj_t *tcpobj, 
				unsigned char *putdata, 
				int putdata_len, 
				char *skey, 
				char *path,
				char *qname,
				char *result_code);

int FQ_get_json( fq_logger_t *l, 
				tcp_client_obj_t *tcpobj, 
				char *skey, 
				char *path,
				char *qname,
				unsigned char **qdata,
				int	*qdata_len,
				char *result_code);
int FQ_health_check_json( fq_logger_t *l, 
				tcp_client_obj_t *tcpobj, 
				char *skey, 
				char *path,
				char *qname,
				char *result_code);
#ifdef __cplusplus
}
#endif

#endif
