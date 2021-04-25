#!/bin/bash

set -e

TAG=$CIRCLE_TAG

if [[ -z $TAG ]]; then
    echo "Set CIRCLE_TAG"
    exit 1
fi

lein codox

rm -fr tmp
if [[ -n $GITHUB_ACTOR ]]; then
    git config --global user.email "14146879+github-actions[bot]@users.noreply.github.com"
    git config --global user.name "github-actions[bot]"
    git clone "https://${GITHUB_ACTOR}:${SITE_TOKEN}@github.com/reagent-project/reagent-project.github.io.git" tmp
else
    git clone git@github.com:reagent-project/reagent-project.github.io.git tmp
fi

mkdir -p "tmp/docs/$TAG/"
cp -r target/doc/* "tmp/docs/$TAG/"

cd tmp
git add .
git commit -m "Built docs from $TAG"
git push
rm -rf tmp
