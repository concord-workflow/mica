#!/usr/bin/env bash

set -e

JDK_21_AMD64="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz"
JDK_21_ARM64="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.8_9.tar.gz"

case "${JDK_VERSION}-${TARGETARCH}" in
  "21-amd64")
    echo ${JDK_21_AMD64}
    ;;
  "21-arm64")
    echo ${JDK_21_ARM64}
    ;;
  *)
    >&2 echo "Unsupported JDK ${JDK_VERSION}-${TARGETARCH}"
    exit 1;
    ;;
esac
