/* fq_unix_sockets.h
 *
 *   Header file for fq_unix_sockets.c.
 *
 */
#ifndef FQ_UNIX_SOCKETS_H
#define FQ_UNIX_SOCKETS_H      /* Prevent accidental double inclusion */

#include <sys/socket.h>
#include <sys/un.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C"
{
#endif

int unixBuildAddress(const char *path, struct sockaddr_un *addr);
int unixConnect(const char *path, int type);
int unixListen(const char *path, int backlog);
int unixBind(const char *path, int type);
int sendfd(int sock_fd, int send_me_fd);
int recvfd(int sock_fd);

#ifdef __cplusplus
}
#endif

#endif
