/*
** fq_rlock.h
** Include this header file for using record locking library.
*/
#define FQ_RLOCK_H_VERSION "1.0.0"

#ifndef _FQ_RLOCK_H
#define _FQ_RLOCK_H

#ifdef __cplusplus
extern "C"
{
#endif

#if 0
int create_db(char *dbname, size_t record_size, int max_records);
#endif

int r_lock( int fd,  size_t rec_length, int rec_index );
int r_unlock( int fd, size_t rec_length, int rec_index );

int create_lock_table(char *path, char *lock_table_name, int max_records);
int open_lock_table(char *lock_table_name);
int close_lock_table(int fd);
int r_lock_table( int fd,  int index );
int r_unlock_table( int fd, int rec_index );
int unlink_lock_table(char *lock_table_name);
#ifdef __cplusplus
}
#endif

#endif
