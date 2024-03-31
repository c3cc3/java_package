/*
** monitor.c
** Description: This program sees status of all file queue semi-graphically.
*/
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ncurses/curses.h>
#include <pthread.h>
#include <pwd.h>
#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/sem.h>

#include "fqueue.h"
#include "shm_queue.h"
#include "fq_socket.h"
#include "screen.h"
#include "fq_file_list.h"
#include "fuser_popen.h"
#include "fq_manage.h"
#include "parson.h"
#include "fq_external_env.h"
#include "monitor.h"
#include "fq_timer.h"

/* Customizing Area */
#define PRODUCT_NAME "M&Wise UMS Distributor"
/* Please type your ethernet device name with  $ ip addr command */
#define ETHERNET_DEVICE_NAME "eno1"

#define CURSOR_X_POSITION (50)  // key waiting cursor position
#define MAX_FILES_QUEUES (500)
#define USAGE_THRESHOLD (90.0)
#define DISK_THRESHOLD (1500000)

#define MONOTORING_WAITING_MSEC (1000000)

size_t	used_total_file_size;

static void *main_thread( void *arg );
static void main_title(int y, char *fname);
static void disp_time(int y, int x, int rows, int cols);
static void disp_hostname_ip(int y, int x, int opened_q_cnt);
static void disp_queue_data_home_dir(int y, int x, char *qpath);
static void *waiting_key_thread(void *arg);
static void help(void);
static void disp_GetAvailableSpace(int y, int x, const char* path);
static void detail_view(char key);

char g_eth_name[16];

bool_t user_quit_flag = 0;
/* Global variables */
typedef struct Queues {
	fqueue_obj_t *obj;
	unsigned char old_data[65536];
	bool_t	status;
	int		en_tps;
	int		de_tps;
	long	before_en;
	long	before_de;
	time_t	en_competition;
	time_t	de_competition;
	long	en_sum;
	long	de_sum;
	int big;	
	int		shmq_flag;
	int		q_status;
	pthread_mutex_t	mutex;
} Queues_t;

Queues_t Qs[MAX_FILES_QUEUES];

pthread_mutex_t mutex;
screen_t    s;
int y_status;
int get_y_position;
int current_sleep = 1;
int reset_flag=0;

void init_Qs() {
	int i, rc;
	for(i=0; i<MAX_FILES_QUEUES; i++) {
		Qs[i].obj = NULL;
		Qs[i].old_data[0] = 0x00;
		Qs[i].status = False;
		Qs[i].en_tps = 0;
		Qs[i].de_tps = 0;
		Qs[i].before_en = 0L;
		Qs[i].before_de = 0L;
		Qs[i].en_competition = 0L;
		Qs[i].de_competition = 0L;
		Qs[i].en_sum = 0L;
		Qs[i].de_sum = 0L;
		rc = pthread_mutex_init (&Qs[i].mutex, NULL);
		CHECK( rc == 0);
		Qs[i].big = 0;
		Qs[i].shmq_flag = 0;
	}
}

char *g_path = NULL;
char fq_data_home[16+1];

int main(int ac, char **av)
{
    int rc;
    pthread_t thread_cmd, thread_main;
    int thread_id;

	/* shared memory */
    key_t     key=5678;
    int     sid = -1;
    int     size;

	if( ac == 1 ) {
		g_path = getenv("FQ_DATA_HOME");
		if( !g_path ) {
			getprompt("There is no FQ_DATA_HOME in env. Type your FQ_DATA_HOME: ", fq_data_home, 16);
			g_path = fq_data_home;
		}
	} else if( ac == 2 ) {
		g_path = av[1];
	} else {
		fprintf(stderr, "Usage: $ %s [FQ_DATA_HOME]", av[0]);
		exit(0);
	}

	init_Qs();

    rc = pthread_mutex_init (&mutex, NULL);
    if( rc !=0 ) {
        perror("pthread_mutex_init"), exit(0);
    }

    init_screen(&s);

    pthread_create(&thread_main, NULL, main_thread, &thread_id);
	sleep(2);
    pthread_create(&thread_cmd, NULL, waiting_key_thread, &thread_id);

    pthread_join(thread_cmd, NULL);

    end_screen(&s, 0);

    return(0);
}

typedef struct thread_params {
	int array_index;
	fqueue_obj_t *obj;
	fq_logger_t *l;
	time_t waiting_msec;
} thread_params_t;

/*
** This thread gets values of self and puts it to Qs array.
*/
void *mon_thread( void *arg ) {

	thread_params_t	*tp = arg;
	fqueue_obj_t *obj = tp->obj;
	fq_logger_t	*l = tp->l;
	int array_index = tp->array_index;
	time_t waiting_msec = tp->waiting_msec;
	
	fq_log(l, FQ_LOG_INFO, "[%d]-th mon_thread() started.", array_index);

	while(1) {
		bool_t status = False;

		if( user_quit_flag ) break;

		pthread_mutex_lock(&Qs[array_index].mutex);

#if 1
		Qs[array_index].en_competition = obj->on_check_competition( l, obj, FQ_EN_ACTION);
		if( Qs[array_index].en_competition > 1000) {
			fq_log(l, FQ_LOG_ERROR, "en_competition: [%ld] micro second.", Qs[array_index].en_competition);
		}
		Qs[array_index].de_competition = obj->on_check_competition( l, obj, FQ_DE_ACTION);
		if( Qs[array_index].de_competition > 1000) {
			fq_log(l, FQ_LOG_ERROR, "de_competition: [%ld] micro second.", Qs[array_index].de_competition);
		}
#else
	
		 Qs[array_index].en_competition = 1;
		Qs[array_index].de_competition = 1;

#endif

		// This has a bug, so we display alway Good.
		// bug
		// status = obj->on_do_not_flow( l, obj, waiting_msec );
		if( Qs[array_index].en_competition < 1000 && Qs[array_index].de_competition < 1000) {
			Qs[array_index].status = 0; // Good
		}
		else {
			Qs[array_index].status = 0; // Bad
		}

		if( Qs[array_index].before_en > 0 )  Qs[array_index].en_tps = obj->h_obj->h->en_sum - Qs[array_index].before_en;
		else Qs[array_index].en_tps = Qs[array_index].before_en;
		Qs[array_index].before_en = obj->h_obj->h->en_sum;

		if( Qs[array_index].before_de > 0 )  Qs[array_index].de_tps = obj->h_obj->h->de_sum - Qs[array_index].before_de;
		else Qs[array_index].de_tps = Qs[array_index].before_de;
		Qs[array_index].before_de = obj->h_obj->h->de_sum;

		if( status ) {
			fq_log(l, FQ_LOG_ERROR, "index=[%d]: path=[%s] qname=[%s] status=[%d].", array_index, obj->path, obj->qname, status);
		}
		Qs[array_index].en_sum = obj->h_obj->h->en_sum;
		Qs[array_index].de_sum = obj->h_obj->h->de_sum;

		Qs[array_index].q_status = obj->h_obj->h->status;
	

		pthread_mutex_unlock(&Qs[array_index].mutex);
		usleep(1000000); // 1 second
	}

	fq_log(l, FQ_LOG_INFO, "[%d]-th mon_thread() exit.", array_index);
	pthread_exit( (void *)0 );
}


/*
** This function open all file queues and create each monitoring thread.
*/
int open_all_file_queues_and_run_mon_thread(fq_logger_t *l, fq_container_t *fq_c, int *opened_q_cnt) {
	dir_list_t *d=NULL;
	fq_node_t *f;
	int opened = 0;

	for( d=fq_c->head; d!=NULL; d=d->next) {
		pthread_attr_t attr;
		pthread_t *mon_thread_id=NULL;
		thread_params_t *tp=NULL;

		for( f=d->head; f!=NULL; f=f->next) {
			int rc;

			fq_log(l, FQ_LOG_DEBUG, "OPEN: [%s/%s].", d->dir_name, f->qname);
			
			char *p=NULL;
			if( (p= strstr(f->qname, "SHM_")) == NULL ) {
				rc =  OpenFileQueue(l, NULL, NULL, NULL, NULL, d->dir_name, f->qname, &Qs[opened].obj);
			} 
			else {
				rc =  OpenShmQueue(l, d->dir_name, f->qname, &Qs[opened].obj);
				Qs[opened].shmq_flag = 1;
			}
			if( rc != TRUE ) {
				fq_log(l, FQ_LOG_ERROR, "OpenFileQueue('%s') error.", f->qname);
				return(-1);
			}
			fq_log(l, FQ_LOG_DEBUG, "%s:%s open success.", d->dir_name, f->qname);

			mon_thread_id = calloc(1, sizeof(pthread_t));
			tp = calloc(1, sizeof(thread_params_t));

			/*
			** Because of shared memory queue, We change it to continue.
			*/
			// CHECK(rc == TRUE);

			tp->array_index = opened;
			tp->obj = Qs[opened].obj;
			tp->l = l;
			tp->waiting_msec = MONOTORING_WAITING_MSEC;

			used_total_file_size += tp->obj->h_obj->h->file_size;

			pthread_attr_init(&attr);
			pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
			rc = pthread_create( mon_thread_id, &attr, mon_thread, tp);
			CHECK(rc == 0 );
			pthread_attr_destroy(&attr);
			opened++;
		}
	}

	fq_log(l, FQ_LOG_INFO, "Number of opened filequeue is [%d].", opened);
	*opened_q_cnt = opened;
	return(0);
}

void close_all_file_queues(fq_logger_t *l) {
	int i;

	for(i=0; Qs[i].obj; i++) {
		if( Qs[i].obj  && (Qs[i].shmq_flag == 0)) CloseFileQueue(l, &Qs[i].obj);
		else if( Qs[i].obj  && (Qs[i].shmq_flag == 1)) CloseShmQueue(l, &Qs[i].obj);
	}
}

typedef struct body_contents {
	int	odinal;
	char *qpath;
	char *qname;
	int max_rec_cnt;
	int gap;
	float usage;
	time_t	en_competition;
	time_t	de_competition;
	unsigned char data[65536];
	bool_t	status;
	int	en_tps;
	int de_tps;
	int	big;
	int msglen;
	long en_sum;
	long de_sum;
	int	shmq_flag;
	int q_status;
} body_contents_t;


int fill_body_contents_with_qobj( fq_logger_t *l,  int index, fqueue_obj_t *obj, body_contents_t *bc) {
	int rc;

	unsigned char buf[65536];
	long l_seq, run_time;
	

	bc->odinal = index;
	bc->qpath = obj->path;
	bc->qname = obj->qname;
	bc->max_rec_cnt = obj->h_obj->h->max_rec_cnt;
	bc->gap = obj->on_get_diff(l, obj);
	bc->usage = obj->on_get_usage(l, obj);
	bc->en_competition = Qs[index].en_competition;
	bc->de_competition = Qs[index].de_competition;
	bc->en_sum = Qs[index].en_sum;
	bc->de_sum = Qs[index].de_sum;

	sprintf(bc->data, "%20.20s", "Ready...");

	bc->status = Qs[index].status;
	bc->en_tps = Qs[index].en_tps;
	bc->de_tps = Qs[index].de_tps;
	bc->big = obj->on_get_big(l, obj);
	bc->msglen = obj->h_obj->h->msglen;
	bc->shmq_flag = Qs[index].shmq_flag;
	bc->q_status = Qs[index].q_status;

	return(0);
}

#define MAX_COLUMNS 15
int	screen_format[MAX_COLUMNS+1] = { 1, 7, 25, 35, 45, 53, 62, 71, 79,  87,  97,  110, 124};
								   /*0, 1, 2,  3,  4,  5,  6,  7,  8,   9,   10,  11, 12*/

void sub_title( int py ) {
	int i;

	attron(A_BOLD);
	// attron(A_REVERSE);
	for(i=0; (i< MAX_COLUMNS && screen_format[i]>0) ; i++) {
		move(py, screen_format[i]);
		switch(i) {
			case 0:
				printw("%s", "NO");
				break;
			case 1:
				printw("%s", "QNAME");
				break;
			case 2:
				printw("%s", "MAX_RECO");
				break;
			case 3:
				printw("%s", "GAP");
				break;
			case 4:
				printw("%s", "Usage");
				break;
			case 5:
				printw("%s", "enQ_lock");
				break;
			case 6:
				printw("%s", "deQ_lock");
				break;
			case 7:
				printw("%s", "BIG");
				break;
			case 8:
				printw("%s", "TPS(en)");
				break;
			case 9:
				printw("%s", "TPS(de)");
				break;
			case 10:
				printw("%s", "en_sum");
				break;
			case 11:
				printw("%s", "de_sum");
				break;
			case 12:
				printw("%s", "Q_path");
				break;
			default:
				break;
		}
	}
	attroff(A_BOLD);
	// attron(A_REVERSE);
	return;
}

int display_screen( int py,  body_contents_t *bc) {
	int i;

	move(py, 0); clrtoeol(); /* clear line to end */

	for(i=0; (i< MAX_COLUMNS && screen_format[i]>0) ; i++) {
		move(py, screen_format[i]);
		switch(i) {
			case 0:
				if( bc->odinal >= 0 && bc->odinal <= 9 ) {
					if( bc->q_status == 0 ) {
						printw("%d(%c)", bc->odinal, bc->odinal+48);
					}
					else {
						init_pair(1, COLOR_RED, COLOR_BLACK); /* init_pair(pairNum,fg,bg) */
						attrset(COLOR_PAIR(1)); /* set new color attribute */
						printw("%d(%c)-X", bc->odinal, bc->odinal+48);
						attrset(COLOR_PAIR(0)); /* set old color attribute */
					}
				}
				else {
					if( bc->q_status == 0 ) {
						printw("%d(%c)", bc->odinal, bc->odinal+55);
					}
					else {
						init_pair(1, COLOR_RED, COLOR_BLACK); /* init_pair(pairNum,fg,bg) */
						attrset(COLOR_PAIR(1)); /* set new color attribute */
						printw("%d(%c)-X", bc->odinal, bc->odinal+55);
						attrset(COLOR_PAIR(0)); /* set old color attribute */
					}
				}
				break;
			case 1:
				if(bc->shmq_flag) 
					printw("%s(s)", bc->qname);
				else
					printw("%s", bc->qname);
				break;
			case 2:
				printw("%d", bc->max_rec_cnt);
				break;
			case 3:
				printw("%d", bc->gap);
				break;
			case 4:
				if( bc->usage > USAGE_THRESHOLD ) {
					init_pair(1, COLOR_RED, COLOR_BLACK); /* init_pair(pairNum,fg,bg) */
					attrset(COLOR_PAIR(1)); /* set new color attribute */
					printw("%5.2f", bc->usage);
					attrset(COLOR_PAIR(0)); /* set old color attribute */
				} else if( bc->usage > 50 ) {
					init_pair(2, COLOR_YELLOW, COLOR_BLACK); /* init_pair(pairNum,fg,bg) */
					attrset(COLOR_PAIR(2)); /* set new color attribute */
					printw("%5.2f", bc->usage);
					attrset(COLOR_PAIR(0)); /* set old color attribute */
				} else if( bc->usage > 20 ) {
					init_pair(3, COLOR_GREEN, COLOR_BLACK); /* init_pair(pairNum,fg,bg) */
					attrset(COLOR_PAIR(3)); /* set new color attribute */
					printw("%5.2f", bc->usage);
					attrset(COLOR_PAIR(0)); /* set old color attribute */
				} else {
					printw("%5.2f", bc->usage);
				}
				break;
			case 5:
#if 0
				if( bc->en_competition == 0 ) {
					init_pair(1, COLOR_RED, COLOR_BLACK); /* init_pair(pairNum,fg,bg) */
					attrset(COLOR_PAIR(1)); /* set new color attribute */
					printw("%s",  "locked");
					attrset(COLOR_PAIR(0)); /* set old color attribute */
				}
				else {
					printw("%ld", bc->en_competition);
				}
#else
				printw("%ld", bc->en_competition);
#endif
				break;
			case 6:
#if 0
				if( bc->de_competition == 0 ) {
					init_pair(1, COLOR_RED, COLOR_BLACK); /* init_pair(pairNum,fg,bg) */
					attrset(COLOR_PAIR(1)); /* set new color attribute */
					printw("%s",  "locked");
					attrset(COLOR_PAIR(0)); /* set old color attribute */
				}
				else {
					printw("%ld", bc->de_competition);
				}
#else
				printw("%ld", bc->de_competition);

#endif

				break;
			case 7:
				printw("%d", bc->big);
				break;
			case 8:
				if( bc->en_tps > 0 ) {
					init_pair(3, COLOR_GREEN, COLOR_BLACK); /* init_pair(pairNum,fg,bg) */
					attrset(COLOR_PAIR(3)); 
					printw("%d", bc->en_tps);
					attrset(COLOR_PAIR(0)); /* set old color attribute */
				} else {
					printw("%d", bc->en_tps);
				}
				break;
			case 9:
				if( bc->en_tps > 0 ) {
                    attrset(COLOR_PAIR(3)); /* set new color attribute */
                    printw("%d", bc->de_tps);
                    attrset(COLOR_PAIR(0)); /* set old color attribute */
                } else {
                    printw("%d", bc->de_tps);
                }
				break;
			case 10:
				printw("%ld", bc->en_sum);
				break;
			case 11:
				printw("%ld", bc->de_sum);
				break;
			case 12:
				printw("%s", bc->qpath);
				break;
			default:
				break;
		}
	}
				
	return(0);
}

#define MAX_USERID_LENGTH 16
static void *main_thread( void *arg )
{
    int     cmd_y;
    int     y_err=1;
    int     rc;
	int status=0;
	int y=0;
	int	port;
	int px=0, py=0;
	int	fd, lockfd;
	char *path = NULL;
	char ListInfoFile[256];
	fq_logger_t *l=NULL;
	int opened_q_cnt = 0;
	unsigned char buf[65536];
	long l_seq, run_time;
	external_env_obj_t *external_env_obj = NULL;

	fq_container_t *fq_c=NULL;

	char username[MAX_USERID_LENGTH];

	struct passwd *user_info;
	uid_t user_id;
	user_id = getuid();
	user_info = getpwuid(user_id);
	// cuserid(username);

	char logfilename[256];
	sprintf(logfilename, "/tmp/monitor_%s.log", user_info->pw_name);
	rc = fq_open_file_logger(&l, logfilename, FQ_LOG_ERROR_LEVEL);
	CHECK(rc == TRUE);

	/* here error : core dump */
	rc = load_fq_container(l, &fq_c);
	if( rc != TRUE ) {
		fq_log(l, FQ_LOG_ERROR, "load_fq_container() error.");
		goto stop;
	}

	rc = open_all_file_queues_and_run_mon_thread(l, fq_c, &opened_q_cnt);
	if(rc != 0 ) {
		fq_log(l, FQ_LOG_ERROR, "open_all_file_queues_and_run_mon_thread() error.");
		goto stop;
	}

	open_external_env_obj( l, g_path, &external_env_obj);
	if( external_env_obj ) {
		external_env_obj->on_get_extenv( l, external_env_obj, "eth", g_eth_name);
		fq_log(l, FQ_LOG_EMERG, "eth='%s'.", g_eth_name);
	}
	else {
		sprintf(g_eth_name, "%s", "eth0");
	}

	clear();

	int old_row_wsize=0, old_col_wsize=0;
	int cur_row_wsize=0, cur_col_wsize=0;

	double passed_time;
	start_timer();
	while(1) {
		int i;

		cur_row_wsize = get_row_winsize();
		cur_col_wsize = get_col_winsize();

		if( old_row_wsize == 0 || old_col_wsize == 0 ) {
			old_row_wsize = cur_row_wsize;
			old_col_wsize = cur_col_wsize;
		}
		else {
			if( (old_row_wsize != cur_row_wsize) || (old_col_wsize != cur_col_wsize) ) {
				fq_log(l, FQ_LOG_INFO, "Changed: rows=[%d], cols=[%d].", cur_row_wsize, cur_col_wsize);
				clear();
				refresh();
			}
				
			old_row_wsize = cur_row_wsize;
			old_col_wsize = cur_col_wsize;

		}
		passed_time = end_timer();
		if( passed_time > 5.0 ) {
			clear();
			refresh();
			start_timer();
		}

		if( user_quit_flag ) break;
		py = 0;
		y_status = get_row_winsize()-2;
		cmd_y = get_row_winsize();

        status = pthread_mutex_lock (&mutex);

        if( status !=0 ) {
            mvprintw(10, 1, "pthread_mutex_lock error.");
			break;
        }

		disp_time(py++, 0, cur_row_wsize, cur_col_wsize);
		disp_hostname_ip(py++,0, opened_q_cnt);
		disp_queue_data_home_dir( py++,0, g_path);
		disp_GetAvailableSpace(py++,0, g_path);

        main_title(py++, "LogCollector File Queue monitoring");

		px = 1;
		py++;

		sub_title(py);
		py++;
	
		/* display body values */
		for(i=0; Qs[i].obj; i++) {
			body_contents_t bc;
			int waiting_msec = 100000;
			int text_color = COLOR_WHITE;

			rc = fill_body_contents_with_qobj(l, i, Qs[i].obj, &bc);
			CHECK(rc == 0);

			rc = display_screen( py, &bc);
			CHECK( rc == 0 );

			py++;
		}
		
		draw_bar(py++, 0);

        move(get_y_position, CURSOR_X_POSITION);
        refresh();

        pthread_mutex_unlock (&mutex);
        sleep(1); /* If you remove it, you will get core-dump */
    }

    move(y_err, 0); clrtoeol();


	if(fq_c) fq_container_free(&fq_c);

	close_all_file_queues(l);

	if(l) fq_close_file_logger(&l);

stop:
	if(!user_quit_flag) {
		clear();
		mvprintw(0, 0 , "fatal: See logfile(/tmp/monitor.log). Good bye!  Press 'q'.");
		refresh();
		getch();
		end_screen(&s, 0);
		exit(0);
	}

    pthread_exit((void *)0);
}

#include<sys/utsname.h>   /* Header for 'uname'  */

static void help(void)
{
    int x=0, y=0;
	struct utsname uname_pointer;

	uname(&uname_pointer);

    clear();

    mvprintw(y++, x, "Semi-graphic Viewer version %s, Copyright (c) 2019 through 2010, Gwisang Choi", FQUEUE_LIB_VERSION);
    mvprintw(y++, x, "Compiled on %s %s ", __TIME__, __DATE__);
	y++;
	mvprintw(y++, x, "-----< How to connect to developer  >-------");
    mvprintw(y++, x, "Connect to Author -> Mobile: %s,  e-mail: %s ", "010-7202-1516", "gwisang.choi@gmail.com");
    mvprintw(y++, x, "Connect to Company(CLANG Co.) -> Tel: %s", "031-916-1516");

	y++;
	mvprintw(y++, x, "-----< Environment of compiled system >-------");
	mvprintw(y++, x, "- System name : %s", uname_pointer.sysname);
	mvprintw(y++, x, "- Nodename    : %s", uname_pointer.nodename);
	mvprintw(y++, x, "- Release     : %s", uname_pointer.release);
	mvprintw(y++, x, "- Version     : %s", uname_pointer.version);
	mvprintw(y++, x, "- Machine     : %s", uname_pointer.machine);
 	// printf("Domain name - %s \n", uname_pointer.domainname);
	y++;
	mvprintw(y++, x, "-----< Configurations of Program >-------");
	mvprintw(y++, x, "- MAX_FILES_QUEUES       : %d", MAX_FILES_QUEUES);
	mvprintw(y++, x, "- USAGE_THRESHOLD        : %f", USAGE_THRESHOLD);
	mvprintw(y++, x, "- DISK_THRESHOLD(blocks) : %d", DISK_THRESHOLD);

    y++;

    mvprintw(y++,x, "Available command charactors.");
    mvprintw(y++,x, "q\t -quit");
    mvprintw(y++,x, "r\t -reset");
    mvprintw(y++,x, "h or ?\t -help: show this text");

    y++;
    y++;
    y++;
    attrset(A_REVERSE);
    mvprintw(y,x, "Hit any key to continue: ");
    attrset(0);
    refresh();
    getch();
    clear();
    return;
}

static void detail_view(char key)
{
    int x=0, y=0;
	struct utsname uname_pointer;
	fqueue_obj_t *obj=NULL;
	int i;
	long i_before_en = 0;
	long i_before_de = 0;

	uname(&uname_pointer);

    clear();

	if( key >= '0' && key <= '9')
		i = key - 48;
	else
		i = key - 55;

	obj = Qs[i].obj;

	while(1) {
		int input_key;
		char *fuser_out = NULL;
		int rc;

		clear();
		disp_time(y++, x, 0, 0);
		mvprintw(y++, x, "%s", "---------------< Status of File Queue>--------------");
		mvprintw(y++, x, "- path        : %s", obj->path);
		mvprintw(y++, x, "- qname       : %s", obj->qname);
		mvprintw(y++, x, "- description : %s", obj->h_obj->h->desc);
		mvprintw(y++, x, "- header file : %s", obj->h_obj->name);
		mvprintw(y++, x, "- data   file : %s", obj->d_obj->name);
		mvprintw(y++, x, "- message size: %ld", obj->h_obj->h->msglen);
		mvprintw(y++, x, "- file size   : %ld", (long int)obj->h_obj->h->file_size);
		mvprintw(y++, x, "- multi num   : %ld", (long int)obj->h_obj->h->multi_num);
		mvprintw(y++, x, "- mapping num : %ld", (long int)obj->h_obj->h->mapping_num);
		mvprintw(y++, x, "- mapping len : %ld", (long int)obj->h_obj->h->mapping_len);
		mvprintw(y++, x, "- records     : %d", obj->h_obj->h->max_rec_cnt);
		mvprintw(y++, x, "- income(TPD) : %ld-(%ld)", obj->h_obj->h->en_sum, (i_before_en>0)?(obj->h_obj->h->en_sum-i_before_en): i_before_en);
		i_before_en = obj->h_obj->h->en_sum;
		mvprintw(y++, x, "- outcome(TPD): %ld-(%ld)", obj->h_obj->h->de_sum, (i_before_de>0)?(obj->h_obj->h->de_sum-i_before_de): i_before_de);
		i_before_de = obj->h_obj->h->de_sum;
		mvprintw(y++, x, "- diff        : %ld", obj->on_get_diff(NULL, obj));
		mvprintw(y++, x, "- BIG files   : %d", obj->h_obj->h->current_big_files);
	
		char *p=NULL;
		mvprintw(y++, x, "- last en time: %s", (p=asc_time(obj->h_obj->h->last_en_time)));
		if(p) free(p);
		mvprintw(y++, x, "- last de time: %s", (p=asc_time(obj->h_obj->h->last_de_time)));
		if(p) free(p);

		rc = fuser_popen( obj->d_obj->name, &fuser_out);
		if( rc > 0 ) {
			mvprintw(y++, x, "- using pids  : %s", fuser_out);
			SAFE_FREE(fuser_out);
		}
		else {
			mvprintw(y++, x, "- using pids  : %s", "none");
		}

		mvprintw(y++, x, "- status      : %d", obj->h_obj->h->status);

		y++;
		mvprintw(y,x, "Press space key for refreshing or quit(q)");
		refresh();

		input_key = getch();

		if(input_key == 'q') break;
		else {
			y = 0;
			continue;
		}
	}
    refresh();
    clear();
    refresh();
    return;
}


static void main_title(int y, char *fname)
{
    int		i;
    char    Center_Title[128];
	int		solution_no;

    sprintf(Center_Title, "[ %s FileQueue Status. library version %s ]", PRODUCT_NAME, FQUEUE_LIB_VERSION);

    center_disp(y, Center_Title);
    draw_bar(y+1, 0);

    return;
}

static void *waiting_key_thread(void *arg)
{
    int key, cmd_y;

    cmd_y = get_row_winsize();
    y_status = cmd_y-2;

    while(1) {
        int status;
        char szCmd[128];

        move(cmd_y-1, 0);
        get_y_position = cmd_y-1;
        clrtoeol();
        mvprintw(cmd_y-1, 0, "%s", "Type a key[h(help), hotkey(for detail), q(quit)]>");
         move(cmd_y-1, 57);
		// refresh();
		// wmove(my_win, y, x);
        key = getch();

        status = pthread_mutex_lock (&mutex);
        if( status !=0 ) {
            mvprintw(10, 1, "pthread_mutex_lock error.");
            pthread_exit(0);
        }
		switch(key) {
            case 'q':
            case KEY_F(4):
				user_quit_flag = True;
                pthread_mutex_unlock (&mutex);
				sleep(1);
				// close_all_file_queues(NULL);
                goto L0;
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '5':
			case '6':
			case '7':
			case '8':
			case '9':
			case 'A':
			case 'B':
			case 'C':
			case 'D':
			case 'E':
			case 'F':
			case 'G':
			case 'H':
			case 'I':
			case 'J':
			case 'K':
			case 'L':
			case 'M':
			case 'N':
			case 'O':
			case 'P':
			case 'Q':
			case 'R':
			case 'S':
			case 'T':
			case 'U':
			case 'V':
			case 'W':
			case 'X':
			case 'Y':
			case 'Z':
				detail_view(key);
				clear();
				break;
            case 'h':
            case '?':
                help();
                break;
            case 'r':
            case KEY_F(1):
				clear();
				refresh();
				reset_flag=1;
                break;
            default:
                mvprintw(y_status, 0, "Error: [%c] is invalid key.", key);
                break;
        }
        refresh();
        pthread_mutex_unlock (&mutex);
    }
L0:
    endwin();
    exit(0);
    // pthread_exit((void *)0);
}

static void disp_time(int y, int x, int rows, int cols)
{
    char    szTime[30];
    long    sec, usec;

    memset(szTime, 0x00, sizeof(szTime));
    MicroTime(&sec, &usec, szTime);
    move(y,x);
    clrtoeol();
    mvprintw(y,x, "%s -refresh interval(%d),   rows(%d) cols(%d), Press key: Fefresh:<F1> quit<F4>", szTime, current_sleep, rows, cols);
    return;
}

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <netinet/in.h>
#include <net/if.h>

static void disp_hostname_ip(int y, int x, int opened_q_cnt)
{
	char hostname[64];
	int fd;
	struct ifreq ifr;
	char IPbuffer[32];

	gethostname(hostname, 64);

	fd = socket(AF_INET, SOCK_DGRAM, 0);
	ifr.ifr_addr.sa_family = AF_INET;
	snprintf(ifr.ifr_name, IFNAMSIZ, "%s", g_eth_name);
	// int snprintf(char *str, size_t size, const char *format, ...);
	ioctl(fd, SIOCGIFADDR, &ifr);

	sprintf(IPbuffer, "%s", inet_ntoa(((struct sockaddr_in *)&ifr.ifr_addr)->sin_addr));
	close(fd);

    move(y,x);
    clrtoeol();
    mvprintw(y,x, "- Hostname: %s,  - IP address: %s - Number of Queues: %d", hostname, IPbuffer, opened_q_cnt);
    return;
}

static void disp_queue_data_home_dir(int y, int x, char *qpath)
{
	attron(A_BOLD);
    mvprintw(y,x, "FQ_DATA_HOME: %s, Allocated Disk: %ld", qpath, used_total_file_size);
	attroff(A_BOLD);
    return;
}
#include <sys/statvfs.h>

static void disp_GetAvailableSpace(int y, int x, const char *path)
{
	struct statvfs stat;

	if (statvfs(path, &stat) != 0) {
		return;
	}

	if( stat.f_bavail < DISK_THRESHOLD) {
		attrset(COLOR_PAIR(1)); /* set new color attribute */
		// mvprintw(y,x, "Available Disk Space: %ld", stat.f_bsize);
		mvprintw(y,x, "Available Disk Space: %ld blocks.(1 block=%ld)", stat.f_bavail, stat.f_bsize);
		// mvprintw(y,x, "Available Disk Space: %ld", (stat.f_bsize * stat.f_bavail));
		attrset(COLOR_PAIR(0)); /* set old color attribute */
	} else {
		mvprintw(y,x, "Available Disk Space: %ld blocks.(1 block=%ld)", stat.f_bavail, stat.f_bsize);
	}

    return;
}
