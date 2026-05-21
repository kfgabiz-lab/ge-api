#!/bin/bash
# BO API 빌드 스크립트 (Java 25)
# 사용법: ./build.sh

JAVA_HOME="C:/Program Files/Java/jdk-25.0.2"
export JAVA_HOME

echo ">>> Gradle 데몬 종료"
JAVA_HOME="$JAVA_HOME" ./gradlew --stop

echo ">>> Java 25 클린 빌드 시작"
JAVA_HOME="$JAVA_HOME" ./gradlew clean bootJar --rerun-tasks

if [ $? -eq 0 ]; then
    echo ">>> 빌드 성공"
else
    echo ">>> 빌드 실패"
    exit 1
fi
