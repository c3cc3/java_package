/*
*/
#include <stdio.h>
#include <stdbool.h>
#include <libgen.h>

#include "fq_common.h"
#include "fq_linkedlist.h"
#include "fq_delimiter_list.h"

#include "fq_codemap.h"
#include "fqueue.h"
#include "fq_file_list.h"
#include "ums_common_conf.h"
#include "fq_timer.h"
#include "fq_hashobj.h"
#include "fq_tci.h"
#include "fq_cache.h"
#include "fq_conf.h"
#include "fq_gssi.h"
#include "fq_mem.h"

#include "virtual_gw_conf.h"

static int uchar_enQ(fq_logger_t *l, unsigned char *data, size_t data_len, fqueue_obj_t *obj);
static void fill_qformat_local(qformat_t *w, char* postdata, fqlist_t *t, char *bytestream);
static int format_conversion_local( char** str, char* fn, int len, char* codes);
static int parse_codeseq_local(char* codes, int *codeval);
static int do_convert_local( int code, char *src, char *dst, int len, char *fn);

static int fit_chars(int code, char* src, char* dst, int len);
static int to_number(int code, char* src, char* dst, int len);
static int to_lower(int code, char* src, char* dst, int len);
static int to_upper(int code, char* src, char* dst, int len);
static int ins_chars_to_date(int code, char* src, char* dst, int len);
static int add_string(int code, char* src, char* dst, int len);
static int change_chars(int code, char* src, char* dst, int len);
static int del_chars(int code, char* src, char* dst, int len);
static int fill_chars(int code, char* src, char* dst, int len);
static int check_chars(int code, char* src, char* dst, int len);
static int check_length(int code, char* src, char* dst, int len);

static char* get_tcikey(int type);

int main( int ac, char **av)
{
	mem_obj_t *source; // source
	mem_obj_t *target; // target
	/* touch timer */
	double passed_time;

	/* ums common config */
	ums_common_conf_t *cm_conf = NULL;

	/* my_config */
	virtual_gw_conf_t	*my_conf=NULL;
	char *errmsg=NULL;

	/* logging */
	fq_logger_t *l=NULL;
	char log_pathfile[256];

	/* common */
	int rc;
	bool tf;

	/* dequeue object */
	fqueue_obj_t *enq_obj=NULL;

	qformat_t *q=NULL;


	if( ac != 2 ) {
		printf("Usage: $ %s [your config file] <enter>\n", av[0]);
		return 0;
	}

	int dst_buf_len=4096;
	char c;
	char dst[4096];
	open_mem_obj( l, &source , dst_buf_len);
    open_mem_obj( l, &target , dst_buf_len);
	fqueue_obj_t *fq_obj = NULL;

	source->mseek( source, 0);
	source->mcopy( source, "AAA", strlen("AAA"));
	source->mprint(source);
	target->mputc( target, ' ');
	c = source->mgetc( source );
	target->mgets(target, dst);

	close_mem_obj(l, &source);
    close_mem_obj(l, &target);


	if(Load_ums_common_conf(&cm_conf, &errmsg) == false) {
		printf("Load_ums_common_conf() error. reason='%s'\n", errmsg);
		return(-1);
	}	

	if(LoadConf(av[1], &my_conf, &errmsg) == false) {
		printf("LoadMyConf('%s') error. reason='%s'\n", av[1], errmsg);
		return(-1);
	}

	sprintf(log_pathfile, "%s/%s", cm_conf->ums_common_log_path, my_conf->log_filename);
	rc = fq_open_file_logger(&l, log_pathfile, my_conf->i_log_level);
	CHECK(rc==TRUE);
	printf("log file: '%s'\n", log_pathfile);

	rc =  OpenFileQueue(l, NULL, NULL, NULL, NULL, my_conf->qpath, my_conf->qname, &fq_obj);
    CHECK(rc==TRUE);


	int file_len;
	unsigned char *json_template_string=NULL;
	
	printf("json_template_pathfile: %s\n",  my_conf->json_template_pathfile);
	rc = LoadFileToBuffer( my_conf->json_template_pathfile, &json_template_string, &file_len,  &errmsg);
	CHECK(rc > 0);

	printf("JSON Templete: '%s'\n", json_template_string);

	pid_t	mypid = getpid();

	fprintf(stdout, "This program will be logging to '%s'. pid='%d'.\n", log_pathfile, mypid);
	fq_log(l, FQ_LOG_EMERG, "Program(%s) start.\n", av[0]);

	start_timer();
	int test_count = my_conf->test_count;

	cache_t *cache_short_for_gssi;
	cache_short_for_gssi = cache_new('S', "Short term cache");

	for(int i=0; i<test_count; i++) {
#if 1
		char date[9], time[7];
		get_time(date, time);
		char send_time[16];
		char dist_time[16];

		sprintf(send_time, "%s%s", date, time);
		cache_update(cache_short_for_gssi, "senddate", send_time, 0);

		sprintf(dist_time, "%s%s", date, time);
		cache_update(cache_short_for_gssi, "DistDate", dist_time, 0);

		char tempbuf[12];
		char rand_telno_11[12];

		sprintf(rand_telno_11, "%s", get_seed_ratio_rand_str( tempbuf, 12, "013579"));
		cache_update(cache_short_for_gssi, "rand_telno_11", rand_telno_11, 0);


		char history_key[7];
		get_seed_ratio_rand_str( history_key, 7, "12345");
		cache_update(cache_short_for_gssi, "history_key", history_key, 0);
		

		char  *var_data = "Gwisang|Kookmin|57|gwisang.choi@gmail.com|M";
		char	dst[8192];
		rc = gssi_proc( l, json_template_string, var_data, "", cache_short_for_gssi, '|', dst, sizeof(dst));
		CHECK(rc==0);

		printf("gssi_proc() result. [%s], rc=[%d] len=[%ld]\n", dst, rc, strlen(dst));
#endif

#if 1
		// printf("Send JSON = [%s]\n", json_template_string);
		// printf("Send JSON = [%s]\n", dst);
		rc = uchar_enQ(l, dst, strlen(dst), fq_obj);
		if( rc < 0 ) {
			break;
		}
		else if( rc == 0 ) {
			printf("Queue is full.\n");
			sleep(1);
			continue;
		}
		else {
			printf("enQ success!! rc=[%d]\n", rc);
		}
#endif
		usleep(my_conf->usleep);

	} /* while end */


	if(q) free_qformat(&q);

	// close_ratio_distributor(l, &obj);

	return 0;
}

static int uchar_enQ(fq_logger_t *l, unsigned char *data, size_t data_len, fqueue_obj_t *obj)
{
	int rc;
	long l_seq=0L;
    long run_time=0L;

	while(1) {
		rc =  obj->on_en(l, obj, EN_NORMAL_MODE, (unsigned char *)data, data_len+1, data_len, &l_seq, &run_time );
		// rc =  obj->on_en(l, obj, EN_CIRCULAR_MODE, (unsigned char *)data, data_len+1, data_len, &l_seq, &run_time );
		if( rc == EQ_FULL_RETURN_VALUE )  {
			fq_log(l, FQ_LOG_EMERG, "Queue('%s', '%s') is full.\n", obj->path, obj->qname);
			usleep(100000);
			continue;
		}
		else if( rc < 0 ) { 
			fq_log(l, FQ_LOG_ERROR, "Queue('%s', '%s'): on_en() error.\n", obj->path, obj->qname);
			return(rc);
		}
		else {
			fq_log(l, FQ_LOG_INFO, "enqueued ums_msg len(rc) = [%d].", rc);
			break;
		}
	}
	return(rc);
}

static void fill_qformat_local(qformat_t *w, char* postdata, fqlist_t *t, char *bytestream)
{
	register int i;

	assert(w);

	for (i=0; i < w->count; i++) {
		if( w->s[i]->fill == FILL_POST ) {
			printf("POST.\n"); 
			char *buf=NULL;
				
			buf = calloc(MAX_FIELD_SIZE+1, sizeof(char));

			if ( postdata && get_item(postdata, w->s[i]->name, buf, strlen(postdata)+1) == 0 ) {
				 SAFE_FREE( w->s[i]->value ); // free default value
				 w->s[i]->value = strdup(buf);
			}
			else {
				printf("POST value not found.\n"); 
			}

			SAFE_FREE(buf);
		}
		else if( t && (w->s[i]->fill == FILL_CACHE) ) {
			printf("CACHE.\n"); 
			char *p=NULL;

			if( (p=fqlist_getval_unlocked(t, w->s[i]->name)) != NULL ) {
				SAFE_FREE ( w->s[i]->value ); // free default value
				w->s[i]->value = STRDUP(p);
			}	
		}
		else if( bytestream && w->s[i]->fill == FILL_STREAM ) {
			printf("STREAM.\n"); 
			char *p=NULL;

			SAFE_FREE( w->s[i]->value ); // free default value

			p = calloc(w->s[i]->len+1, sizeof(char));
			memset(p, 0x00, w->s[i]->len+1);
			memcpy(p, &bytestream[w->s[i]->msg_index], w->s[i]->msg_len);
			w->s[i]->value = STRDUP(p);
			SAFE_FREE(p);
		}

#ifdef _USE_UUID
		else if( w->s[i]->fill == FILL_UUID ) {
			printf("UUID.\n"); 
			char buf[37];

			get_uuid(buf, sizeof(buf));

			SAFE_FREE( w->s[i]->value );

			w->s[i]->value = strdup(buf);

		}
#endif
		else if( w->s[i]->fill == FILL_DATE) {
			printf("DATE.\n"); 
			char date[9];

			get_time(date, NULL);
			SAFE_FREE( w->s[i]->value ); 

			w->s[i]->value = strdup(date);
		}
		else if( w->s[i]->fill == FILL_HOSTID ) {
			printf("HOSTID.\n"); 
			char	hostid[36];

			get_hostid( hostid );
			SAFE_FREE( w->s[i]->value );

			w->s[i]->value = strdup(hostid);
		}
		else if(  w->s[i]->fill == FILL_FIXED ) {
			printf("FIXED.\n"); 
			printf("FIXED: nothing to do.\n"); 
		}

		if( w->s[i]->conv ) {
			if ( format_conversion_local(&w->s[i]->value, w->s[i]->name, w->s[i]->len, w->s[i]->conv) < 0 ) {
				printf("format_conversion(%s) error!\n", w->s[i]->conv);
			}
		}
	}
}
static int format_conversion_local( char** str, char* fn, int len, char* codes)
{
	char *dst, *buf, *org;
	int i, n, rc;
	int codeval[MAX_MULTI_CONVERSION];

	n = parse_codeseq_local(codes, codeval);

	if( n == 0 || codeval[0] == 0 ) {
		return(0);
	}

	dst = calloc( MAX_FIELD_SIZE, sizeof(char)); assert(dst);
	buf = calloc( MAX_FIELD_SIZE, sizeof(char)); assert(buf);
	org = strdup(*str);

	strcpy(dst, VALUE(*str));

	for (i=0; i < n; i++) {
		/* ignore conversion code zero */
		if ( codeval[i] == 0 ) continue;

		if ( codeval[i] == STOP_CONV ) break;

		if ( codeval[i] == STOP_CONV_IF_BLANK ) {
			if ( str_isblank(*str) )		
				break;
			else
				continue;
		}

		rc = do_convert_local(codeval[i], *str, dst, len, fn);
		if( rc == 0 ) {
			SAFE_FREE(*str);
			*str = STRDUP(dst);
		}
		else {
			printf("do_convert(%s, %d) error!\n", *str, codeval[i]);
		}

	}

	SAFE_FREE(*str);
	*str = STRDUP(dst);

	SAFE_FREE(org);
	SAFE_FREE(dst);
	SAFE_FREE(buf);

	return(0);

}
static int parse_codeseq_local(char* codes, int *codeval)
{
	char *p = (char*)codes, *q, buf[80];
	int i, j;

	for (i=0; i < MAX_MULTI_CONVERSION; i++) {
		if ( !p )
			break;
		if ( (q=strchr(p, '+')) ) {
			strncpy(buf, p, q-p);
			buf[q-p	] = '\0';
			p = q+1;
		}
		else {
			strcpy(buf, p);
			p = NULL;
		}
		codeval[i] = atoi(buf);
	}
	return(i);
}
static int do_convert_local( int code, char *src, char *dst, int len, char *fn)
{
	char* p = src;
	char* q = dst;
	int rc;

	assert(src);
	assert(dst);

	*q = '\0';

	if ( code >= CHECK_LENGTH_FROM && code <= CHECK_LENGTH_TO )
		return (check_length(code, p, q, len));

	switch ( code ) {
		case CHECK_FIELD_LENGTH:
			return(check_length(code, p, q, len));
		case CHECK_NUMERIC_ONLY:
		case CHECK_ALPHA_ONLY:
			return(check_chars(code, p, q, len));

		case FILL_SPACE:
		case FILL_SPACE_LEFT:
		case FILL_SPACE_RIGHT:
		case FILL_ZERO_LEFT:
			return(fill_chars(code, p, q, len));

		case DEL_SPACE:
			return(del_chars(code, p, q, len));

		case CHG_PLUS_TO_SPACE:
			return(change_chars(code, p, q, len));

		case ADD_HAN_WON:
		case ADD_HAN_DOLLOR:
		case ADD_CHAR_DOLLOR:
		case ADD_CHAR_PERCENT: 
			return(add_string(code, p, q, len));

		case INS_SLASH_TO_DATE:
		case INS_HYPHEN_TO_DATE:
			return(ins_chars_to_date(code, p, q, len));

		case TO_UPPER: return(to_upper(code, p, q, len));
		case TO_LOWER: return(to_lower(code, p, q, len));
		case TO_NUMBER: return(to_number(code, p, q, len));

		case FIT_LEFT:
			return(fit_chars(code, p, q, len));

		default:
			strcpy(dst,p);
			return(-CODE_UNDEFINED);
	}
}	
static int check_length(int code, char* src, char* dst, int len)
{
	assert(src);
	assert(dst);

	strcpy(dst, src);
	if ( code >= CHECK_LENGTH_FROM && code <= CHECK_LENGTH_TO ) {
		if ( strlen(src) == code ) 
			return(0);
		return (-code);
	}
	else if ( code == CHECK_FIELD_LENGTH ) {
		if ( strlen(src) == len ) 
			return(0);
		return (-code);
	}
	return(-code);
}

/*
** character type checking
*/
static int check_chars(int code, char* src, char* dst, int len)
{
	char *p = src;
	register int i;


	assert(src);
	assert(dst);

	strcpy(dst, src);
	switch ( code ) {
		case CHECK_NUMERIC_ONLY:
			while ( *p ) {
				if (!isdigit(*p)) 
					return (-code);
				p++;
			}
			return(0);

		case CHECK_ALPHA_ONLY:
			while ( *p ) {
				if ( !isalpha(*p) )
					return(-code);
				p++;
			}
			return(0);
		default:
			return(-code);
	}
}
/*
** Fill the destination with given characters
*/
static int fill_chars(int code, char* src, char* dst, int len)
{
	char *p = src;
	char *q = dst;
	char ch;
	register int i, k;

	assert(src);
	assert(dst);

	switch ( code ) {
		
	case FILL_SPACE: 
		for (i=0; i < len; i++, q++)
			*q = ' ';
		*q = '\0';
		return(0);
	
	case FILL_SPACE_LEFT: 
		if ( (k = strlen(src)) > len )
			return(-code);
		for (i=0; i < len-k; i++, q++)
			*q = ' ';
		for (; i < len; i++, p++, q++)
			*q = *p;
		*q = '\0';
		return(0);
	
	case FILL_SPACE_RIGHT: 
		if ( (k = strlen(src)) > len )
			return(-code);
		for (i=0; i < k; i++, p++, q++)
			*q = *p;
		for (; i < len; i++, q++)
			*q = ' ';
		*q = '\0';
		return(0);

	case FILL_ZERO_LEFT: 
		if ( (k = strlen(src)) > len )
			return(-code);
		for (i=0; i < len-k; i++, q++)
			*q = '0';
		for (; i < len; i++, p++, q++)
			*q = *p;
		*q = '\0';
		return(0);
	case DISPLAY_PWD: 
		for (i=0; i < len; i++, q++)
			*q = '*';
		*q = '\0';
		return(0);

	default:
		return(-code);

	}
}
/*
** Delete characters
*/
static int del_chars(int code, char* src, char* dst, int len)
{
	char *p = src;
	char *q = dst;
	char ch;
	register int i, j, k;

	assert(src); 
	assert(dst); 

	switch ( code ) { 
	case DEL_SPACE: 
	case DEL_COMMA: 
		switch ( code ) {
			case DEL_SPACE:  ch = ' '; break;
			case DEL_COMMA:  ch = ','; break;
		}
		k = strlen(p);
		for (i=0; i < k; i++, p++) {
			if ( *p == ch )
				continue;
			else {
				*q = *p; q++;
			}
		}
		*q = '\0';
		return(0);
		k = strlen(p);
		p += 2;
		for( i=0; i < (k-2); i++ ) {
			*q = *p;
			q++;
			p++;
		}
		*q = '\0';
		return(0);
	default:
		return(-code);
	}
}

/*
** Change characters
*/
static int change_chars(int code, char* src, char* dst, int len)
{
	char *p = src;
	char *q = dst;
	char ch, ch2;
	register int i, j, k;

	ASSERT(src);
	ASSERT(dst);

	switch ( code ) {
		case CHG_PLUS_TO_SPACE:  	ch = '+'; ch2 = ' '; break;
		case CHG_ZERO_TO_SPACE:    	ch = '0'; ch2 = ' '; break;
	}

	k = strlen(p);
	for (i=0; i < k; i++, p++, q++) {
		if ( *p == ch )
			*q = ch2;
		else {
			*q = *p;
		}
	}
	*q = '\0';
	return(0);
}

static int add_string(int code, char* src, char* dst, int len)
{
	char *p = src;
	char *q = dst;
	char add_str[10];

	assert(src);
	assert(dst);

	*dst = '\0';
	switch ( code ) {
	case ADD_HAN_WON:	strcpy(add_str, "¿ø"); break;
	case ADD_HAN_DOLLOR:	strcpy(add_str, "´Þ·¯"); break;
	case ADD_CHAR_DOLLOR:	strcpy(add_str, "£¤"); break;
	case ADD_CHAR_PERCENT:  strcpy(add_str, "%"); break;
#if 0
	case ADD_HAN_IL:	strcpy(add_str, "ÀÏ"); break;
	case ADD_HAN_GUN:	strcpy(add_str, "°Ç"); break;
	case ADD_HAN_HOI:	strcpy(add_str, "È¸"); break;
	case ADD_HAN_GOA:	strcpy(add_str, "ÁÂ"); break;
	case ADD_HAN_GYEWOL:	strcpy(add_str, "°³¿ù"); break;
	case ADD_HAN_NYEN:	strcpy(add_str, "³â"); break;
	case ADD_HAN_KANG:	strcpy(add_str, "°­"); break;
	case ADD_HAN_KA:	strcpy(add_str, "°¡"); break;
	case ADD_CHAR_YEN:	strcpy(add_str, "¡Í"); break;
	case ADD_CHAR_WON:	strcpy(add_str, "£Ü"); break;
	case ADD_HAN_MANWON:	strcpy(add_str, "¸¸¿ø"); break;
#endif
	}

	strcpy(dst, src);
	strcat(dst, add_str);
	return(0);
}

static int ins_chars_to_date(int code, char* src, char* dst, int len)
{
	char *p = src;
	char *q = dst;
	char ch;
	register int i;

	assert(src);
	assert(dst);

	switch ( code ) {
		case INS_SLASH_TO_DATE: 	ch = '/'; break;
		case INS_HYPHEN_TO_DATE: 	ch = '-'; break;
	}

	strcpy(dst, src);

	if ( len > 4 ) {
		for (i=0; i < len-4; i++, p++, q++) 
			*q = *p;
		*q = ch; q++;
	}

	for (i=0; i < 2; i++, p++, q++) 
		*q = *p;
	*q = ch; q++;
	for (i=0; i < 2; i++, p++, q++) 
		*q = *p;
	*q = '\0';

	return(0);
}

static int to_upper(int code, char* src, char* dst, int len)
{
	char* p = src;
	char* q = dst;
	register int i;

	assert(src);
	assert(dst);

	for (i=0; i < strlen(src); i++, p++, q++)
		*q = toupper(*p);
	*q = '\0';
	return (0);
}

static int to_lower(int code, char* src, char* dst, int len)
{
	char* p = src;
	char* q = dst;
	register int i;

	assert(src);
	assert(dst);

	for (i=0; i < strlen(src); i++, p++, q++)
		*q = tolower(*p);
	*q = '\0';
	return (0);
}

static int to_number(int code, char* src, char* dst, int len)
{
	long long ll;

	assert(src);
	assert(dst);

	ll = strtoll(src, NULL, 10);
	sprintf(dst, "%lld", ll);
	return(0);
}

/*
** fit characters
*/
static int fit_chars(int code, char* src, char* dst, int len)
{
	char *p = src;
	char *q = dst;

	assert(src);
	assert(dst);

	switch(code) {
	case FIT_LEFT:
		strcpy(q, p);
		q[len] = '\0';
		return(0);
		
	default:
		return(-code);
	}
}
