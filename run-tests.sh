#!/bin/bash

# Kill all subshells with ctrl-c
trap "kill 0" SIGINT

reset='\033[0m'
red='\033[0;31m'
green='\033[0;32m'
blue='\033[0;34m'

EXIT=0

SUMMARY="$blue##\n## SUMMARY\n##$reset\n\n"

for env in test-environments/*; do
    name=$(basename "$env")
    (
    cd "$env"
    echo -e "$blue##"
    echo -e "## TESTING $name"
    echo -e "##$reset"
    echo
    ./test.sh
    )
    if [[ $? != "0" ]]; then
        echo
        echo -e "${red}FAIL $name$reset"
        SUMMARY="$SUMMARY${red}FAIL $name$reset\n"
        EXIT=1
    else
        SUMMARY="$SUMMARY${green}OK   $name$reset\n"
    fi
    echo
    echo
done

echo -e "$SUMMARY"

echo

for env in test-environments/*-prod; do
    name=$(basename "$env")
    path="test-environments/$name/target/cljsbuild/prod-test/main.js"
    if [[ -f "$path" ]]; then
        echo "$name	$(./node_modules/.bin/gzip-size "$path")"
    fi
done

echo
echo "NOTE: These sizes include Reagent test suite which also uses React-dom/server, so this doesn't demonstrate real use case."

exit $EXIT
