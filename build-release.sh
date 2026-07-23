#!/usr/bin/env bash
set -e # Exit if one of commands exit with non-zero exit code
set -u # Treat unset variables and parameters other than the special parameters ‘@’ or ‘*’ as an error
set -o pipefail # Any command failed in the pipe fails the whole pipe
cd "$(dirname "$0")"
# set -x # Print shell commands as they are executed (or you can try -v which is less verbose)

./gradlew assembleRelease
cp ./build/outputs/apk/release/Web-Shortcuts-release.apk \
    "web-shortcuts-v$(grep versionName build.gradle.kts | grep --only-matching -E "\d+(\.\d+)*").apk"
