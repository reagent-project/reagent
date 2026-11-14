# Reagent

ClojureScript interface to React. Library source in `src/reagent/`, tests in `test/`.

## Layout

- `src/reagent/core.cljs` – public API; `ratom.cljs` – reactive atoms/reactions/cursors
- `src/reagent/impl/` – internals: `template.cljs` (Hiccup → React elements),
  `component.cljs` (class/function component wrapping), `util.cljs`, `batching.cljs`
- `test/reagenttest/test*.cljs` and `test/reagent/**/*_test.cljs` – tests
- `demo/`, `site/`, `prerender/` – the reagent-project.github.io site; `examples/` – sample apps
- `doc/` – user docs (markdown), `CHANGELOG.md` – add user-facing changes under an
  "Unreleased" section at the top

## Tests

`reagenttest.utils/deftest` (not `cljs.test/deftest`) defines each test twice:
`--class` (default class compiler) and `--fn` (`{:function-components true}`
compiler). Use `u/*test-compiler*` / `with-render` helpers so both variants are exercised.

### Fast loop: REPL in the dev build

Start the dev server (`.claude/launch.json` → `shadow-client`, i.e.
`npx shadow-cljs watch client`), then open http://localhost:8090 in a browser so
a CLJS runtime connects. The page also runs the whole test suite on load (result
box top-right, details in the browser console).

Then use the shadow nREPL (port in `.shadow-cljs/nrepl.port`):

```bash
clj-nrepl-eval -p $(cat .shadow-cljs/nrepl.port) "(shadow.cljs.devtools.api/repl :client)"
clj-nrepl-eval -p $(cat .shadow-cljs/nrepl.port) "(require 'reagent.impl.util-test :reload) (cljs.test/run-tests 'reagent.impl.util-test)"
```

The `repl :client` switch persists for the nREPL session. It fails with "no
runtime" if no browser has the page open. Warnings about missing
`cider.nrepl`/`refactor-nrepl` middleware at startup come from the local
`.nrepl.edn` and are harmless.

### Full suite, headless (before finishing a change)

```bash
npx shadow-cljs compile test && npx karma start test-environments/shadow-cljs-prod/karma.conf.js --single-run
```

~30s cold; faster if the shadow server is already running. Runs all ~200 tests
in ChromeHeadless.

### All environments (CI-equivalent, slow)

`./run-tests.sh` runs every `test-environments/*/test.sh` (lein/cljsbuild with npm
and cljsjs, bundled, advanced compilation, shadow release). Only needed for changes
that could behave differently under advanced compilation or across build tools.

## Lint

```bash
clj-kondo --lint src test
```

master already has a few findings; only care about new ones in files you touched.

## Conventions

- Keep hot paths (template/hiccup parsing, `class-names`, ratom deref) allocation-light;
  performance matters here.
- Changes to public API need docstrings and a CHANGELOG entry.
