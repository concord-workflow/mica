#!/bin/bash

BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

MAIN_CLASS="ca.ibodrov.mica.standalone.Main"

if [[ -z "${MICA_JAVA_OPTS}" ]]; then
    MICA_JAVA_OPTS="-Xms2g -Xmx2g"
fi
echo "MICA_JAVA_OPTS: ${MICA_JAVA_OPTS}"

if [[ -z "${MICA_TMP_DIR}" ]]; then
    export MICA_TMP_DIR="/tmp"
fi

echo "Using $(which java)"
java -version

JAVA_VERSION=$(java -version 2>&1 \
  | head -1 \
  | cut -d'"' -f2 \
  | sed 's/^1\.//' \
  | cut -d'.' -f1)

if [[ -z "${EXTRA_CLASSPATH}" ]]; then
  EXTRA_CLASSPATH=""
fi

echo "EXTRA_CLASSPATH: ${EXTRA_CLASSPATH}"

exec java \
${MICA_JAVA_OPTS} \
${JDK_SPECIFIC_OPTS} \
-Dfile.encoding=UTF-8 \
-Djava.net.preferIPv4Stack=true \
-Djava.security.egd=file:/dev/./urandom \
-cp "${BASE_DIR}/lib/*:${BASE_DIR}/ext/*:${BASE_DIR}/classes:${EXTRA_CLASSPATH}" \
"${MAIN_CLASS}"
