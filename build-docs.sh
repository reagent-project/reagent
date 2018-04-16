#!/bin/bash

set -e

TAG=$CIRCLE_TAG

if [[ -z $TAG ]]; then
    echo "Set CIRCLE_TAG"
    exit 1
fi

lein codox

rm -fr tmp
git clone git@github.com:reagent-project/reagent-project.github.io.git tmp

mkdir -p "tmp/docs/$TAG/"
cp -r target/doc/* "tmp/docs/$TAG/"

cd tmp
git add .
git commit -m "Built docs from $TAG"
git push
rm -rf tmp
