/*
 * fq_types.h
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

#define FQ_TYPES_H_VERSION "1,0,0"

/***************************************************************************
 * Description: Platform specific, auto-detected types.                    *
 * Author:      Rainer Jung <rjung@apache.org>                             *
 * Version:     $Revision: 1.1.1.1 $                                           *
 ***************************************************************************/

#ifndef FQ_TYPES_H
#define FQ_TYPES_H

/* GENERATED FILE WARNING!  DO NOT EDIT fq_types.h
 *
 * You must modify fq_types.h.in instead.
 *
 */

#ifdef __cplusplus
extern "C"
{
#endif                          /* __cplusplus */

/* fq_uint32_t defines a four byte word */
typedef unsigned int fq_uint32_t;

/* And FQ_UINT32_T_FMT */
#define FQ_UINT32_T_FMT "u"

/* And FQ_UINT32_T_HEX_FMT */
#define FQ_UINT32_T_HEX_FMT "x"

/* fq_uint64_t defines a eight byte word */
typedef unsigned long fq_uint64_t;

/* And FQ_UINT64_T_FMT */
#define FQ_UINT64_T_FMT "lu"

/* And FQ_UINT64_T_HEX_FMT */
#define FQ_UINT64_T_HEX_FMT "lx"

/* And FQ_PID_T_FMT */
#define FQ_PID_T_FMT "d"

/* fq_pthread_t defines a eight byte word */
typedef unsigned long fq_pthread_t;

/* And FQ_PTHREAD_T_FMT */
#define FQ_PTHREAD_T_FMT "lu"

#ifdef __cplusplus
}
#endif                          /* __cplusplus */

#endif                          /* FQ_TYPES_H */
