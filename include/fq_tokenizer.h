/*
** fq_tokenizer.h
*/
#include "fq_logger.h"

#define FQ_TOKENIZER_H_VERSION "1,0,0"

#ifndef _FQ_TOKENIZER_H
#define _FQ_TOKENIZER_H

#define TOK_TOKEN_O 0x01
#define TOK_TOKEN_X 0x10

typedef struct tokened_st{
  int type;
  char *sta;
  char *end;
  struct tokened_st *next;
} tokened;


#define MAX_FILEDS 200
typedef struct _fq_token fq_token_t;
struct _fq_token {
	char	*src;
	char 	*pos[MAX_FILEDS+1];
	int		n_delimiters;
	char	delimiter;
};


#ifdef __cplusplus
extern "C" {
#endif
/*
** prototypes
*/
int tokenizer(const char *,const char *,tokened **);
int tokenizer_free(tokened *tokened_head);

int Tokenize(fq_logger_t *l, char *src, char delimiter, fq_token_t *t);
int GetToken(fq_logger_t *l, fq_token_t *t, int position, char **dst);

#ifdef __cplusplus
}
#endif

#endif
