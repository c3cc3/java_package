/*
** fq_json.h
*/

#ifndef FQ_JSON_H
#define FQ_JSON_H

#define FQ_JSON_H_VERSION "1.0.0"

#define _CRT_SECURE_NO_WARNINGS    // fopen 보안 경고로 인한 컴파일 에러 방지
#include <stdio.h>     // 파일 처리 함수가 선언된 헤더 파일
#include <stdlib.h>    // malloc, free 함수가 선언된 헤더 파일
#include <stdbool.h>   // bool, true, false가 정의된 헤더 파일
#include <string.h>    // strchr, memset, memcpy 함수가 선언된 헤더 파일

#include "fq_logger.h"

#ifdef __cplusplus
extern "C"
{
#endif


#define TOKEN_COUNT 50    // 토큰의 최대 개수

// 토큰 종류 열거형
typedef enum _TOKEN_TYPE {
    TOKEN_STRING,    // 문자열 토큰
    TOKEN_NUMBER,    // 숫자 토큰
} TOKEN_TYPE;

// 토큰 구조체
typedef struct _TOKEN {
    TOKEN_TYPE type;   // 토큰 종류
    union {            // 두 종류 중 한 종류만 저장할 것이므로 공용체로 만듦
        char *string;     // 문자열 포인터
        double number;    // 실수형 숫자
    };
    bool isArray;      // 현재 토큰이 배열인지 표시
} TOKEN;


// JSON 구조체
typedef struct _JSON {
    TOKEN tokens[TOKEN_COUNT]; // 토큰 배열
} JSON;

/* function prototypes */
char *readFile(fq_logger_t *l, char *filename, int *readSize);    // 파일을 읽어서 내용을 반환하는 함수
void parseJSON(fq_logger_t *l, char *doc, int size, JSON *json); // JSON 파싱 함수
void freeJSON(fq_logger_t *l, JSON *json);    // JSON 해제 함수
char *getString( fq_logger_t *l, JSON *json, char *key);    // 키에 해당하는 문자열을 가져오는 함수
char *getArrayString( fq_logger_t *l, JSON *json, char *key, int index); // 키에 해당하는 배열 중 인덱스를 지정하여 문자열을 가져오는 함수
int getArrayCount( fq_logger_t *l, JSON *json, char *key);     // 키에 해당하는 배열의 요소 개수를 구하는 함수
double getNumber( fq_logger_t *l, JSON *json, char *key);    // 키에 해당하는 숫자를 가져오는 함수

#ifdef __cplusplus
}
#endif

#endif
