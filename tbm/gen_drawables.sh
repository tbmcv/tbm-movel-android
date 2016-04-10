#!/bin/sh

create_png () {
    FILENAME="$1"
    OUTFILE="$2"
    DP="$3"
    rsvg-convert -w "$DP" -h "$DP" "$FILENAME" > "$OUTFILE"
    optipng -quiet -o2 "$OUTFILE"
}

create_icon () {
    FILENAME="$1"
    DENSITY="$2"
    DP="$3"
    DIR="$(dirname "$FILENAME")/src/main/res/drawable-$DENSITY"
    mkdir -p "$DIR"
    create_png "$FILENAME" "$DIR/${NAME}.png" "$DP"
}
