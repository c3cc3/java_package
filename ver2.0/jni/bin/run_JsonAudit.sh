#!/bin/bash

# Agent 설치 기준 디렉터리
APP_HOME="/ums24/wiseu/fq/project_src/jni"

# 설정파일
CONFIG_FILE="${APP_HOME}/conf/Agent.properties"

# JNI 라이브러리
JNI_LIB="${APP_HOME}/lib/libfqueue_jni.so"

# Java 실행 환경
JAVA="${JAVA_HOME:+${JAVA_HOME}/bin/}java"

# Java 클래스 및 외부 라이브러리 경로
CLASSPATH="${APP_HOME}:${APP_HOME}/java:${APP_HOME}/lib/*"

# 설정파일 확인
if [ ! -r "$CONFIG_FILE" ]; then
    echo "ERROR: Cannot read config file: $CONFIG_FILE" >&2
    exit 1
fi

# JNI 라이브러리 확인
if [ ! -r "$JNI_LIB" ]; then
    echo "ERROR: Cannot read JNI library: $JNI_LIB" >&2
    exit 1
fi

# Agent 실행
exec "$JAVA" \
    -Djava.library.path="$APP_HOME/lib" \
    -cp "$CLASSPATH" \
    JsonAudit ./../map/sms.map ./../out/sms.jsonl

