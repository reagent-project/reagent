#!/bin/bash

# Kill all subshells with ctrl-c
trap "kill 0" SIGINT

reset='\033[0m'
blue='\033[0;34m'
red='\033[0;31m'

EXIT=0

for env in test-environments/*; do
    FAIL=0
    name=$(basename "$env")
    (
    cd "$env"
    echo -e "$blue##"
    echo -e "## TESTING $name"
    echo -e "##$reset"
    echo
    ./test.sh
    )
    [[ $? != "0" ]] && FAIL=1
    if [[ $FAIL != "0" ]]; then
        echo
        echo -e "$red!! FAIL $name$reset"
        EXIT=1
    fi
    echo
    echo
done

exit $EXIT
