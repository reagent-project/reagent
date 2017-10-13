#!/bin/bash
set -ex
lein with-profile test do clean, doo chrome-headless client once
test -f out/node_modules/react/index.js
