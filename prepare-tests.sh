#!/bin/bash

for env in test-environments/*; do
    (
    cd "$env"
    npm install
    )
done
