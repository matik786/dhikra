#!/bin/sh
#
# Gradle wrapper script. The Dhikra APK is normally built with
# ./build-manual.sh (aapt2 + kotlinc + d8, no Gradle needed); this wrapper
# exists so a fresh checkout can also build with Gradle elsewhere.

APP_BASE_NAME=${0##*/}
APP_HOME=$(cd "${0%/*}" >/dev/null 2>&1 && pwd -P)

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
