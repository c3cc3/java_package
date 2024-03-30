/*
** fq_base64_std.h
*/
#include <sys/types.h>
#include <ctype.h>
#include <resolv.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
int b64_encode(const unsigned char *src, size_t srclength, char *target, size_t targsize);
int b64_decode(const char *src, unsigned char *target, size_t targsize); 
