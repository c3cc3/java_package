/* vi: set sw=4 ts=4: */
/*
 * fq_unixQ.h
 */
#ifndef _FQ_UNIXQ_H
#define _FQ_UNIXQ_H

#include <stdio.h>
#include <string.h>

#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/msg.h>
#include <errno.h>

#include "fq_logger.h"

#define FQ_UNIXQ_H_VERSION "1.0.0"

#ifdef __cplusplus
extern "C"
{
#endif

#define FQ_UNIXQ_KEY_PATH 	"/tmp"
#define FQ_UNIXQ_KEY_CHAR   'q' /* 0x71010002 */

#define UQ_FULL_RETURN_VALUE 0
#define UQ_USER_MAX_MSGLEN   65000

/* UnixQueue(UQ) msgbuf */
struct UQ_msgbuf {
    long pid; /* send 할때 , 받을 쪽의 ID 지정한다. */
    long tid; /* 돌려받기 위한 turn ID 를 지정한다. */
    char mtext[UQ_USER_MAX_MSGLEN+1];
};

typedef struct _unixQ_obj_t unixQ_obj_t;
struct _unixQ_obj_t {
	fq_logger_t *l;

	char *path;
	char key_char;
	key_t	key;
	int 	msq_id;

	int(*on_send_unixQ)(unixQ_obj_t *, const void *, size_t, int);
	int(*on_recv_unixQ)(unixQ_obj_t *, void *, size_t, long);
	int(*on_getstat_unixQ)(unixQ_obj_t *, struct msqid_ds *);
	int(*on_en)(unixQ_obj_t *, unsigned char *, size_t, long, long, int);
	int(*on_de)(unixQ_obj_t *, unsigned char *, size_t, long, long *, long *);
};

int open_unixQ_obj(  fq_logger_t *l, char *path, char key_char, unixQ_obj_t **obj);
int close_unixQ_obj(fq_logger_t *l, unixQ_obj_t **obj);
int unlink_unixQ( fq_logger_t *l, char *path, char key_char );

#ifdef __cplusplus
}
#endif

#endif

