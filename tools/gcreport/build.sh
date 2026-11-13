#!/usr/bin/env bash
# Збирає міні-мод gcreport у build/gcr.jar (Java 8 bytecode, без залежностей від Minecraft під час компіляції).
# JDK 8: JAVA8_HOME, або типовий шлях Adoptium на ПК власника.
set -euo pipefail
cd "$(dirname "$0")"
JH="${JAVA8_HOME:-/c/Program Files/Eclipse Adoptium/jdk-8.0.502.7-hotspot}"
rm -rf build
mkdir -p build/stub build/classes
"$JH/bin/javac" -encoding UTF-8 -source 8 -target 8 -nowarn -d build/stub stub/net/fabricmc/api/ClientModInitializer.java
"$JH/bin/javac" -encoding UTF-8 -source 8 -target 8 -nowarn -cp build/stub -d build/classes $(find src -name '*.java')
cp -r resources/. build/classes/
(cd build/classes && "$JH/bin/jar" cf ../gcr.jar .)
echo "OK: build/gcr.jar ($(wc -c < build/gcr.jar) байт)"
