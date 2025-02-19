/* @(#) $Id: p_libedi.h,v 1.1.1.1 2016/11/22 17:39:46 cvs Exp $ */

/*
 * Copyright (c) 2003, 2004, 2005, 2006, 2007, 2008 Mo McRoberts.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The names of the author(s) of this software may not be used to endorse
 *    or promote products derived from this software without specific prior
 *    written permission.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY 
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL
 * AUTHORS OF THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef P_LIBEDI_H_
# define P_LIBEDI_H_                   1

# include <stdio.h>
# include <stdlib.h>
# include <string.h>

# define LIBEDI_EXPORTS                1
# ifdef PIC
#  define LIBEDI_SHARED                1
# endif

# include "libedi.h"

struct edi_parser_struct
{
	int error; /* Error status */
	int sep_seg; /* Segment separator */
	int sep_data; /* Data element separator */
	int sep_sub; /* Sub-element separator */
	int sep_tag; /* Tag delimiter */
	int escape; /* Escape (release) character */
};

struct edi_interchange_private_struct
{
	char **stringpool;
	char **sp;
	size_t *poolsize;
	size_t npools;
};

# define STRINGPOOL_BLOCKSIZE          512
# define SEG_BLOCKSIZE                 8
# define ELEMENT_BLOCKSIZE             8

ssize_t edi__stringpool_get(edi_interchange_t *msg, size_t minsize);
char *edi__stringpool_alloc(edi_interchange_t *msg, size_t length);
int edi__stringpool_free(edi_interchange_t *msg, char *p);

#endif /* !P_LIBEDI_H_ */
