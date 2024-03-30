/*
** fq_md5.h
*/

#ifndef FQ_HD5_H
#define FQ_MD5_H

#define FQ_MD5_H_VERSION "1.0.0"


// leftrotate function definition
#define LEFTROTATE(x, c) (((x) << (c)) | ((x) >> (32 - (c))))

#ifdef __cplusplus
extern "C"
{
#endif

struct _fq_hash {
	uint32_t h0;
	uint32_t h1;
	uint32_t h2;
	uint32_t h3;
};
typedef struct _fq_hash fq_hash_t;

void md5(uint8_t *initial_msg, size_t initial_len, fq_hash_t *h, char dst[]);

#ifdef __cplusplus
}
#endif

#endif
