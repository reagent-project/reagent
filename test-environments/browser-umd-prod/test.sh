#!/bin/bash
set -ex
lein do clean, doo chrome-headless prod-test once
test -f target/cljsbuild/prod-test/main.js
