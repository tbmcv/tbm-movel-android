#!/bin/sh

ZIPFILE="$1"
PROJECT_DIR=$(dirname "$0")
LIB_DIR="$PROJECT_DIR/libs"
TMP_DIR="$LIB_DIR/tmp-android-sdk"


if [ ! -r "$ZIPFILE" ]
then
    echo "Must specify the zip file to be imported"
    exit 1
fi

SDK_VERSION=$(echo "$ZIPFILE" | grep -Eo 'sdk-[0-9].*\.(zip|jar)$' | cut -d - -f 2-)
if [ -z "$SDK_VERSION" ]
then
    echo "Couldn't find SDK version from filename $ZIPFILE"
    exit 1
fi
SDK_VERSION=$(basename -s .jar "$SDK_VERSION")
SDK_VERSION=$(basename -s .zip "$SDK_VERSION")

if [ -e "$TMP_DIR" ]
then
    echo "Temporary directory $TMP_DIR already exists"
    exit 1
fi

mkdir "$TMP_DIR"
unzip "$ZIPFILE" -d "$TMP_DIR"
cd "$TMP_DIR"
mv libs/*linphone*.jar "linphone-core-$SDK_VERSION.jar"
mv libs lib
zip "linphone-android-sdk-$SDK_VERSION.jar" -r lib
cd -
mv ${TMP_DIR}/*.jar -t "$LIB_DIR"
rm -rf "$TMP_DIR"
