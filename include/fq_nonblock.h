#ifndef _FQ_NONBLOCK_H
#define _FQ_NONBLOCK_H

#define FQ_NONBLOCK_H_VERSION "1.0.0" /* if this source is released, version starts from 1.0.0 */

#ifdef __cplusplus
extern "C" {
#endif

int fq_nonblock(int  sockfd,    /* operate on this */
                   int nonblock   /* TRUE or FALSE */);

#ifdef __cplusplus
}
#endif

#endif
