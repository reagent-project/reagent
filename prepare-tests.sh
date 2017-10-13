#!/bin/bash

npm install

echo

# Symlinked node_modules, package.json and package-lock.json
# are used to share node_modules between environments that
# use the same packages.

for env in test-environments/*; do
    name=$(basename "$env")
    (
    cd "$env"
    if [[ ! -L node_modules ]]; then
        echo "Install $name packages"
        npm install
    else
        echo "$name uses $(readlink node_modules)"
    fi
    )
    echo
done
