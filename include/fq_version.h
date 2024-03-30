/*
 * fq_verion.h
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

#define FQ_VERSION_H_VERSION "1.0,0"

/***************************************************************************
 * Description: JK version header file                                     *
 * Version:     $Revision: 1.1.1.1 $                                           *
 ***************************************************************************/

#ifndef __FQ_VERSION_H
#define __FQ_VERSION_H

/************** START OF AREA TO MODIFY BEFORE RELEASING *************/
#define FQ_VERMAJOR     9
#define FQ_VERMINOR     0
#define FQ_VERFIX       0

/* set FQ_VERISRELEASE to 1 when release (do not forget to commit!) */
#define FQ_VERISRELEASE 1
/* Beta number */
#define FQ_VERBETA      0
#define FQ_BETASTRING   "0"
/* Release candidate */
#define FQ_VERRC        0
#define FQ_RCSTRING     "0"
/* Source Control Revision as a suffix, e.g. "-r12345" */
#define FQ_REVISION " (1026297)"

/************** END OF AREA TO MODIFY BEFORE RELEASING *************/
#if 0
#if !defined(PACKAGE)
#if defined(FQ_ISAPI)
#define PACKAGE "isapi_redirector"
#define FQ_DLL_SUFFIX "dll"
#elif defined(FQ_NSAPI)
#define PACKAGE "nsapi_redirector"
#define FQ_DLL_SUFFIX "dll"
#else
#define PACKAGE "mod_jk"
#define FQ_DLL_SUFFIX "so"
#endif
#endif

/* Build FQ_EXPOSED_VERSION and FQ_VERSION */
#define FQ_EXPOSED_VERSION_INT PACKAGE "/" FQ_VERSTRING

#if (FQ_VERBETA != 0)
#define FQ_EXPOSED_VERSION FQ_EXPOSED_VERSION_INT "-beta-" FQ_BETASTRING
#else
#undef FQ_VERBETA
#define FQ_VERBETA 255
#if (FQ_VERRC != 0)
#define FQ_EXPOSED_VERSION FQ_EXPOSED_VERSION_INT "-rc-" FQ_RCSTRING
#elif (FQ_VERISRELEASE == 1)
#define FQ_EXPOSED_VERSION FQ_EXPOSED_VERSION_INT
#else
#define FQ_EXPOSED_VERSION FQ_EXPOSED_VERSION_INT "-dev" FQ_REVISION
#endif
#endif
#define FQ_FULL_EXPOSED_VERSION FQ_EXPOSED_VERSION FQ_REVISION

#define FQ_MAKEVERSION(major, minor, fix, beta) \
            (((major) << 24) + ((minor) << 16) + ((fix) << 8) + (beta))

#define FQ_VERSION FQ_MAKEVERSION(FQ_VERMAJOR, FQ_VERMINOR, FQ_VERFIX, FQ_VERBETA)
#endif

/** Properly quote a value as a string in the C preprocessor */
#define FQ_STRINGIFY(n) FQ_STRINGIFY_HELPER(n)
/** Helper macro for FQ_STRINGIFY */
#define FQ_STRINGIFY_HELPER(n) #n
#define FQ_VERSTRING \
            FQ_STRINGIFY(FQ_VERMAJOR) "." \
            FQ_STRINGIFY(FQ_VERMINOR) "." \
            FQ_STRINGIFY(FQ_VERFIX)

/* macro for Win32 .rc files using numeric csv representation */
#define FQ_VERSIONCSV FQ_VERMAJOR ##, \
                    ##FQ_VERMINOR ##, \
                    ##FQ_VERFIX


#endif /* __FQ_VERSION_H */

