#ifndef CIPHER_H_INCLUDED
#define CIPHER_H_INCLUDED

#ifdef __cplusplus
extern "C" {
#endif

#define CIPHER_ENCRYPT 1
#define CIPHER_DECRYPT 0

typedef struct {
	int sizeOfBlock;
	void (*init)(void *, const unsigned char *, const unsigned char *, int, int);
	int (*update)(void *, unsigned char *, const unsigned char *, unsigned int);
	void (*restart)(void *);
} CIPHER_ALGORITHM;

typedef struct {
	const CIPHER_ALGORITHM *cipher;
	void *cipher_ctx;
	int encrypt;
	unsigned char buffer[16];
	unsigned int bufferLen;
} CIPHER_CTX;

typedef struct {
	int sizeOfDigest;
	int sizeOfBlock;
	void (*init)(void *);
	int (*update)(void *, const unsigned char *, unsigned int);
	void (*final)(unsigned char *, void *);
} DIGEST_ALGORITHM;

void CipherInit(CIPHER_CTX *context, const CIPHER_ALGORITHM *cipher, void *ctx, const unsigned char *iv, const unsigned char *key, int nkey, int encrypt);
int CipherUpdate(CIPHER_CTX *context, unsigned char *partOut, const unsigned char *partIn, unsigned int partInLen);
int CipherFinal(CIPHER_CTX *context, unsigned char *partOut);

#ifdef __cplusplus
}
#endif

#endif
