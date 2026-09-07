#!/bin/bash
# 与 CI spotless 8.10.1 默认 google-java-format 版本一致（1.28.0）
set -e
DIR=.
JAR=/gjf-1.28.jar
if [ ! -f  ]; then
  curl -sL -o  https://github.com/google/google-java-format/releases/download/v1.28.0/google-java-format-1.28.0-all-deps.jar
fi
echo 
