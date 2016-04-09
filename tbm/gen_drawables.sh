#!/bin/sh

FILENAME="$1"
NAME=$(basename "$FILENAME" .svg)
SVG_FILE="${NAME}.svg"
PNG_FILE="${NAME}.png"

create_png () {
    OUTFILE=$1
    DP=$2
    rsvg-convert -w "$DP" -h "$DP" "$SVG_FILE" > "$OUTFILE"
    optipng -quiet -o2 "$OUTFILE"
}

create_icon () {
    DENSITY="$1"
    DP="$2"
    DIR="$(dirname $FILENAME)/src/main/res/drawable-$DENSITY"
    mkdir -p "$DIR"
    create_png "$DIR/$PNG_FILE" "$2"
}

create_icon ldpi 36
create_icon mdpi 48
create_icon hdpi 72
create_icon xhdpi 96
create_icon xxhdpi 144
create_icon xxxhdpi 192
create_png "${NAME}-web.png" 512
