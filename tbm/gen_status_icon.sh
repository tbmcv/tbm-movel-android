#!/bin/sh

. "$(dirname "$0")/gen_drawables.sh"

FILENAME="$1"
NAME=$(basename "$FILENAME" .svg)

create_icon "$FILENAME" ldpi 18
create_icon "$FILENAME" mdpi 24
create_icon "$FILENAME" hdpi 36
create_icon "$FILENAME" xhdpi 48
create_icon "$FILENAME" xxhdpi 72
create_icon "$FILENAME" xxxhdpi 96
