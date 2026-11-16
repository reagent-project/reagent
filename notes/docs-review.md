# Reagent user docs review

Started 2026-11-14. Last updated 2026-11-16. This is a working list: update or remove entries as the issues are fixed.

When a page has been reviewed (and updated) against the current Reagent, add YAML front matter at the top of its Markdown file:

```yaml
---
reviewed: 2026-11-14
---
```

The site shows a "Not reviewed yet" notice on guide pages without it, and "Reviewed <date>" next to the edit link on pages with it. Note that cljdoc's Markdown renderer has no front matter support, so the block probably renders there as a rule and a "reviewed: …" heading. GitHub shows it as a small table.

**Summary**
- Almost every rendering example still uses `reagent.dom/render` (React 18 only), and two use `reagent.core/render`, which now throws. No doc outside `2.0-upgrade.md` shows `reagent.dom.client`. `ReactFeatures.md` uses `react-dom/hydrate`, which React 19 removed.
- The 2.0 features `defc` and `reagent.hooks` are only introduced briefly in `WhatsNew2.md`; no guide covers them. The Hooks and function-component sections still show `:f>` plus raw `react/useState`, and they include a "Pre 1.0 workaround" that links to the archived `hx` library.
- Some docs name APIs that don't exist: `r/dom-node`, `after-update` (the function is `after-render`) and the `make-reaction` option `derefed`. Others describe behaviour wrongly: ratom-triggered re-renders skipping `component-did-update`, ratoms comparing with `=`, and "Reactions power `r/atom`".
- The Cljsjs, npm and Material-UI advice is out of date: the FAQ says Reagent depends on Cljsjs packages (`project.clj` has `:dependencies []`), `0.8-upgrade.md` calls Cljsjs "the recommended setup", and the MUI doc shows the v1 `"material-ui"` API while the example project uses `@mui/material` v7.
- The two indexes have drifted apart: `README.md` doesn't list 7 of the docs that `cljdoc.edn` lists, its numbering is broken, and the HTML-entity FAQ exists twice with different advice.

Scope: all files in `doc/` as of 2026-11-14, checked against the source in `src/reagent/`. Line numbers match the files as of the last update. Not reviewed yet: `GettingStarted.md`, `WebComponents.md` and the deref caveats and cursor watch sections added to `ManagingState.md` after the review.
---

## 1. Rendering entry point / React versions

- **[high] `reagent.core/render` is used, but it throws.**
  - Where: `doc/CreatingReagentComponents.md:213` (`(reagent/render [my-component 1 2 3] ...)`) and `doc/WhenDoComponentsUpdate.md:229` (`(reagent.core/render [top-level-component] ...)`).
  - Verified against `src/reagent/core.cljs:377-381`: the function is `:deprecated "0.10.0"` and throws "moved to reagent.dom namespace".
  - Fix: use `rdomc/create-root` + `rdomc/render` (from `reagent.dom.client`), or at least `reagent.dom/render`.
- **[high] The "primary entrypoint" is described as `reagent.dom/render`.**
  - Where: `doc/UsingHiccupToDescribeHTML.md:179-188`.
  - Verified against `src/reagent/dom.cljs:31-33`. That docstring says "Usable only with React 18 or older", and the repo tests against React 19.3 (`package.json:24-25`).
  - Fix: make `reagent.dom.client/create-root` + `render` the primary example and mention `reagent.dom` as legacy (React ≤18).
- **[medium] Other examples also use `rdom/render` / `reagent.dom/render`.**
  - Where: `doc/InteropWithReact.md:35-48`, `doc/ManagingState.md:3,134`, `doc/ReactFeatures.md:40,73,86`, `doc/ReagentCompiler.md:16,36` and `doc/examples/smooth-ui.md:19`.
  - Fix: switch them to `reagent.dom.client`. In `ReagentCompiler.md`, show `(rdomc/render root [main] functional-compiler)`; the 3-arity is verified at `src/reagent/dom/client.cljs:57-60`.
- **[high] Hydrate example uses an API that React 19 removed.**
  - Where: `doc/ReactFeatures.md:243-247` (`(react-dom/hydrate (r/as-element ...) container)`).
  - Reagent provides `reagent.dom.client/hydrate-root` (verified at `src/reagent/dom/client.cljs:72-81`; it takes `{:keys [compiler on-recoverable-error identifier-prefix]}`).
  - Fix: show `(rdomc/hydrate-root container [main-component])` and update the link to react.dev.
- **[medium] The 2.0 upgrade guide is thin and its React version is stale.**
  - Where: `doc/2.0-upgrade.md:11-13` says `npm i react@19.2.0`, but `package.json:24` and the CHANGELOG "Unreleased" section test 19.3.
  - The guide also omits these 2.0 breaking changes listed in `CHANGELOG.md:62-66`:
    - `reagent.dom/dom-node` was removed.
    - The 2-arity of `force-update` was removed.
    - `rdomc/unmount` and `hydrate-root` exist.
    - `defc` and `reagent.hooks` are new.
  - Fix: use a generic `react@19`, and add a short "Other changes" list that links to the CHANGELOG.
- **[medium] `reagent1.md` mixes up "Reagent 1" with "React 18 via Cljsjs".**
  - Where: `doc/reagent1.md:1-13`. The heading is "# Reagent 1", the cljdoc title is "Reagent 1 and React 18", and the content is really "using React ≤18 / Cljsjs", which also applies to Reagent 2 (see `2.0-upgrade.md:8-9`).
  - Fix: retitle it to "Using React 18 / Cljsjs" and cross-link it from `2.0-upgrade.md`.
- **[medium] `BatchingAndTiming.md` says nothing about React 18/19.**
  - Where: `doc/BatchingAndTiming.md:3-18`. It describes rAF batching only. Since 2.0, the ratom queue is flushed via `flushSync` (verified at `src/reagent/impl/batching.cljs:31-50` and `dom/client.cljs:61`).
  - Fix: add a paragraph or a link to `React19.md`. `React19.md` should in turn link back here, since the two cover the same topic.
- **[low] Unnecessary `as-element` wrapping in the SSR example.**
  - Where: `doc/ReactFeatures.md:238-241`. `render-to-string` already accepts Hiccup and an optional compiler (verified at `src/reagent/dom/server.cljs:8-15`). Wrapping in `as-element` makes the compiler argument ineffective.
  - Fix: `(reagent.dom.server/render-to-string [main-component])`.

## 2. Component forms, function components and hooks

- **[high] `defc` and `reagent.hooks` aren't covered in the guides.**
  - Only `WhatsNew2.md` (added 2026-11-16) introduces them with a short example. Verified that they exist: `src/reagent/core.clj:29` (`defmacro defc`) and `src/reagent/hooks.cljs` (`use-state`, `use-effect`, `use-ref`, …).
  - Docs that should mention them:
    - `doc/ReactFeatures.md:144-224` (Function components / Hooks)
    - `doc/ReagentCompiler.md`
    - `doc/CreatingReagentComponents.md` ("The Three Ways", line 33)
  - Fix:
    - Add a `defc` + `reagent.hooks` section to ReactFeatures.
    - Present `defc` as the preferred way to get a function component.
    - Mention it as a fourth option in CreatingReagentComponents.
- **[medium] Hooks section has outdated content.**
  - Where: `doc/ReactFeatures.md:190-224`. The "Pre 1.0 workaround" and its link to `hx` (an archived library) are obsolete. The example at line 175-188 uses raw `react/useState` rather than `reagent.hooks/use-state`.
  - Fix: delete the Pre 1.0 section and use `reagent.hooks` in the example.
- **[high] `r/dom-node` is recommended, but it doesn't exist.**
  - Where: `doc/CreatingReagentComponents.md:184` ("You may also find `r/dom-node` helpful…").
  - Verified: there is no `dom-node` in `src/reagent/core.cljs`. `reagent.dom/dom-node` was removed in 2.0 (`CHANGELOG.md:63`).
  - Fix: recommend a `:ref` callback instead (see `FAQ/UsingRefs.md`).
- **[high] Wrong claim that ratom re-renders skip lifecycle methods.**
  - Where: `doc/WhenDoComponentsUpdate.md:197-201`: "when the re-render occurs because an input ratom changed, **Lifecycle functions are not run**… `component-did-update` will not be called".
  - Ratom-triggered renders call `.forceUpdate` (`src/reagent/impl/batching.cljs:41-50`). React's `forceUpdate` only skips `shouldComponentUpdate`; it still runs `componentWillUpdate`/`componentDidUpdate`.
  - Fix: reword to "`should-component-update` is skipped; `component-did-update` is still called." Confirm in the REPL if desired.
- **[medium] Contradictory equality semantics for ratoms.**
  - Where: `doc/WhenDoComponentsUpdate.md:173-185`. The text says ratoms use `identical?`, then an "Update" says that since 0.6.0 they use `=`.
  - Source: component render reactions skip only when the old and new values are `identical?` (`src/reagent/ratom.cljs:396`), and `RAtom` notifies watchers on every reset (`ratom.cljs:138-145`). `=` is used for derived reactions, cursors and `track` (`ratom.cljs:438,468`).
  - Fix: rewrite this section once, correctly, without the "Update" addendum.
- **[medium] Form-2 "rookie mistake" is duplicated.**
  - Where: `doc/FAQ/ComponentNotRerendering.md:21-41` copies `doc/CreatingReagentComponents.md:140-155` almost word for word.
  - Fix: keep the explanation in one place and link to it from the other.
- **[low] Outdated function-compiler limitations list.**
  - Where: `doc/ReagentCompiler.md:3,40-49`. It says "a new way" and "(in React 17)", and lists a `useEffect` cleanup timing note with a link to the React 17 RC. The benchmark image is captioned "1.0.0-alpha2" (`:51`).
  - Fix: update the list for React 19 or date it explicitly. Add `defc` as the per-component alternative to the global compiler.
- **[low] Misleading "old Context API" reference.**
  - Where: `doc/ReactFeatures.md:92-93` calls `context-wrapper` "old React lifecycle Context API" and links to `testreagent.cljs#L1159-L1168`.
  - The test actually uses `createContext` + `:context-type` (`test/reagenttest/testreagent.cljs:1195-1217`), and the line numbers are stale.
  - Fix: remove the sentence or fix both the description and the link.
- **[low] Unresolved FIXME and question marks in published text.**
  - Where: `doc/ReactFeatures.md:101-102` (FIXME about `component-did-catch`) and `:296-299` ("(?)" twice, plus the typo "to now throw").
- **[medium] StrictMode section is out of date.**
  - Where: `doc/ReactFeatures.md:336-356` ("With Reagent this unfortunately might not be helpful").
  - It doesn't mention:
    - the StrictMode fixes in 2.0.0 and 2.0.1 (`CHANGELOG.md:39-47,81`);
    - the `strict-mode?` 4th argument of `reagent.dom.client/render` (`src/reagent/dom/client.cljs:60-71`).
  - Fix: document the argument and the current status.

## 3. State

- **[high] Non-existent function name.**
  - Where: `doc/BatchingAndTiming.md:122` ("The `after-update` is similar…").
  - Verified: the function is `reagent.core/after-render` (`src/reagent/core.cljs:348`); `after-update` doesn't exist.
- **[medium] Wrong `make-reaction` options.**
  - Where: `doc/ManagingState.md:222-226`. It lists a `derefed` option ("TODO unclear") and says `on-set`/`on-dispose` run "when the reaction is set and unset from the DOM".
  - Verified at `src/reagent/ratom.cljs:519-531`:
    - The only options are `:auto-run`, `:on-set` and `:on-dispose`.
    - `on-set` runs when the value updates, before watchers are notified.
    - `on-dispose` runs when the reaction is disposed.
- **[medium] "Reactions are what give `r/atom`, `r/cursor`, and `r/wrap` their power" is wrong.**
  - Where: `doc/ManagingState.md:220`.
  - `RAtom` (`ratom.cljs:126`) and `Wrapper` (`:561`) don't use Reaction. Only `RCursor`/`Track` do.
  - Fix: say "reactions back cursors and `track`".
- **[medium] Wrong flush function for reactions.**
  - Where: `doc/ManagingState.md:261` ("call `flush` if you depend on reaction side effects").
  - `r/flush` flushes the render queue (`core.cljs:211-217`). Reaction queues are flushed by `reagent.ratom/flush!` (`ratom.cljs:507`).
  - Fix: name the correct function.
- **[medium] WIP markers.**
  - Where: `doc/ManagingState.md:5,107,207,210,226` contain "TODO" notes, and `doc/README.md:7` still has the "[WIP]" title.
  - Fix: resolve them or move them to an issue.
- **[low] Example bugs in ManagingState.md.**
  - `:150` refers to `app-state`, but the example's atom is `state`.
  - `:157` has a stray `]` in `(r/wrap first-name swap! n assoc :first-name)]`.
  - The file mixes the `reagent/` and `r/` aliases.

## 4. Interop

- **[medium] Broken Material-UI example code.**
  - Where: `doc/examples/material-ui.md`.
    - `:17` requires `"material-ui"` (v1).
    - `:31` uses `(def example [] ...)`, which is invalid.
    - `:34` uses `e` inside `#(...)` instead of `%`.
  - The linked example project uses `@mui/material` 7 (`examples/material-ui/package.json:7`, `src/example/core.cljs:2-7`).
  - Fix: update the snippet to match `examples/material-ui/src/example/core.cljs`. Drop the "v1" wording (`:12`) and the dead "async rendering" link (`:8`).
- **[medium] Smooth UI example is dead and has a broken link.**
  - Where: `doc/examples/smooth-ui.md:3` links to `../CotrolledInputs.md` (typo; the file is `ControlledInputs.md`). It also uses `rdom/render` (`:19`), and Smooth UI is unmaintained.
  - Fix: fix the link, or drop the page and its links in `ControlledInputs.md:46-47` and cljdoc.edn.
- **[low] Incomplete sentence in ControlledInputs.md.**
  - Where: `doc/ControlledInputs.md:23-25`. The sentence ends with a colon and is followed directly by the React Native section; the Material-UI example it introduces is only linked at the bottom.
- **[low] Unclear wording about `current-component`.**
  - Where: `doc/InteropWithReact.md:206-207` ("must be called outside of e.g. event handlers"). The intended meaning is "not inside".
  - The props/children split described at `:197-199` applies to the class compiler only.
  - Fix: note that with `defc` or the function compiler, `current-component` is a mock object (as `ReagentCompiler.md:37` says).
- **[low] ReactRenderers example doesn't compile as written.**
  - Where: `doc/ReactRenderers.md:31-46`. The `ns` doesn't require `reagent.core :as r`, and `Root` is defined twice (`def` and `defn`) in one snippet.
  - Fix: add the require and mark the definitions as alternatives.

## 5. FAQ duplicates and consistency

- **[medium] HTML entities are covered twice, with different advice.**
  - Where: `doc/FAQ/UsingAnEntity.md` and `doc/FAQ/HtmlEntities.md`.
  - What differs:
    - UsingAnEntity warns that `unescapeEntities` needs the DOM (Node/SSR).
    - HtmlEntities instead suggests literal or `×` characters and says nothing about the DOM requirement.
  - Fix: merge them into one FAQ, and recommend literal or `\uXXXX` characters as the first option because they also work in SSR.
- **[medium] Cljsjs FAQ is wrong for 2.x.**
  - Where: `doc/FAQ/CljsjsReactProblems.md:10-14`. It says to add exclusions "so only Reagent will have transitive dependency on React packages" and that "Reagent still depends on empty package" (`cljsjs/react-dom-server`).
  - Verified: `project.clj:6` has `:dependencies []`, and Cljsjs is only in the `:dev` profile. The FAQ also mentions Boot (`:5,16`).
  - Fix: say that Reagent has no React dependency, so users must provide React themselves (npm recommended; Cljsjs only for React ≤18).
- **[low] Inconsistent FAQ structure.**
  - Headings: fixed on 2026-11-14. Every FAQ now starts with the question as an H1, and `cljdoc.edn` has short titles.
  - The "Up: FAQ Index" footer is missing in CljsjsReactProblems, ForcingComponentRecreation and HtmlEntities.
  - Fix: use the same footer everywhere, or drop it since the site has a nav.
- **[low] ForcingComponentRecreation example shadows `comp`.**
  - The example function is named `comp`, which shadows `clojure.core/comp`. (The title mismatch with the index was fixed on 2026-11-14.)
- **[low] Refs FAQ only covers Form-2.**
  - Where: `doc/FAQ/UsingRefs.md:27`. It could add a `defc` + `reagent.hooks/use-ref` variant.

## 6. Outdated libraries and links

- **[medium] `0.8-upgrade.md` reads as current advice.**
  - Where: `doc/0.8-upgrade.md:15` ("**This is the recommended setup.**" for Cljsjs). This contradicts `2.0-upgrade.md:15-16` (Cljsjs can't do React 19).
  - The file also references `create-react-class` (`:25,56,73`), which no longer appears in `src/`, and contains a "???" stub at `:79`.
  - Fix: add a "Historical (Reagent 0.8, 2018)" banner at the top.
- **[medium] Dev workflow in `development.md` is outdated.**
  - Where: `doc/development.md:23-33`. It only describes lein Figwheel (`client`/`client-npm` builds still exist in `project.clj:55-73`). The current dev loop (shadow-cljs `watch client` on :8090, and `npx shadow-cljs compile test` + karma) isn't mentioned. The Karma link points to the 2.0 docs (`:5`).
  - Fix: add the shadow-cljs workflow first.
- **[low] Many links still point to legacy reactjs.org.**
  - Where:
    - `ReactFeatures.md:6,30,32,53,95,97,144,173,226,243`
    - `CreatingReagentComponents.md:90,100,176`
    - `UsingHiccupToDescribeHTML.md:63`
    - `FAQ/MyAttributesAreMissing.md:7`
    - `FAQ/dangerouslySetInnerHTML.md:3`
    - `FAQ/UsingRefs.md:33`
    - `ReagentCompiler.md:46`
    - `examples/material-ui.md:10`
  - Fix: update them to react.dev equivalents.

## 7. Index and structure

- **[high] README is missing docs that cljdoc.edn (the site nav, via `demo/reagentdemo/docs.clj:31`) lists.**
  - `doc/README.md` doesn't list:
    - Security.md
    - ReactRenderers.md
    - ServerSideRendering.md
    - React19.md
    - reagent1.md
    - examples/smooth-ui.md
  - Readers on GitHub/cljdoc landing on README won't find the React 19 or 2.0 upgrade docs.
  - Fix: list everything that cljdoc.edn lists, or make README a short intro that points at the nav.
- **[medium] Broken numbering in README.**
  - Where: `doc/README.md:10-13` (four items numbered "8."), and `:32-39` (FAQ numbered 1,1,2,3,4,5,5,5,6).
  - Fix: use unnumbered lists.
- **[medium] Contribution note names only README.**
  - Where: `doc/README.md:46-50` ("Want To Add An FAQ?") says to update only this README. The site nav comes from `cljdoc.edn`.
  - Fix: say both files must be updated.
- **[low] Item listed only in README.**
  - The external FAQ "How do I access props in lifecycle methods?" (`README.md:40`) and the "Commercial Videos" section exist only in README. purelyfunctional.tv is listed twice (`:16`, `:27`).
- **[low] Titles differ between files and indexes.** The sidebar titles in `cljdoc.edn` were shortened on 2026-11-14 and the README still uses the old ones:
  - `reagent1.md` H1 "Reagent 1" vs "Reagent 1 and React 18";
  - `ManagingState.md` H1 lacks "[WIP]" while README still has it;
  - `README.md:44` "MaterialUI with working TextField" vs cljdoc "Material-UI v1" at HEAD.

## 8. Style and terminology

- **[low] Some docs have no H1.**
  - `doc/CreatingReagentComponents.md:1-8` starts with a blank line and body text, then `#####` sub-headings (`:8,15`).
  - `doc/UsingSquareBracketsInsteadOfParens.md:1` and `doc/WhenDoComponentsUpdate.md:1` start with body text.
  - `doc/README.md:1` starts at `##`.
  - Fix: give every page a single H1 that matches its index title.
- **[medium] "Ratom" spelling varies.** It appears as "ratom", "RAtom" and "Ratom" (e.g. `ReactFeatures.md:157,171`, `ReagentCompiler.md:26,41`, `React19.md:27,50`). Open: decide which spelling to use, then apply it everywhere (docs and docstrings).
- **[low] First-person, uncertain tone.**
  - Where: `doc/React19.md:34-35` ("but I don't know how exactly React batching works") and `:58`, plus `doc/BatchingAndTiming.md:24,34` ("on my Macbook Air").
  - Fix: rephrase neutrally.
- **[low] TODO left in Security.md.**
  - Where: `doc/Security.md:11` ("TODO: Does this change in React 19?").

### Out of scope (source, noticed while verifying)
- `src/reagent/core.cljs:155-160`: the `force-update` docstring still describes the removed second ("deep") argument.

## Fixed

- 2026-11-14: every FAQ page starts with the question as its H1, and `cljdoc.edn` has short sidebar titles.
- 2026-11-14: typos fixed: styntax, attibutes, seperately, creats, announcenment, "to to", rereun, "an component", Offical, "it's own", "Its a"/"Its just".
- 2026-11-14: component forms are written in lower case (`form-1`, `form-2`, `form-3`) except at the start of a sentence or heading.
- 2026-11-14: `http://` links changed to `https://` (all checked). `README.md` now links to re-frame's current docs (`day8.github.io/re-frame/Using-Stateful-JS-Components/`) and `timothypratley.blogspot.com`.
- 2026-11-16: new `WhatsNew2.md` (from the 2.0.0/2.0.1 changelog); "Reagent 2" nav section with it and the upgrade guide, also at the top of `README.md`.
- 2026-11-16: `WhatsNew2.md` and `2.0-upgrade.md` marked reviewed.
