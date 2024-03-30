/*
 * fq_logger.h
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/***************************************************************************
 * Description: Logger object definitions                                  *
 * Author:      Gal Shachor <shachor@il.ibm.com>                           *
 * Version:     $Revision: 1.4 $                                           *
 ***************************************************************************/

#define FQ_LOGGER_H_VERSION "1.0.0"

#ifndef FQ_LOGGER_H
#define FQ_LOGGER_H

#include "fq_global.h"


#define FQ_TIME_MAX_SIZE (64)

typedef struct fq_logger fq_logger_t;
struct fq_logger
{
    void *logger_private;
    int level;
    const char *log_fmt;                   /* the configured timestamp format for logging */
    char log_fmt_subsec[FQ_TIME_MAX_SIZE]; /* like log_fmt, but milli/micro seconds marker
                                              replaced, because strftime() doesn't handle those */
    int    log_fmt_type;                   /* do we want milli or microseconds */
    size_t log_fmt_offset;                 /* at which position should we insert */
    size_t log_fmt_size;                   /* how long is this format string */

    int (FQ_METHOD * log) (fq_logger_t *l, int level, int used, char *what);
};

typedef struct fq_file_logger_t fq_file_logger_t;
struct fq_file_logger_t
{
    FILE *logfile;
    /* For Apache 2 APR piped logging */
    void *jklogfp;
    /* For Apache 1.3 piped logging */
    int log_fd;
};

typedef struct _fqlog fqlog_t;
struct _fqlog {
	int	 log_level;
	char *progname;
	char *logpath;
};

/* Level like Java tracing, but available only
   at compile time on DEBUG preproc define.
 */
#define FQ_LOG_TRACE_LEVEL   0
#define FQ_LOG_DEBUG_LEVEL   1
#define FQ_LOG_INFO_LEVEL    2
#define FQ_LOG_WARNING_LEVEL 3
#define FQ_LOG_ERROR_LEVEL   4
#define FQ_LOG_EMERG_LEVEL   5
#define FQ_LOG_REQUEST_LEVEL 6
#define FQ_LOG_DEF_LEVEL     FQ_LOG_INFO_LEVEL

#define FQ_LOG_TRACE_VERB   "trace"
#define FQ_LOG_DEBUG_VERB   "debug"
#define FQ_LOG_INFO_VERB    "info"
#define FQ_LOG_WARN_VERB    "warn"
#define FQ_LOG_ERROR_VERB   "error"
#define FQ_LOG_EMERG_VERB   "emerg"
#define FQ_LOG_DEF_VERB     FQ_LOG_INFO_VERB

#if defined(__GNUC__) || (defined(_MSC_VER) && (_MSC_VER > 1200))
#define FQ_LOG_TRACE   __FILE__,__LINE__,__FUNCTION__,FQ_LOG_TRACE_LEVEL
#define FQ_LOG_DEBUG   __FILE__,__LINE__,__FUNCTION__,FQ_LOG_DEBUG_LEVEL
#define FQ_LOG_ERROR   __FILE__,__LINE__,__FUNCTION__,FQ_LOG_ERROR_LEVEL
#define FQ_LOG_EMERG   __FILE__,__LINE__,__FUNCTION__,FQ_LOG_EMERG_LEVEL
#define FQ_LOG_INFO    __FILE__,__LINE__,__FUNCTION__,FQ_LOG_INFO_LEVEL
#define FQ_LOG_WARNING __FILE__,__LINE__,__FUNCTION__,FQ_LOG_WARNING_LEVEL
#else
#define FQ_LOG_TRACE   __FILE__,__LINE__,NULL,FQ_LOG_TRACE_LEVEL
#define FQ_LOG_DEBUG   __FILE__,__LINE__,NULL,FQ_LOG_DEBUG_LEVEL
#define FQ_LOG_ERROR   __FILE__,__LINE__,NULL,FQ_LOG_ERROR_LEVEL
#define FQ_LOG_EMERG   __FILE__,__LINE__,NULL,FQ_LOG_EMERG_LEVEL
#define FQ_LOG_INFO    __FILE__,__LINE__,NULL,FQ_LOG_INFO_LEVEL
#define FQ_LOG_WARNING __FILE__,__LINE__,NULL,FQ_LOG_WARNING_LEVEL
#endif

#define FQ_LOG_REQUEST __FILE__,0,NULL,FQ_LOG_REQUEST_LEVEL

#if defined(FQ_PRODUCTION)
/* TODO: all DEBUG messages should be compiled out
 * when this define is in place.
 */
#define FQ_IS_PRODUCTION    1
#define FQ_TRACE_ENTER(l)
#define FQ_TRACE_EXIT(l)
#else
#define FQ_IS_PRODUCTION    0
#define FQ_TRACE_ENTER(l)                               \
    do {                                                \
        if ((l) && (l)->level == FQ_LOG_TRACE_LEVEL) {  \
            int tmp_errno = errno;                      \
            fq_log((l), FQ_LOG_TRACE, "enter");         \
            errno = tmp_errno;                          \
    } } while (0)

#define FQ_TRACE_EXIT(l)                                \
    do {                                                \
        if ((l) && (l)->level == FQ_LOG_TRACE_LEVEL) {  \
            int tmp_errno = errno;                      \
            fq_log((l), FQ_LOG_TRACE, "exit");          \
            errno = tmp_errno;                          \
    } } while (0)

#endif  /* FQ_PRODUCTION */

#define FQ_LOG_NULL_PARAMS(l) fq_log((l), FQ_LOG_ERROR, "NULL parameters")

/* Debug level macro
 * It is more efficient to check the level prior
 * calling function that will not execute anyhow because of level
 */
#define FQ_IS_DEBUG_LEVEL(l)  ((l) && (l)->level <  FQ_LOG_INFO_LEVEL)
#define FQ_IS_ERROR_LEVEL(l)  ((l) && (l)->level ==  FQ_LOG_ERROR_LEVEL)

#ifdef __cplusplus
extern "C"
{
#endif

int fq_open_file_logger(fq_logger_t **l, const char *file, int level);
int fq_log(fq_logger_t *l,
           const char *file, int line, const char *funcname, int level,
           const char *fmt, ...);
int fq_close_file_logger(fq_logger_t **l);
void fq_syslog(char *fmt, ...);
int get_log_level(const char *level);
char *get_log_level_str( int level, char *dst );
int gen_tmp_logname( fq_logger_t *l, char *Prefix, char *logname, size_t bufsize);
void backup_log( char *log_pathfile, char *log_open_date);



#ifdef __cplusplus
}
#endif      /* __cplusplus */
#endif      /* FQ_LOGGER_H */
