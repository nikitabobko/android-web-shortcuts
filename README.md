# Web Shortcuts (for Android)

Simple Android app for creating Web Shortcuts on homescreen

## Installation

Download the APK file directly from [GitHub releases](https://github.com/nikitabobko/android-web-shortcuts/releases)
(there are no automatic updates, but the app is simple enough that you won't need updates anyway)

## Building

```shell
echo 'sdk.dir=/Users/bobko/Library/Android/sdk' >> local.properties

# Debug
./gradlew assembleDebug # ./build/outputs/apk/debug/Web-Shortcuts-debug.apk

# Release
keytool -genkey -v -keystore ./release.jks -keyalg RSA -validity 36500 -storepass ******
echo 'storePassword=******' >> ./local.properties
./build-release.sh # ./web-shortcuts-v*.apk
```
