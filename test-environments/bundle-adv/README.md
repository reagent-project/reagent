# Advanced optimized JS bundle

- `tesh.sh`
    - Creates `karma.js` bundle using `karma.edn`, including the test suite.
    - Runs Karma with `karma.config.js`.
- `build.sh`
    - Creates `index.js` bundle which contains the demo site, without test suite.
    - Runs Webpack and copies static files to target folder.
- The bundles share the ClojureScript compiler output-dir, so need to
compile everything twice.
