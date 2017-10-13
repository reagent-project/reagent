#!/bin/bash
set -ex
lein with-profile react-16,test do clean, doo chrome-headless client once
test -f out/cljsjs/react/development/react.inc.js
