#ifndef _FQ_SOCKET_H
#define _FQ_SOCKET_H
// test2
// test3
// test4
#define FQ_SOCKET_H_VERSION "1.0.0"

#include <stdbool.h>
#include "fq_logger.h"

#define NET_TIMEOUT 5

#define HASVALUE(ptr)   ((ptr) && (strlen(ptr) > 0))

#define SOCK_MAXDATALEN 65532
#define BINARY_HEADER_TYPE 	0
#define ASCII_HEADER_TYPE	1
#define SOCK_CLIENT_HEADER_SIZE 4
#define SOCK_MAX_USER_DATA_SIZE  (SOCK_MAXDATALEN-SOCK_CLIENT_HEADER_SIZE)
#define ACK_LEN 1

typedef int (*CB1)(unsigned char *, int);
typedef int (*CB2)(unsigned char *, int, unsigned char *, int *); /* sync type : uss ACK */

typedef enum { TCP=0, UDP, MULTICAST } protocol_t;
typedef enum { BINARY=0, ASCII } header_type_t;
typedef enum { FLAG_FALSE=0, FLAG_TRUE } flag_t;

typedef struct _TCP_server {
	fq_logger_t *l;
    char    *ip;
    int     port;
	int		ack_mode; /* 1: use ACK,  0: no ACK */
	CB1		fp;
    CB2     sync_fp;
	header_type_t   header_type;
    int     header_size;
}TCP_server_t;

typedef struct _manager_server {
	fq_logger_t *l;
	int		new_sock;
	int		ack_mode; /* 1: use ACK,  0: no ACK */
    CB2     sync_fp;
	char	peer_ip[16];
	header_type_t	header_type;
	int		header_size;
} manager_server_t;

#define MAX_HEADER_SIZE 16	

typedef struct _fq_socket fq_socket_t;
struct _fq_socket {
	/* common part */
	int				sockfd;
	flag_t			header_flag;
	header_type_t	header_type;
	int				header_length;
	int				ack_mode; /* 1: use ACK,  0: no ACK */
	char			*error_msg;

	/* send request part */
	unsigned char 	snd_header[MAX_HEADER_SIZE];
	int				snd_rtn;

	/* recv response part */
	unsigned char 	rcv_header[MAX_HEADER_SIZE];
	unsigned char 	*rcv_data;
	ssize_t        	rcv_datalen;
	int				rcv_rtn;

	int (* on_send)(fq_logger_t *, fq_socket_t *, unsigned char *, int);
	int (* on_recv)(fq_logger_t *, fq_socket_t *);
	int (* on_release)(fq_logger_t *, fq_socket_t * );
};

#ifdef __cplusplus
extern "C" {
#endif


void *TCP_callback_server( void *argument);
void *TCP_callback_sync_server( void *argument);


int create_tcp_socket(fq_logger_t *l, int port, const char *ip);
ssize_t  writen(int fd, const void *vptr, size_t n);
ssize_t readn(int fd, void *vptr, size_t n);
ssize_t  debug_writen(fq_logger_t *l, int fd, const void *vptr, size_t n);
ssize_t  debug_writen_timeout(fq_logger_t *l, int fd, const void *vptr, size_t n, struct timeval *timeout);
ssize_t debug_readn(fq_logger_t *l, int fd, void *vptr, size_t n);
ssize_t debug_readn_timeout(fq_logger_t *l, int fd, void *vptr, size_t n, struct timeval *timeout);
void *get_in_addr(struct sockaddr *sa);
int new_connector(fq_logger_t *l, const char *ip, const char *port, int *sockfd);
int new_connector2(fq_logger_t *l, const char *ip, const char *str_port, int timeout, int *sockfd);
int timed_connect(fq_logger_t *l, int sock, struct sockaddr *sin, int nsin, int timeout);

int make_thread_server(fq_logger_t *l, TCP_server_t *x, char *ip, int port, CB1);
int make_thread_sync_CB_server( fq_logger_t *l, TCP_server_t *x, char *ip, int port, header_type_t header_type, int header_size, int ack_mode, CB2);
int make_thread_sync_CB_server_no_header( fq_logger_t *l, TCP_server_t *x, char *ip, int port, CB2);

char *get_peer_ip(int fd, char buff[]);

char *jk_dump_hinfo(struct sockaddr_in *saddr, char *buf); /* host info */
char *jk_dump_sinfo(int sd, char *buf); /* session info */
int is_socket_connected(int sd);
int is_valid_socket(fq_logger_t *l, int sd);
char *get_socket_fd_info(int sd, char *buf, char *src_ip, char *src_port, char *dst_ip, char *dst_port);
int looping_read(int sd, char *dst, int until_len);
int check_socket(int socket);
int check_udp_socket(int socket);
int can_read(int socket, int timeout);
int can_write(int socket, int timeout);
int sock_write(int socket, const void *buffer, int size);
int sock_read(int socket, void *buffer, int size, int timeout);
int check_host(const char *hostname);
int set_bodysize(unsigned char* header, int size, int value, int header_type);
int get_bodysize(unsigned char* header, int size, int header_type);
int send_header( int HeaderSize, int nSend, int HeaderType, int sock);
unsigned char *recv_header( int HeaderSize, int HeaderType, int sockfd);

/* for using socket object */
int prepare_socket_client(fq_logger_t *l, char *hostname, char *port);
int init_socket_obj( fq_socket_t *obj, int sockfd, header_type_t header_type, int header_length, fq_logger_t *l);
void socket_buffers_control(int sockfd, int buffer_size_bytes);
bool get_ip_from_hostname(fq_logger_t *l, char *hostname, char *dst);
bool get_ip_from_ethernet_name( fq_logger_t *l, char *eth_name);
char* get_ip_address(const char* interface_name);

#ifdef __cplusplus
}
#endif

#endif
