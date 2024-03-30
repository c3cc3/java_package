#ifndef _FQ_UTL_H
#define _FQ_UTL_H

#define FQ_UTL_H_VERSION "1.0.0"

#ifdef __cplusplus
extern "C" {
#endif

int MakeTestData( char *fn, int cnt, int msglen );
int CountFileLine( char *fn, int *LineLength );
bool checkFileSystemSpace( fq_logger_t *l, const char *path, unsigned long long requiredSpace);

#ifdef __cplusplus
}
#endif

#endif


