#ifndef _FQ_SYSLOG_H
#define _FQ_SYSLOG_H

#define FQ_SYSLOG_H_VERSION "1.0.0"

#ifdef __cplusplus
extern "C" {
#endif

#define MSG_LVL_INFO	0
#define MSG_LVL_ERROR	1
#define MSG_LVL_WARN	2
#define MSG_LVL_ETC		3

void init_strace( char *pname);
void strace(int level, char *fmt, ...);
void end_strace();

#ifdef __cplusplus
}
#endif

#endif
