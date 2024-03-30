/*
** fq_gssi.h
*/
#ifndef _FQ_GSSI_H
#define _FQ_GSSI_H

#include "fq_logger.h"
#include "fq_cache.h"

#ifdef _cplusplus
extern "C" {
#endif

#define NUM_GSSI_TAG 15
#define MAX_TAG_SIZE 10
#define MAX_VALUE_SIZE 4096
#define MAX_ARG_SIZE 4096

typedef struct _gssi_tag_type gssi_tag_type_t;

struct _gssi_tag_type {
	char *tag_name;
	int tag_type; // 0: var_data( delimiter ), 1: postdata( key=value & key1=value1, 2: cache variable with keyname )
};

#define NUM_GSSI_TAGS 62

#if 0
gssi_tag_type_t gssi_tags[NUM_GSSI_TAGS] = {
	// { "º¯¼ö0", 0},  /* 1 */
    { "var1", 0},  /* 1 */
    { "var2", 0},  /* 2 */
    { "var3", 0},  /* 4 */
    { "var4", 0},  /* 4 */
    { "var5", 0},  /* 5 */
    { "var6", 0},  /* 6 */
    { "var7", 0},  /* 7 */
    { "var8", 0},  /* 8 */
    { "var9", 0},  /* 9 */
    { "var10", 0},  /* 10 */
    { "var11", 0},  /* 11 */
    { "var12", 0},  /* 12 */
    { "var13", 0},  /* 13 */
    { "var14", 0},  /* 14 */
    { "var15", 0},  /* 15 */
    { "var16", 0},  /* 16 */
    { "var17", 0},  /* 17 */
    { "var18", 0},  /* 18 */
    { "var19", 0},  /* 19 */
    { "var20", 0},  /* 20 */
    { "var21", 0},  /* 21 */
    { "var22", 0},  /* 22 */
    { "var23", 0},  /* 23 */
    { "var24", 0},  /* 24 */
    { "var25", 0},  /* 25 */
    { "var26", 0},  /* 26 */
    { "var27", 0},  /* 27 */
    { "var28", 0},  /* 28 */
    { "var29", 0},  /* 29 */
    { "var30", 0},  /* 30 */
    { "var31", 0},  /* 31 */
    { "var32", 0},  /* 32 */
    { "var33", 0},  /* 33 */
    { "var34", 0},  /* 34 */
    { "var35", 0},  /* 35 */
    { "var36", 0},  /* 36 */
    { "var37", 0},  /* 37 */
    { "var38", 0},  /* 38 */
    { "var39", 0},  /* 39 */
    { "var40", 0},  /* 40 */
    { "var41", 0},  /* 41 */
    { "var42", 0},  /* 42 */
    { "var43", 0},  /* 43 */
    { "var44", 0},  /* 44 */
    { "var45", 0},  /* 45 */
    { "var46", 0},  /* 46 */
    { "var47", 0},  /* 47 */
    { "var48", 0},  /* 48 */
    { "var49", 0},  /* 49 */
    { "var50", 0},  /* 50 */
	{ "name", 1},  /* 51 */ /* reserved word */
	{ "button", 1},  /* 52 */ /* reserved word */
	{ "address", 1},  /* 53 */ /* reserved word */
	{ "part", 1},  /* 54 */ /* reserved word */
	{ "date", 1},  /* 55 */ /* reserved word */
	{ "time", 1},  /* 56 */ /* reserved word */
	{ "system", 1},  /* 57 */ /* reserved word */
	{ "IPadress", 1},  /* 58 */ /* reserved word */
	{ "email", 1},  /* 59 */ /* reserved word */
	{ "CODE", 1},    /* 60 */ /* reserved word */
	{ "cache", 2},  /* 61 */
	{ "jsonarray", 3}
};
#endif

int gssi_proc( fq_logger_t *l, char *template, char *var_data, char *postdata, cache_t *short_cache, char delimiter,  char *dst, int dst_buf_len);
// int SSIP(fq_logger_t *l, load_template_fmt_db *tContent, char *sTempContents, char *varData, tmpl_msg_fmt *trgt_msg);

#ifdef _cplusplus
}
#endif
#endif
