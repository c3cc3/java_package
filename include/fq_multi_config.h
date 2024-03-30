#ifndef __SZ_DEFINE_H
#define __SZ_DEFINE_H

/* Copyright (C) KANG SHIN-SUK (symlink@hanmail.net)
 *     2002/11/27			Version 1.0
 */
#include "fq_typedef.h"
#include <stdarg.h>

#define MAX_GROUPSIZ		80	
#define CONFIG_BUFSIZ		16384

typedef struct SZ_toValue {
	unsigned char *pDefine;
	unsigned char *pValue;					/* 값: NULL일 경우 삭제로 처리 */
} SZ_toValue_t;

typedef struct {
	int           numUser;			/* 등록된 항목의 수 */
	int           numWait;			/* 정렬되지 않는 항목의 수 */
	SZ_toValue_t *pUser;
	unsigned char        *pGroup;
	struct {
		int   numOfs;				/* 저장된 데이터 수 */

		/* toBuffer: 설정된 내용을 저장한다.
		 *    +---------- ~~~ -----------+
		 *    |                          |
		 *    +---------- ~~~ -----------+
		 *  numOfs                     numUser
		 *
		 *  toBuffer                                   	설정 내용 저장
		 *  (toBuffer + BUFSIZ) - sizeof(SZ_toValue_t) 	정렬 배열
		 */
		unsigned char toBuffer[CONFIG_BUFSIZ];
		unsigned char toGroup[MAX_GROUPSIZ];
	} SP;
} SZ_toDefine_t;

#define SZ_toAt( __pSet, __numAt)			&((__pSet)->pUser[__numAt])
#define SZ_maxUser( __pSet)				 	(__pSet)->numUser

#define SZ_toInit( __pSet)							\
	(__pSet)->numUser =								\
		(__pSet)->SP.numOfs = 0,					\
		(__pSet)->pGroup = (__pSet)->SP.toGroup,	\
		(__pSet)->pUser = (SZ_toValue_t *)((__pSet)->SP.toBuffer + CONFIG_BUFSIZ)
		
#define SZ_toExit( __pSet)
#define SZ_toStart( __pSet)					\
	(__pSet)->numUser =						\
		(__pSet)->SP.numOfs = 0,			\
		(__pSet)->pUser = (SZ_toValue_t *)((__pSet)->SP.toBuffer + CONFIG_BUFSIZ)


/* 
 * SZ_toDefine: 설정한다.
 */
EXTRN int    SZ_toDefine( SZ_toDefine_t *, unsigned char *, unsigned char *pValue, ...);

/* SZ_toCommit: 설정을 정렬한다.
 *   - 주) 만약, 검색전에 값을 설정 (SZ_toDefine 함수) 하였다면,
 *         반드시 정렬을 해 주어야 한다.
 */
EXTRN int    SZ_toCommit( SZ_toDefine_t *);


/* SZ_toGroup: 기본 그룹을 설정한다.
 *   - pGroup은 모든 처리에 영향을 미치게 된다.
 */
EXTRN int    SZ_toGroup( SZ_toDefine_t *pSet, unsigned char *pGroup, ...);

/* SZ_toValue: 항목을 검색한다. 
 *     이때, 동일 항목명을 갖는 항목이 있다면, 해당 항목의 처음 위치로 
 *     이동을 하게 된다.
 *
 *     ** 주) 만약, 정렬되지 않은 항목이 존재한다면 검색을 정렬된 항목을
 *            기준으로 먼저 검색한 후 값이 존재한다면, 검색 영역의 동일
 *            항목의 처음위치를 반환하며, 정렬되지 않는 항목은 더 이상
 *            검색을 하지 않는다. 이때, 정렬 영역에서 값을 발견하지 못
 *            하였다면, 비 정렬영역을 검색하여 동일 항목의 처음 위치를
 *            반환한다.
 *
 *            이는, 동일 항목이 정렬되지 않는 항목에 있을 경우 찾지 못할수
 *            있는 가능성이 있다. (SZ_toCommit() 후에는 정상적인 결과 반환)
 *
 *     그룹으로 검색을 할 경우, pDefine의 마지막에 그룹임을 표시하는 '.'을
 *     포함하여 검색을 하면된다. (EX. "Group.")
 */
EXTRN SZ_toValue_t *SZ_toValue( SZ_toDefine_t *, const unsigned char *pDefine);

#define SZ_toFirst						SZ_toValue

/* SZ_toNext: pCur의 항목명과 일치하는 다음 항목을 얻는다.
 *   - 주) 검색 항목은 최종 검색에 사용된 항목을 사용한다.
 *         그러므로, SZ_toNext() 함수를 호출할 경우는, 최종 검색 항목을
 *         계속 유지하여야 한다. (검색을 하게 되면, 최종 검색 항목이 변경된다)
 *
 * SZ_toValue_t *pCur;
 *
 * if ((pCur = SZ_toFirst( &toDefine, "Item")) != NULL)
 *    do {
 *       ...
 *    } while ((pCur = SZ_toNext( &toDefine, pCur)) != NULL);
 */
EXTRN SZ_toValue_t *SZ_toNext( SZ_toDefine_t *, SZ_toValue_t *pCur);


/* SZ_toCompact: 버퍼를 정리한다.
 *   - 제거된 버퍼의 내용을 없애고, 정렬된 순서대로 버퍼에 내용을 다시
 *     기록한다.
 */
EXTRN int           SZ_toCompact( SZ_toDefine_t *pSet);


/* SZ_fromBuffer: 버퍼로 부터, 설정 내용을 읽어 들인다.(한 항목의 설정)
 *    pDelim	항목과 값을 구분하는 구문자 목록
 *
 * SZ_fromStream: 파일로 부터, 설정 내용을 읽어 들인다.(다중 항목의 설정)
 *    처리 규칙:
 *       1) 설명문				";", "#'
 *       2) 항목 구분자			":", "=", " ", "\t"
 *       	이때, 처음에 항목 구분자가 있을 경우 전 항목에 값을 추가한다.
 *
 *       3) 그룹의 시작			"{"
 *       	반드시, 항목에 대한 설정값 마지막에 존재하여야 한다.
 *
 *       	예) Group=1 {
 *
 *       4) 그룹의 마침			"}"
 *       	반드시, 항목명 위치에 존재 하여야 한다.
 *
 *       5) 값의 설정
 *       	- 묶여진 형태의 값 설정
 *       	  값의 시작과 끝이 "'", "\"", "`"로 동일하게 끝나는 설정 형태
 *
 *       	  묶여진 형태의 값에서는 설명문을 나타내는 글자는 무시되며,
 *       	  마지막 또는 처음에 존재하는 공백 문자등은 허용된다.
 *
 *       	  예) " 값의 설정"		=> " 값의 설정"
 *       	      '값의 설정 '		=> "값의 설정 "
 *       	      ` 값의 설정 `		=> " 값의 설정 "
 *
 *			- 일반적인 형태의 값 설정
 *			  이때는 설명문을 표시하는 글자 이전의 설정 값만을 취하게 되며,
 *			  마지막에 존재하는 공백(" ", "\t")문자는 제거 된다.
 * 		
 * 			- 전 항목에 연속되는 값의 설정
 * 			  항목명 시작 부분에 공백을 제외한 ":", "="으로 구분한 다음 값을
 * 			  설정한다.
 *
 * 			  예) : " 값의 설정"
 * 			  
 * Sample.conf
 * ------------------------------------------------------
 *   Group {
 *     Default "Default Value" {
 *     	  Value=10
 *   	  Test		CFG toConfig	# Test String
 *   	  Sample: test
 *   	  : " Sample 항목에 추가 됩니다"
 *     }
 *
 *     String Type;
 *   }
 *
 * ------------------------------------------------------
 *   해당 항목 접근
 *   	
 *             	  SZ_toGroup( NULL)     SZ_toGroup( "Group")
 *            	---------------------	------------------------
 * SZ_toValue 	"Group.Default.Value" 	 "Default.Value"
 *   	      	"Group.Default.Test" 	 "Default.Test"
 *
 *            	"Group.Type"         	 "Type"
 *            	"Group.Default"     	 "Default"
 *
 */
EXTRN int    SZ_fromBuffer( SZ_toDefine_t *, const char *pDelim, unsigned char *);
EXTRN int    SZ_fromStream( SZ_toDefine_t *, FILE *fpSet);
EXTRN int    SZ_fromFile( SZ_toDefine_t *, const char *filename);


/* pDefine의 항목을 갖는 설정값을 얻는다. 만약, 설정값이 없다면 pDefault를
 * 리턴으로 반환한다.
 */
EXTRN unsigned char         *SZ_toExist( SZ_toDefine_t *, const unsigned char *pDefine, unsigned char *pDefault);

#define SZ_toString( __pSet, __pDefine)				\
		SZ_toExist( __pSet, __pDefine, NULL)
#define SZ_toInteger( __pSet, __pDefine)			\
		atoi( (char *)SZ_toExist( __pSet, __pDefine, (unsigned char *)"0"))
#define SZ_toDouble( __pSet, __pDefine)			\
		atof( (char *)SZ_toExist( __pSet, __pDefine, (unsigned char *)"0.0"))
#define SZ_toFloat( __pSet, __pDefine)			\
		atof( (char *)SZ_toExist( __pSet, __pDefine, (unsigned char *)"0.0"))


/* SZ_toDelete: 항목을 삭제한다. 
 *   - 이는 SZ_toValue()로 해당 항목을 찾아, pValue = NULL을 설정하는 것과
 *     같은 효과를 갖는다.
 *
 *   반환: 제거된 항목의 수
 *
 * int SZ_toDelete( SZ_toDefine_t *pSet, const char *pDefine)
 * {
 *     SZ_toValue_t *pCur;
 *
 *     if ((pCur = SZ_toFirst( pSet, pDefine)) != NULL)
 *        do {
 *           pCur->pValue = NULL;
 *        } while ((pCur = SZ_toNext( pSet, pCur)) != NULL);
 *
 *     return 0;
 * }
 */
EXTRN int            SZ_toDelete( SZ_toDefine_t *, const unsigned char *);

EXTRN int            SZ_toSave( SZ_toDefine_t *pSet, int fpSet);
EXTRN int            SZ_toLoad( int fpSet, SZ_toDefine_t *pSet);


#if 0
typedef struct SZ_toSetup {
	char  *pDefine;							/* 항목명 */

	/* fromBuffer:
	 *    int		저장된 값의 순서
	 *    char *	저장된 값
	 *    void *	저장할 버퍼 (pValue)
	 */
	int  (*fromBuffer)( int, char *, void *);
	void  *pValue;							/* 값 저장 버퍼 */
	struct SZ_toSetup *pGroup;				/* 그룹 항목 리스트 */
} SZ_toSetup_t;

/* fromBuffer */
EXTRN int SZ_PTR   ( int, char *, void *);	/* 포인터을 얻는다. */

#if 0
EXTRN int SZ_STRING( int, char *, void *);	/* 문자열을 얻는다. */
#endif

EXTRN int SZ_INT8  ( int, char *, void *);	/* char 형 값으로 얻는다. */
EXTRN int SZ_INT16 ( int, char *, void *);	/* short형 값으로 얻는다. */
EXTRN int SZ_INT32 ( int, char *, void *);	/* int형 값으로 얻는다. */

EXTRN int SZ_FLOAT ( int, char *, void *);	/* double형으로 얻는다. */


EXTRN int           SZ_toSetup( SZ_toDefine_t *pSet, SZ_toSetup_t *pSetup);
#endif

#endif
