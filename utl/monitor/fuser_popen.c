/*
** fuser_popen.c
*/
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <curses.h>
#include <pthread.h>
#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/sem.h>

#include "fq_delimiter_list.h"
#include "fq_common.h"


/*
** cat /proc/sys/kernel/pid_max
*/
#define PID_MAX_LIMIT (65536)

int fuser_popen(char *pathfile, char **out)
{
	FILE *fp=NULL;
	char cmd[512];
	char pid_list[4096];
	int	n;
	delimiter_list_obj_t *obj = NULL;
	delimiter_list_t *this_entry=NULL;
	int i, rc;
	char real_list[4096];

	memset(cmd, 0x00, sizeof(cmd));
	memset(pid_list, 0x00, sizeof(pid_list));
	memset(real_list, 0x00, sizeof(real_list));

	sprintf(cmd, "fuser %s", pathfile);
	
	fp = popen(cmd, "r");

	if( !fp ) {
		return(-1);
	}

	n = fread(pid_list, sizeof(pid_list), 1, fp);
	if(n < 0) {
		if( fp ) pclose(fp);
		fp = NULL;
		return(-2);
	}
	/* Already we get list data , It is very important.*/
	if(fp!=NULL) {
		pclose(fp);
		fp = NULL;
	}

	rc = open_delimiter_list(NULL, &obj, pid_list, ' ');
	if( rc == FALSE ) {
		if( fp ) pclose(fp);
        fp = NULL;
        return(-3);
    }

	this_entry = obj->head;
	for( i=0;  (this_entry->next && this_entry->value); i++ ) {
		int pid;
		pid = atoi(this_entry->value);
		if( (pid > 1) && (pid < PID_MAX_LIMIT) && is_alive_pid_in_general( pid ) ) {
			strcat( real_list, this_entry->value );
			strcat( real_list, " ");
		}

		this_entry = this_entry->next;
	}

	close_delimiter_list(NULL, &obj);

	*out = strdup(real_list);

	return(strlen(*out));
}
