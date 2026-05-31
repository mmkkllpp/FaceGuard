#!/bin/sh
# Gradle wrapper script
APP_HOME=$( cd "${0%[/\\]*}" > /dev/null && pwd -P ) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if ! java -version >/dev/null 2>&1; then echo "Java not found"; exit 1; fi
exec java -Dorg.gradle.appname=FaceGuard -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
