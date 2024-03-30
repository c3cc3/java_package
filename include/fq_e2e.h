#ifndef _FQ_E2E_H
#define _FQ_E2E_H

#define FQ_E2E_H_VERSION "1.0.0"

#ifdef __cplusplus
extern "C" {
#endif

int e2e_decrypt(unsigned char *src, int src_len, char **dst, int *dst_len);

#ifdef __cplusplus
}
#endif

#endif
