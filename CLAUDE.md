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
a CLJS runtime connects. The DEV box in the bottom right corner runs the whole
test suite ("Run tests", details in the browser console); its "Run on load"
checkbox, off by default, runs it on every page load.

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

### Controlled input e2e (Playwright, Chromium)

`e2e/inputs.spec.js` types into the page `test/reagenttest/e2e/inputs.cljs`
(shadow-cljs `:e2e` build) with real, trusted input (keyboard, CDP IME
composition) to test `reagent.impl.input`. Known bugs are marked `test.fail`
with the issue link (the list reporter shows them as ✘ but counts them as
passed; they fail the run if they start passing).

```bash
npx playwright install chromium-headless-shell   # once
npm run test:e2e   # shadow-cljs release e2e && playwright test
```

`npx playwright test` alone reuses the last build; `--repeat-each 20` checks
for flakiness. The page is served on port 8091; set `E2E_PORT` to run in
several checkouts at once. To try the page by hand, `npm run e2e:page` builds
and serves it on http://127.0.0.1:8091 (`PORT` overrides), or start `e2e-page`
from `.claude/launch.json` to serve the last build. Run the specs with another
`E2E_PORT` meanwhile.

### All environments (CI-equivalent, slow)

`./run-tests.sh` runs every `test-environments/*/test.sh` (lein/cljsbuild with npm
and cljsjs, bundled, advanced compilation, shadow release). Only needed for changes
that could behave differently under advanced compilation or across build tools.

## Formatting

cljfmt with `.cljfmt.edn` (clojure-lsp reads the same file). The Claude hook in
`.claude/settings.json` runs it automatically after each Write/Edit. Manually
(`cljfmt.sh` only passes tracked files; `examples/*/` contain ignored build caches):

```bash
./cljfmt.sh check
./cljfmt.sh fix
```

Community style. When adding a macro with a body, add an `:extra-indents`
entry for it in `.cljfmt.edn` (fully qualified symbol; aliases and `:refer`
are resolved).

## Lint

```bash
./lint.sh
```

Lints `src test demo prerender bb` in one run, then each `examples/*/src` on
its own (several examples share the `example.core` namespace name, so linting
them together gives false "Unresolved var" warnings).

Should report no findings; keep it that way. CI pins the clj-kondo version
(`.github/workflows/clojure.yml`); an older local binary can report false positives.

`.clj-kondo/<group>/<artifact>/` are configs imported from libraries on the
classpath (clojure-lsp copies them, or `clj-kondo --lint <classpath>
--dependencies --copy-configs --skip-lint`). The ones the code here uses are
committed; don't edit or reformat them. The rest come from libraries built into
babashka and are listed in `.gitignore`.

## Conventions

- Keep hot paths (template/hiccup parsing, `class-names`, ratom deref) allocation-light;
  performance matters here.
- Changes to public API need docstrings and a CHANGELOG entry.
