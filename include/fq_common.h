#ifndef _FQ_COMMON_H
#define _FQ_COMMON_H

#define FQ_COMMON_H_VERSION "1.0.4"

/* 1.0.2 :2013/09/02  get_seq() -> fq_get_seq() */
/* 1.0.3 :2013/11/13  add: str_trim_char() */
/* 1.0.4 :2014/03/25  add: int is_running_time(char *from, char *to); */

/* this line does not support on Linux Fedora15 also */
#include <unistd.h>

#include "config.h"
#include <stdbool.h>

#ifdef HAVE_UUID_GENERATE
#include <uuid/uuid.h>
#endif

#ifdef HAVE_SIGNAL_H
#include <signal.h>
#endif

#include "fq_defs.h"
#include "fq_cache.h"
#include "fq_logger.h"

#ifndef TRUE
#define TRUE	(1)
#endif

#ifndef FALSE
#define FALSE	(0)
#endif

#define MAX_SUB_FOLDERS 1000

#define _KEY_YES    1
#define _KEY_NO     0
#define _KEY_PLUS   2
#define _KEY_MINUS  3
#define _KEY_MULTIPLE   4
#define _KEY_DIVISION   5
#define _KEY_ZERO       6
#define _KEY_UNDEFINE   9
#define _KEY_CONTINUE   10

#define HIGHLIGHT_BEGIN "\033[1;40;31m"
#define HIGHLIGHT_END "\033[0;40;0m"

typedef struct _stopwatch_micro {
	struct timeval tp;
	long start_usec;
	long start_sec;
	long end_usec;
	long end_sec;
}stopwatch_micro_t;

typedef struct _stopwatch_nano {
	struct timespec ts;
	long start_nsec;
	long start_sec;
	long end_nsec;
	long end_sec;
}stopwatch_nano_t;

#ifdef __cplusplus
extern "C" {
#endif

#define THIS_BIG_ENDIAN     0
#define THIS_LITTLE_ENDIAN  1

#define C_COMMENT       '#'

#ifndef OS_HPUX
#define FQ_MIN(a,b)  ( (a) < (b) ? (a) : (b) )
#define FQ_MAX(a,b)  ( (a) > (b) ? (a) : (b) )
#endif

#define TOLOWER(a)      ( ((a) >= 'A' && (a) <= 'Z') ? (a)+32 : (a) )
#define TOUPPER(a)      ( ((a) >= 'a' && (a) <= 'z') ? (a)-32 : (a) )
#define VALUE(ptr)      ((ptr) ? (ptr) : "")
#define STRCMP(a,b)     (strcmp((a),(b)))
#define STRDUP(ptr)     ((ptr) ? strdup(ptr) : strdup(""))
#define STRNULL_FOR_NULL(x) ((x) ? (x) : "(null)")

#define ASSERT(EX) (void)((EX) || (assertf(#EX, __FILE__, __LINE__), 0))

#define SAFE_FREE(p) { if(p) (void)free(p); (p)=NULL;}
#define SAFE_FD_CLOSE(p) { if(p>0) close(p); p=-1;}
#define SAFE_FP_CLOSE(p) { if(p!=NULL) fclose(p); p=NULL;}

/* #define ASSERT(e) if(!(e)) { fq_log(l, "AssertException: aborting...\n"); abort(); } */

/* ---( PRIVATE )--- */
/* for get_seed_ratio_rand_str(); */
#define RANDSTR_MIN 32
#define RANDSTR_MAX 126
#define RANDSTR_RANGE ((RANDSTR_MAX + 1) - RANDSTR_MIN) + RANDSTR_MIN

#define CHECK(x) \
	do {\
		if (!(x)) { \
			fprintf(stderr, "%s:%d: ", __func__, __LINE__); \
			perror(#x); \
			fprintf(stderr, "Result is failure. Program will be stopped.\n");\
			exit(-1); \
		} \
	} while (0) \

/* ¹®ÀåÀÌ ÂüÀÌ ¾Æ´Ï¸é ·Î±×¸¦ ³²±â°í ÇÁ·Î±×·¥À» Á¾·áÇÑ´Ù.
** x °ª¿¡´Â ºñ±³¹®ÀÌ µé¾î°¡¾ß ÇÑ´Ù.
*/
#define FQ_CHECK(x) \
	do {\
		if (!(x)) { \
			fq_log(l, FQ_LOG_ERROR, "src=[%s],  reason=[%s].", #x, strerror(errno)); \
			perror(#x); \
			exit(-1); \
		} \
	} while (0) \

#define RCMD(context, ...) { \
	free ReplyObject(reply); \
	reply = redisCommand( context, __VA_ARGS__); \
	assert(reply != NULL) \
}
	
#define TEST(A) printf("%d %-72s-", __LINE__, #A);\
                if(A){puts(" OK");}\
                else{puts(" FAIL");}

int get_endian(void);
char *asc_time( time_t tval );
void printc(int color, const char *fmt, ...);
void packet_dump(const char* buf, int size, int unit, const char *departure);
int deFQ_packet_dump(fq_logger_t *l, const unsigned char* buf, int size, long seq, long run_time);
int STRCCMP(const void* s1, const void* s2);
int skip_comment(FILE *fp);
int get_token(FILE* fp, char *dst, int* line);
int get_token1(FILE* fp, char *dst, int* end_token_flag);
int get_feol(FILE* fp, char *dst);
int get_assign(const char *str, char *key, char *val);
void str_trim_char(char *str, char Charactor);
void str_trim(char *str);
void str_trim2(char *str);
void str_lrtrim(char *str);
void unquote(char* str);

void on_stopwatch_micro(stopwatch_micro_t *p);
void off_stopwatch_micro(stopwatch_micro_t *p);
void get_stopwatch_micro(stopwatch_micro_t *p, long *sec, long *usec);
long off_stopwatch_micro_result(stopwatch_micro_t *p);

#ifdef HAVE_CLOCK_GETTIME
void on_stopwatch_nano(stopwatch_nano_t *p);
void off_stopwatch_nano(stopwatch_nano_t *p);
void get_stopwatch_nano(stopwatch_nano_t *p, long *sec, long *msec);
void get_nano_time(long *sec, long *nsec);
#endif

void get_milisecond( char *timestr );

long get_random();
void get_TPS(long count, long sec, long nsec, float *tps);
char *get_seed_ratio_rand_str( char * buf, size_t count, const char *chrs /* character range used */);
int load_file_mmap(char *filename, char**ptr_dst, int *flen, char** ptr_errmsg);
int load_file_buf(char *path, char *qname, off_t de_sum, char**ptr_dst, int *flen, char** ptr_errmsg);
int get_item(const char *str, const char *key, char *dst, int len);
int str_gethexa(const char* src, char* dst);
int getitem(const char *str, const char *key, char *dst);
int get_item2(const char *str, const char *key, char *dst, int len);
void str_lrtrim2(char *str);
int get_arrayindex(const char* src, char* name, int* index);
int get_item_XML(const char *str, const char *key, char *dst, int len);

int save_big_file(fq_logger_t *l, const char *data, size_t len, const char* path, const char *qname,  off_t sum );

int save_file_mmap(char *data, size_t len, char *qfilename, off_t sum );
char* getprompt(const char* prompt, char *buf, int size);
char* getprompt_str(const char* prompt, char *str, int size);
int getprompt_int(const char* prompt, int *i, int size);
long getprompt_long(const char* prompt, long *i, int size);
float getprompt_float(const char* prompt, float *i, int size);
void getstdin( char *prompt, char input_string_buf[], int str_length);
int yesno();
double unix_getnowtime();
int _checklicense(const char *path);
int get_lsof(char *path, char *filename, tlist_t *p, char** ptr_errmsg);
int str_isblank(char* str);

int is_readable(char *path, int msg);

int get_uuid( char *dst, int dstlen);

unsigned long conv_endian(unsigned long x);
char *get_hostid( char *buf );

#ifndef _LINUX_COMPILE_
float get_cpu_idle(void);
int kernel_version(void);
int is_alive_pid( int mypid );
const char *username(void);
#endif
int get_used_CPU(char *buf, int vmstat_index);
int get_free_MEM(char *buf, int vmstat_index);
int get_used_DISK(char *filesystem_name, char *buf, int vmstat_index);
int get_cpu_mem_idle(char *CPU_idle_Percent, char *MEM_free_kilo );
void SetLimit();
int syslimit_print();
int bt_scandir(const char *s_path);
int pattern_match(char *form, char* input, char** ptr_errmesg);
void get_random_bytes(void *buf, int nbytes);

void get_time(char *todaystr, char* timestr);
void get_time2(char *todaystr, char* timestr);
void get_time3(char *datatime); /* 14 bytes */
void get_time_ms(char *todaystr, char* timestr, char *ms);
void get_date_time_ms( char *str );
void assertf(char* exp, char* srcfile, int lineno);
int errf(char *fmt, ...);
int oserrf(char *fmt, ...);

void  force_directories( const char *a_dirc );
int can_access_file( fq_logger_t *l, const char *fname);
int getFileSize(fq_logger_t *l, const char *filename);
int getProgName(const char *ArgZero, char *ProgName);
int MicroTime(long *sec, long *msec, char *asctime);
void redirect_stdfd();

int split_line(char *buf, char *p[]);

long fq_get_seq();
int GetSequeuce( fq_logger_t *l, char *prefix, int total_length, char *buf, size_t buf_size);

int is_busy_file(char *path, char *filename);

#ifdef HAVE_DIV
int is_multiple_int(int number, int multiple);
#endif

#ifdef HAVE_LDIV
int is_multiple_long(long number, long multiple);
#endif

#ifdef HAVE_SIGNAL_H
void set_signal_block(sigset_t *n, sigset_t *old);
void unset_signal_block(sigset_t *old);
#endif

#ifdef OS_LINUX
int is_using_file(char *filename);
#endif

void step_print( char *front, char *result);

int filecopy(const char *fn_src, const char *fn_dst, int setown, uid_t uid, gid_t gid, int setmode, mode_t mode);
time_t bin_time( char *src );
long time_diff( char *old_str1, char *new_str2);
int fq_usleep(unsigned int usec);
int is_file( char *fname);
void get_FileLastModifiedTime(char *path, char *todaystr, char* timestr);
double calc_rate (int bytes, double secs, int *units);
int ReplaceCharactorsInFile(char *in_filename, char *out_filename,  char OldChar, char NewChar, char** ptr_errmsg);
int is_running_time(char *from, char *to);
int is_exceeded_sec(time_t t, long limit);
int is_alive_pid_in_Linux( pid_t pid);
int is_alive_pid_in_general( pid_t pid);
time_t ConvertToSecSince1970(char *szYYYYMMDDHHMMSS);
int delimiter_parse(char *src, char delimeter, int items, char *dst[]);

void tps_usleep(int tps);
float get_percent(int total, int score);
int millisec2sec( int mseconds );
double microsec2sec( double microseconds );

unsigned int* chartoint32(unsigned char *pbData, int inLen);
unsigned char* int32tochar(unsigned int *pbData, int inLen);

char *decimal_to_binary_string(int n);
long factorial(int n);
long nCr(int n, int r); /* ¿¿ ¿¿¿ ¿ */
long nPr(int n, int r); /* ¿¿ ¿¿¿ ¿ */
long gcd(long, long); /* ¿¿¿¿¿ */
long reverse_long(long n);
int is_palindrome(int n);
int check_prime(int a);
int check_armstrong(long long n);
int check_anagram(char a[], char b[]);
int countNumWords(const char *str);
double ConvertSize(double bytes, char* type);
void print_current_time();

void replace(char * o_string, char * s_string, char * r_string);

size_t filesize(int fd);
size_t get_filesize(char *filename);
ssize_t block_size(int fd);
ssize_t mmap_copy_file(int in, int out);
int user_job(int speed_level);
int get_fuser(char *pathfile, char **out);
int queue_data_file_dump(fq_logger_t *l, char *qname, long seq,  const unsigned char* buf, int datasize);
int LoadFileToBuffer(char *filename, unsigned char **ptr_dst, int *flen, char** ptr_errmsg);
char * os_readfile(const char *name, size_t *len);
int isPowerOfTwo(int n);
void safe_fputs( char *src, FILE *stream);

int make_daemon(void);
char *sanitize(char *buf, char *s);
uint64_t milliseconds(void);
void subst(char *s, char from, char to);
void make_segment_fault();
bool is_working_time( fq_logger_t *l, char *from_time, char *to_time);
bool get_LR_value(char *src, char delimiter, char *left, char *right);
char *alloc_reverse( char *src );
bool chg_reverse( char *src );
void exchg_reverse( char *src );
int  find_a_line_from_file( fq_logger_t *l, char *filename, char *line_string);
int delete_a_line_from_file( fq_logger_t *l, char *filename, int delete_line);
int replace_a_line_from_file( fq_logger_t *l, char *filename, int replace_line, char *replace_string);
int day_of_year(int year, int month, int day);
#ifdef __cplusplus
}
#endif

#endif
