---
reviewed: 2026-11-18
---
# Reagent development

## Requirements

- Node.js and npm (`.nvmrc` has the version CI uses)
- Java 21
- [Clojure CLI](https://clojure.org/guides/install_clojure) and
  [Leiningen](https://leiningen.org/), for the full test run
- [clj-kondo](https://github.com/clj-kondo/clj-kondo) and
  [cljfmt](https://github.com/weavejester/cljfmt), for linting and
  formatting

Install the npm dependencies (shadow-cljs, React, Karma) first:

```bash
npm install
```

## Development loop

Start the shadow-cljs watch build for the site and the tests:

```bash
npx shadow-cljs watch client
```

Then open http://localhost:8090. The page reloads the code when you save a
file. The DEV box in the bottom right corner runs the whole test suite ("Run
tests"), with the details in the browser console. Its "Run on load" checkbox
runs the tests on every page load.

### REPL

shadow-cljs starts an nREPL server; the port is in `.shadow-cljs/nrepl.port`.
Connect to it with your editor, switch to the ClojureScript REPL of the
browser page and run a single test namespace:

```clojure
(shadow.cljs.devtools.api/repl :client)
(require 'reagent.impl.util-test :reload)
(cljs.test/run-tests 'reagent.impl.util-test)
```

The REPL needs the page open in a browser.

## Running tests

Tests are in `test/`. Use `reagenttest.utils/deftest` instead of
`cljs.test/deftest`: it defines each test for both the default (class
component) compiler and the function component compiler.

Before finishing a change, run the full suite in headless Chrome:

```bash
npx shadow-cljs compile test && npx karma start test-environments/shadow-cljs-prod/karma.conf.js --single-run
```

### All environments

`./run-tests.sh` runs the tests in every environment under
`test-environments/` (Leiningen with Cljsjs and npm, bundlers, advanced
compilation, Node, shadow-cljs release), like CI does. It is slow, and only
needed for changes that could behave differently between build tools or under
advanced compilation. `./run-tests.sh clj` runs only the bundler
environments (`bundle*`), `./run-tests.sh lein` all the others.

### Controlled input tests

`e2e/inputs.spec.js` uses [Playwright](https://playwright.dev/) to type into
the page in `test/reagenttest/e2e/inputs.cljs` with real keyboard and IME
input, to test controlled inputs in Chromium:

```bash
npx playwright install chromium-headless-shell   # once
npm run test:e2e
```

Known bugs are marked with `test.fail` and a link to the issue.
`npx playwright test` alone reuses the last build. The page is served on port
8091; set `E2E_PORT` to use another port.

To try the page by hand, run `npm run e2e:page` and open
http://127.0.0.1:8091.

CI runs these in the `e2e` job of `.github/workflows/clojure.yml`. Traces of
failed tests are uploaded as the `playwright-test-results` artifact.

## Lint and formatting

```bash
./lint.sh
./cljfmt.sh check   # or: ./cljfmt.sh fix
```

CI runs both and they should report nothing. CI pins the clj-kondo and cljfmt
versions (`.github/workflows/clojure.yml`); older versions can report false
positives.

## Building the package

To build Reagent and use the built version in your application, run
`lein install` and change your application's dependency to the version in
`project.clj`.

If `project.clj` has a version that is released on Clojars, this overwrites
that version in your local Maven repository. To restore the released version,
remove its directory from `~/.m2/repository/reagent/reagent/`.
