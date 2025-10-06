#!/bin/bash

echo "<html><head><title>Reagent docs index</title></head><body><ul>" > tmp/docs/index.html
for path in tmp/docs/*; do
    if [[ -d $path ]]; then
        v=$(basename "$path")
        echo "<li><a href=\"$v\">$v</a></li>" >> tmp/docs/index.html
    fi
done
echo "</ul></body>" >> tmp/docs/index.html
