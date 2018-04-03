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
    echo -e "$blue##"
    echo -e "## TESTING $name"
    echo -e "##$reset"
    echo
    $env/test.sh
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

exit $EXIT
