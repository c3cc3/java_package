/*
** fq_lsof.h
*/
#ifndef OS_AIX
int lsof_main(int argc, char *argv[]);
int find_pid_fds(long int pid);
#endif
