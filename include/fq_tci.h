/*
** fq_tci.h
*/
#define FQ_TCI_H_VERSION "1.1,0"

#ifndef _FQ_TCI_H
#define _FQ_TCI_H

#include "fq_cache.h"
#include "fq_conf.h"
#include "fq_codemap.h"
#include "fq_logger.h"
#include "parson.h"

#define BACKEND_ERR_CODE_INDEX 6
#define BACKEND_ERR_CODE_SIZE  4
#define BACKEND_ERR_CODE_NORMAL "0000"

#define BACKEND_AMSG_CODE_INDEX  2
#define BACKEND_AMSG_CODE_SIZE   8

typedef struct _tcidef_t {
   int type;
   char* key;
} tcidef_t;

#define SAVE		0
#define FILL		1
#define TYPE		2
#define DISP		3
#define BLOCK		4

/* data acquisition method */
#define FILL_FIXED	5
#define FILL_REUSE	6
#define FILL_POST	7
#define FILL_CACHE	8
#define FILL_STREAM	9
#define FILL_UUID	10
#define FILL_DATE	11
#define FILL_HOSTID	12

#define TYPE_STR	13
#define TYPE_PID	14
#define TYPE_CODESET	15
#define TYPE_CLI_OP	16
#define TYPE_INT	17
#define TYPE_HEXA	18
#define TYPE_NO		19
#define TYPE_YES	20
#define OUTPUT          21
#define OUTPUT_P        22
#define OUTPUT_TABLE    23
#define OUTPUT_BLOCK    24
#define OUTPUT_UNION    25

#define SAVE_N		'N'	
#define SAVE_L		'L'
#define SAVE_S		'S'
#define SAVE_LA		'l'
#define SAVE_SA		's'

#define FORMAT_DIR	"format"

#if 0
#define MAX_FIELD_SIZE		2048 /* changed at 2012/08/10 in Shinhan bank. */
#else
#define MAX_FIELD_SIZE		4096
#endif

#define MAX_QFIELD_COUNT	128
#define MAX_AFIELD_COUNT	255
#define MAX_ABLOCK_COUNT	128

#if 0
#define MAX_QMSGSTREAM_LEN (4096*4)
#else
#define MAX_QMSGSTREAM_LEN 65536
#endif

/* conversion */

#define MAX_MULTI_CONVERSION     10

/*
** Standard Conversion Codes
*/
#define NO_CONV               		0

/* check operations */
#define CHECK_LENGTH_FROM         	1
#define CHECK_LENGTH_TO				128
#define CHECK_FIELD_LENGTH      	130
#define CHECK_NUMERIC_ONLY			131
#define CHECK_ALPHA_ONLY			132

#define STOP_CONV           		143
#define STOP_CONV_IF_BLANK      	144

/* fill operations */
#define FILL_SPACE          		200
#define FILL_SPACE_LEFT         	201
#define FILL_SPACE_RIGHT        	202
#define FILL_ZERO_LEFT         		203

/* delete operations */
#define DEL_SPACE          			300
#define DEL_COMMA          			301

/* change operations */
#define CHG_PLUS_TO_SPACE       	400
#define CHG_ZERO_TO_SPACE       	401

/* add operations */
#define	ADD_HAN_WON					500
#define ADD_HAN_DOLLOR				501
#define ADD_CHAR_DOLLOR				502
#define ADD_CHAR_PERCENT			503
#define DISPLAY_PWD					554

/* insert operations */
#define INS_SLASH_TO_DATE			600
#define INS_HYPHEN_TO_DATE			601

/* convert to operations */
#define TO_UPPER					700
#define TO_LOWER					701
#define TO_NUMBER					702

/* Fit(alignment) operation */
#define FIT_LEFT					800

#define CODE_UNDEFINED          	999


/*-------------------------------------------------------
**  R e q u e s t   M e s s a g e  F o r m a t
**  (Gateway -> Bank Host)
**-------------------------------------------------------
**/
/* 
** Query message frame record structure 
**/
typedef struct {
	char 	*name;		/* name of the record */
	int 	len;		/* length(bytes) of this field */
	int		mem_size;	/* memory size (for V1.0 compatibility) */
	int		fill;		/* data acquisition method */
	char*	deflt;		/* default value */
	int 	type;		/* data type during transmission */
	int 	save;		/* caching indicator */
	char*	conv;		/* conversion codes */
	int		msg_index;  /* index of bytestream */
	int		msg_len;	/* size of copying from index of stream */
	char*	value;		/* value */
} qrecord_t;

/* 
** Query message frame structure 
**/

typedef struct {
	char *name;		/* name of the format */
	int count;		/* number of records */
	int length;		/* length of the message */
	qrecord_t *s[MAX_QFIELD_COUNT];	
				/* array of pointers to records */
	char* data;		/* message data to send */
	int datalen;		/* length of data */
} qformat_t;


/*-------------------------------------------------------
**  R e s p o n s e   M e s s a g e  F o r m a t
**  (Bank Host -> Gateway)
**-------------------------------------------------------
**/
/* 
** Answer message frame record structure 
**/

typedef struct {
	char 	*name;		/* name of the record */
	int 	len;		/* length(bytes) of this field */
	int	offset;		/* starting point of the record in the block */
	int	mem_size;	/* memory size */
	int 	type;		/* data type during transmission */
	int 	save;		/* caching indicator */
	char	*conv;		/* conversion codes */
	int	dspl;		/* display option */
	char	*value;		/* value */
} arecord_t;

typedef struct {
	int	type;		/* type of the block */
	char	*name;		/* name of the block */
	int	len;		/* block size (=sum of field sizes) */
	int	offset;		/* starting point of the block in byte stream */
	int	n_rec;		/* number of fields in the block */
	int	n_max;		/* maximum number of list items */
	int	n_dat;		/* number of effective list items */
	char	*n_dat_var;	/* variable for effective list items */
	arecord_t *s[MAX_AFIELD_COUNT];
				/* array of pointers to records */
} ablock_t;

/* 
** Response message frame structure 
**/

#define MAX_POSTDATA_LEN 8192
typedef struct {
	char *name;		/* name of the format */
	int count;		/* number of blocks */
	int length;		/* length of the message */
	ablock_t *b[MAX_ABLOCK_COUNT];	
				/* array of pointers to blocks */
	char* data;		/* received message data */
	int datalen;		/* length of data */
	char *postdata; /* make a postdata in assoc_aformat() */
	JSON_Value *jv; /* pointer of JSON Root value in parson library */
	cache_t *cache_short;  /* make a cache in assoc_aformat() */
} aformat_t;

#ifdef __cplusplus
extern "C" {
#endif
/*-------------------------------------------------------
**  Prototypes
**-------------------------------------------------------
*/
qformat_t* new_qformat(char* name, char** ptr_errmesg);
aformat_t* new_aformat(char* name, char** ptr_errmesg);

void free_qformat(qformat_t **q);
void free_aformat(aformat_t **a);
void free_aformat_values(aformat_t **a);
void free_qformat_values(qformat_t **q);

int  read_qformat(qformat_t *w, char* filename, char** ptr_errmesg);
int  read_aformat(aformat_t *w, char* filename, char** ptr_errmesg);

void print_qformat(qformat_t* w);
void print_aformat(aformat_t* w);
void print_aformat_stdout(aformat_t *w);

void fill_qformat(qformat_t *w, char* postdata, fqlist_t *t, char *bytestream);
void fill_qformat_from_list(qformat_t *w, fqlist_t *t);

int assoc_qformat(qformat_t *w, char* delimiter);
int assoc_aformat(aformat_t *w, char** errmsg);

void save_aformat_to_cache(aformat_t *w);

int get_avalue(aformat_t *w, char *name, char *dst);
int find_qfield(qformat_t *w, char *name);
int find_afieldp(aformat_t *w, char *name, int* block_index);

aformat_t *parsing_stream_message( fq_logger_t *l, conf_obj_t *cfg, codemap_t *svc_code_map, const char *msg, int msglen);

#ifdef __cplusplus
}
#endif
#endif
