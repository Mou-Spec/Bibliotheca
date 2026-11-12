#!/bin/sh
# Maven Wrapper script (minimal)
set -e
MVNW_REPOURL="${MVNW_REPOURL:-https://repo.maven.apache.org/maven2}"
WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain
JAVA_EXE="${JAVA_HOME:+$JAVA_HOME/bin/}java"
exec "$JAVA_EXE" -classpath "$WRAPPER_JAR" "-Dmaven.multiModuleProjectDirectory=$(pwd)" $WRAPPER_LAUNCHER "$@"
