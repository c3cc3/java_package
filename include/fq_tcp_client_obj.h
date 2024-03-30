/*
 * fq_tcp_client_obj.h
 */
#define FQ_TCP_CLIENT_OBJ_H_VERSION "1.0.1"

#ifndef _FQ_TCP_CLIENT_OBJ_H
#define _FQ_TCP_CLIENT_OBJ_H

#include <pthread.h>
#include <stdbool.h>

#include "fq_logger.h"

#ifdef __cplusplus
extern "C"
{
#endif

#define SOCKET_HEADER_LEN      	4

typedef enum { BINARY_HEADER=0, ASCII_HEADER, NO_HEADER } socket_header_type_t;

typedef struct _tcp_client_obj_t	tcp_client_obj_t;
struct _tcp_client_obj_t {
	fq_logger_t	*l;
	char	*ip;
	char	*port;
	int		sockfd;
	socket_header_type_t header_type;
	int		header_len;

	ssize_t (*on_writen)(fq_logger_t *, tcp_client_obj_t *, const void *, size_t);
	ssize_t (*on_readn)(fq_logger_t *, tcp_client_obj_t *, void *, size_t);
	int	 	(*on_get_bodysize)(unsigned char*, int, int);
	int	    (*on_set_bodysize)(unsigned char*, int, int, int);
};

int open_tcp_client_obj( fq_logger_t *l, char *ip, char *port, socket_header_type_t type, int header_len, tcp_client_obj_t **obj);
int close_tcp_client_obj(fq_logger_t *l, tcp_client_obj_t **obj);

int daemon_init(void);
int send_header_and_body_socket(fq_logger_t *l, tcp_client_obj_t *tcpobj, unsigned char *buf, int datalen);
int recv_header_and_body_socket(fq_logger_t *l, tcp_client_obj_t *tcpobj, unsigned char **databuf );
int recv_ACK( fq_logger_t *l, tcp_client_obj_t *tcpobj );
int request_restart_me( fq_logger_t *l, char *auto_fork_svr_listen_port, unsigned char *restart_cmd);
bool checkServerStatus(const char *server_ip, int server_port);


#ifdef __cplusplus
}
#endif

#endif
