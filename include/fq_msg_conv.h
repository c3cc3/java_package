/*
** fq_msg_conv.h
*/
#ifndef _FQ_MSG_CONV_H
#define _FQ_MSG_CONV_H

#include <stdio.h>
#include "fq_codemap.h"
#include "fq_file_list.h"
#include "fq_tokenizer.h"
#include "fq_logger.h"
#include "fq_common.h"

#ifdef __cplusplus
extern "C"
{
#endif

#define MAX_CONV_MSG_LEN (8192)

#define CHECK_LENGTH_FROM           1
#define CHECK_LENGTH_TO             128
#define CHECK_FIELD_LENGTH          130
#define CHECK_NUMERIC_ONLY          131
#define CHECK_ALPHA_ONLY            132

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



int do_msg_conv(fq_logger_t *l, char *src_msg, char delimiter, char *src_codemap_file, char *dst_list_file, char **dst_msg);

#ifdef __cplusplus
}
#endif

#endif
