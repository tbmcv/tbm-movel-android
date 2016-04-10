#!/bin/sh

. "$(dirname "$0")/gen_drawables.sh"

FILENAME="$1"
NAME=$(basename "$FILENAME" .svg)

create_icon "$FILENAME" ldpi 36
create_icon "$FILENAME" mdpi 48
create_icon "$FILENAME" hdpi 72
create_icon "$FILENAME" xhdpi 96
create_icon "$FILENAME" xxhdpi 144
create_icon "$FILENAME" xxxhdpi 192
create_png "$FILENAME" "$(dirname "$FILENAME")/${NAME}-web.png" 512
