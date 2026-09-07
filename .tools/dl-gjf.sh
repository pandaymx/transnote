#!/bin/bash
# 与 CI spotless 8.10.1 配置的 google-java-format 1.30.0 保持一致（JVM 25 要求 >= 1.30.0）
set -e
cd "$(dirname "$0")"
JAR="gjf-1.30.jar"
if [ ! -f "$JAR" ]; then
  curl -sL -o "$JAR" https://repo1.maven.org/maven2/com/google/googlejavaformat/google-java-format/1.30.0/google-java-format-1.30.0-all-deps.jar
fi
echo "$JAR ready"
