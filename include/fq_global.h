/*
 * fq_global.h
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
 * Description: Global definitions and include files that should exist     *
 *              anywhere                                                   *
 * Author:      Gal Shachor <shachor@il.ibm.com>                           *
 * Version:     $Revision: 1.1.1.1 $                                               *
 ***************************************************************************/

#define FQ_GLOBAL_H_VERSION "1.0.0"

#ifndef FQ_GLOBAL_H
#define FQ_GLOBAL_H

#if 0
#if defined(HAVE_CONFIG_H)
#include "fq_config.h"
#endif
#endif

#if defined(WIN32)

/* Ignore most warnings (back down to /W3) for poorly constructed headers
 */
#if defined(_MSC_VER) && _MSC_VER >= 1200
#pragma warning(push, 3)
#endif

/* disable or reduce the frequency of...
 *   C4057: indirection to slightly different base types
 *   C4075: slight indirection changes (unsigned short* vs short[])
 *   C4100: unreferenced formal parameter
 *   C4127: conditional expression is constant
 *   C4163: '_rotl64' : not available as an intrinsic function
 *   C4201: nonstandard extension nameless struct/unions
 *   C4244: int to char/short - precision loss
 *   C4514: unreferenced inline function removed
 */
#pragma warning(disable: 4100 4127 4163 4201 4514; once: 4057 4075 4244)

/* Ignore Microsoft's interpretation of secure development
 * and the POSIX string handling API
 */
#if defined(_MSC_VER) && _MSC_VER >= 1400
#ifndef _CRT_SECURE_NO_DEPRECATE
#define _CRT_SECURE_NO_DEPRECATE
#endif
#pragma warning(disable: 4996)
#endif /* defined(_MSC_VER) && _MSC_VER >= 1400 */
#endif /* defined(WIN32) */

#include "fq_version.h"

#ifdef HAVE_APR
#include "apr_lib.h"
#include "apr_strings.h"
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <errno.h>
#include <time.h>
#include <ctype.h>
#include <limits.h>

#ifdef _OSD_POSIX
#include "ap_config.h"
#endif

#ifdef AS400
#include "ap_config.h"
extern char *strdup(const char *str);
#endif

#include <sys/types.h>
#include <sys/stat.h>

#ifdef WIN32
#ifndef _WINDOWS_
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#ifndef _WIN32_WINNT

/* Restrict the server to a subset of Windows NT 4.0 header files by default
 */
#define _WIN32_WINNT 0x0500
#endif
#ifndef NOUSER
#define NOUSER
#endif
#ifndef NOMCX
#define NOMCX
#endif
#ifndef NOIME
#define NOIME
#endif
#include <windows.h>
/*
 * Add a _very_few_ declarations missing from the restricted set of headers
 * (If this list becomes extensive, re-enable the required headers above!)
 * winsock headers were excluded by WIN32_LEAN_AND_MEAN, so include them now
 */
#include <winsock2.h>
#include <mswsock.h>
#include <mstcpip.h>
#endif /* _WINDOWS_ */
#ifdef _WINDOWS_
#ifndef SIO_RCVALL
#include <mstcpip.h>
#endif
#endif
#include <sys/timeb.h>
#include <process.h>
#include <io.h>
#include <ws2tcpip.h>
#else /* WIN32 */
#include <unistd.h>
#if defined(NETWARE) && defined(__NOVELL_LIBC__)
#include "novsock2.h"
#define __sys_socket_h__
#define __netdb_h__
#define __netinet_in_h__
#define HAVE_VSNPRINTF
#define HAVE_SNPRINTF
#endif
#include <netdb.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <fcntl.h>
#ifndef NETWARE
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <sys/un.h>
#if !defined(_OSD_POSIX) && !defined(AS400) && !defined(__CYGWIN__) && !defined(HPUX11)
#include <sys/socketvar.h>
#endif
#if !defined(HPUX11) && !defined(AS400)
#include <sys/select.h>
#endif
#endif /* NETWARE */

#include <sys/time.h>
#include <sys/ioctl.h>
#endif /* WIN32 */

#ifdef __cplusplus
extern "C"
{
#endif                          /* __cplusplus */

#define FQ_WORKER_FILE_TAG      ("worker_file")
#define FQ_MOUNT_FILE_TAG       ("worker_mount_file")
#define FQ_LOG_LEVEL_TAG        ("log_level")
#define FQ_LOG_FILE_TAG         ("log_file")
#define FQ_SHM_FILE_TAG         ("shm_file")
#define FQ_WORKER_NAME_TAG      ("worker")

#define FQ_WORKER_FILE_DEF  ("workers.properties")

/* Urimap reload check time. Use 60 seconds by default.
 */
#define FQ_URIMAP_DEF_RELOAD    (60)

#define FQ_TRUE  (1)
#define FQ_FALSE (0)

#define FQ_UNSET (-1)

#define FQ_LF (10)
#define FQ_CR (13)

#define FQ_SESSION_IDENTIFIER "JSESSIONID"
#define FQ_PATH_SESSION_IDENTIFIER ";jsessionid"

#if defined(WIN32) || defined(NETWARE)
#ifdef __GNUC__
#define FQ_METHOD
#define C_LEVEL_TRY_START
#define C_LEVEL_TRY_END
#define C_LEVEL_FINALLY_START
#define C_LEVEL_FINALLY_END
#else
#define FQ_METHOD __stdcall
#define C_LEVEL_TRY_START       __try {
#define C_LEVEL_TRY_END         }
#define C_LEVEL_FINALLY_START   __finally {
#define C_LEVEL_FINALLY_END     }
#endif
#define PATH_SEPERATOR          (';')
#define FILE_SEPERATOR          ('\\')
#define PATH_ENV_VARIABLE       ("PATH")

    /* incompatible names... */
#ifndef strcasecmp
#define strcasecmp stricmp
#endif
#else /* defined(WIN32) || defined(NETWARE) */
#define FQ_METHOD
#define C_LEVEL_TRY_START
#define C_LEVEL_TRY_END
#define C_LEVEL_FINALLY_START
#define C_LEVEL_FINALLY_END
#define PATH_SEPERATOR          (':')
#define FILE_SEPERATOR          ('/')
#define PATH_ENV_VARIABLE       ("LD_LIBRARY_PATH")
#endif /* defined(WIN32) || defined(NETWARE) */

/* HTTP Error codes
 */

#define FQ_HTTP_OK                200
#define FQ_HTTP_BAD_REQUEST       400
#define FQ_HTTP_REQUEST_TOO_LARGE 413
#define FQ_HTTP_SERVER_ERROR      500
#define FQ_HTTP_NOT_IMPLEMENTED   501
#define FQ_HTTP_BAD_GATEWAY       502
#define FQ_HTTP_SERVER_BUSY       503
#define FQ_HTTP_GATEWAY_TIME_OUT  504


/*
 * RECO STATUS
 */

#define RECO_NONE   0x00
#define RECO_INITED 0x01
#define RECO_FILLED 0x02

/*
 * JK options
 */

#define FQ_OPT_FWDURIMASK           0x0007

#define FQ_OPT_FWDURICOMPAT         0x0001
#define FQ_OPT_FWDURICOMPATUNPARSED 0x0002
#define FQ_OPT_FWDURIESCAPED        0x0003
#define FQ_OPT_FWDURIPROXY          0x0004

#define FQ_OPT_FWDURIDEFAULT        FQ_OPT_FWDURIPROXY

#define FQ_OPT_FWDDIRS              0x0008
/* Forward local instead remote address */
#define FQ_OPT_FWDLOCAL             0x0010
#define FQ_OPT_FLUSHPACKETS         0x0020
#define FQ_OPT_FLUSHEADER           0x0040
#define FQ_OPT_DISABLEREUSE         0x0080
#define FQ_OPT_FWDCERTCHAIN         0x0100
#define FQ_OPT_FWDKEYSIZE           0x0200
#define FQ_OPT_REJECTUNSAFE         0x0400

#define FQ_OPT_DEFAULT              (FQ_OPT_FWDURIDEFAULT | FQ_OPT_FWDKEYSIZE)

/* Check for EBCDIC systems */

/* Check for Apache 2.0 running on an EBCDIC system */
#if APR_CHARSET_EBCDIC

#include <util_ebcdic.h>

#define USE_CHARSET_EBCDIC
#define fq_xlate_to_ascii(b, l) ap_xlate_proto_to_ascii(b, l)
#define fq_xlate_from_ascii(b, l) ap_xlate_proto_from_ascii(b, l)

#else                           /* APR_CHARSET_EBCDIC */

/* Check for Apache 1.3 running on an EBCDIC system */
#ifdef CHARSET_EBCDIC

#define USE_CHARSET_EBCDIC
#define fq_xlate_to_ascii(b, l) ebcdic2ascii(b, b, l)
#define fq_xlate_from_ascii(b, l) ascii2ebcdic(b, b, l)

#else                           /* CHARSET_EBCDIC */

/* We're in on an ASCII system */

#define fq_xlate_to_ascii(b, l) /* NOOP */
#define fq_xlate_from_ascii(b, l)       /* NOOP */

#endif                          /* CHARSET_EBCDIC */

#endif                          /* APR_CHARSET_EBCDIC */

/* on i5/OS V5R4 HTTP/APR APIs and Datas are in UTF */
#if defined(AS400_UTF8)
#undef USE_CHARSET_EBCDIC
#define fq_xlate_to_ascii(b, l) /* NOOP */
#define fq_xlate_from_ascii(b, l)       /* NOOP */
#endif

/* fq_uint32_t defines a four byte word */
/* fq_uint64_t defines a eight byte word */
#if defined (WIN32)
    typedef unsigned int fq_uint32_t;
#define FQ_UINT32_T_FMT "u"
#define FQ_UINT32_T_HEX_FMT "x"
    typedef unsigned __int64 fq_uint64_t;
#define FQ_UINT64_T_FMT "I64u"
#define FQ_UINT64_T_HEX_FMT "I64x"
#define FQ_PID_T_FMT "d"
#define FQ_PTHREAD_T_FMT "d"
#elif defined(AS400) || defined(NETWARE)
    typedef unsigned int fq_uint32_t;
#define FQ_UINT32_T_FMT "u"
#define FQ_UINT32_T_HEX_FMT "x"
    typedef unsigned long long fq_uint64_t;
#define FQ_UINT64_T_FMT "llu"
#define FQ_UINT64_T_HEX_FMT "llx"
#define FQ_PID_T_FMT "d"
#define FQ_PTHREAD_T_FMT "d"
#else
#include "fq_types.h"
#endif

#define FQ_UINT32_T_FMT_LEN  (sizeof(FQ_UINT32_T_FMT) - 1)
#define FQ_UINT32_T_HEX_FMT_LEN  (sizeof(FQ_UINT32_T_HEX_FMT) - 1)
#define FQ_UINT64_T_FMT_LEN  (sizeof(FQ_UINT64_T_FMT) - 1)
#define FQ_UINT64_T_HEX_FMT_LEN  (sizeof(FQ_UINT64_T_HEX_FMT) - 1)

#ifdef WIN32
/* For WIN32, emulate gettimeofday() using _ftime() */
#define gettimeofday(tv, tz) { struct _timeb tb; _ftime(&tb); \
                               (tv)->tv_sec = (long)tb.time;  \
                               (tv)->tv_usec = tb.millitm * 1000; }
#define HAVE_VSNPRINTF
#define HAVE_SNPRINTF

#ifdef HAVE_APR

/*
#define snprintf apr_snprintf
#define vsnprintf apr_vsnprintf
*/

#else
/* define snprint to match windows version */
/*
#define snprintf _snprintf
#define vsnprintf _vsnprintf
*/
#endif
#endif /* WIN32" */

/* Use apr snprintf() and vsnprintf() when needed */
#if defined(HAVE_APR)
#if !defined(HAVE_SNPRINTF)
/*
#define snprintf apr_snprintf
*/
#endif
#if !defined(HAVE_VSNPRINTF)
/*
#define vsnprintf apr_vsnprintf
*/
#endif
#endif

/* Use ap snprintf() and vsnprintf() when needed */
#if !defined(HAVE_APR)
#if !defined(HAVE_SNPRINTF)
/*
#define snprintf ap_snprintf
*/
#endif
#if !defined(HAVE_VSNPRINTF)
/*
#define vsnprintf ap_vsnprintf
*/
#endif
#endif

#if defined(WIN32) || (defined(NETWARE) && defined(__NOVELL_LIBC__))
typedef SOCKET fq_sock_t;
#define IS_VALID_SOCKET(s) ((s) != INVALID_SOCKET)
#define FQ_INVALID_SOCKET  INVALID_SOCKET
#else
typedef int fq_sock_t;
#define IS_VALID_SOCKET(s) ((s) > 0)
#define FQ_INVALID_SOCKET  (-1)
#endif

#ifdef NETWARE
#ifdef __NOVELL_LIBC__
#define MAX_PATH 511
#else
#define MAX_PATH 255
#endif
#endif

#ifdef AS400_UTF8
#define strcasecmp(a,b) apr_strnatcasecmp(a,b)
#endif

#ifdef __cplusplus
}
#endif                          /* __cplusplus */

#endif                          /* FQ_GLOBAL_H */

