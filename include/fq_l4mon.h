/*
 * monlib.h
 * 
 */
#ifndef MONLIB_H_
#define MONLIB_H_

#ifdef __cplusplus
extern "C"
{
#endif
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/msg.h>
#include <sys/ipc.h>
#include <sys/msg.h>
#include <sys/mman.h>

#define VAS_IP "165.141.120.1"
#define VAS_PORT	5555

void *open_db( char *path, char *fname, int *fd, int *lockfd );
void close_db(void *p, off_t mmap_len, int fd, int lockfd );
int get_node_no();
int get_domain_no(int node_no);
int SendToVAS(char *svc_kind, char *error_msg);
void mon_eventlog(char *fmt, ...);

#ifdef __cplusplus
}
#endif

#endif /* #ifndef MONLIB_H_ */
