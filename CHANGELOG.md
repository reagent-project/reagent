# Changelog

## Unreleased

**[compare](https://github.com/reagent-project/reagent/compare/v1.1.1...master)**

### Features and changes

- Update React to version 18
    - `reagent.dom/render` continues to use old `ReactDOM.render`
    - All old test cases are still using React 17 mode
- Add new `reagent.dom.client` namespace for `createRoot` and related helpers.
    - One new test case is using this and testing Reagent with the new React batching.
- Deprecate `reagent.dom/dom-node` and `reagent.dom/force-update-all`

## 1.1.1 (2022-03-09)

**[compare](https://github.com/reagent-project/reagent/compare/v1.1.0...v1.1.1)**

### Features and changes

- Add name property to function and class components created by Reagent,
this should add real function names to React component stacks on Error boundaries
and errors on the console. ([#549](https://github.com/reagent-project/reagent/pull/549))

## 1.1.0 (2021-06-05)

**[compare](https://github.com/reagent-project/reagent/compare/v1.0.0...v1.1.0)**

### Features and changes

- **BREAKING:** Removed dependencies to Cljsjs React packages and removed `deps.cljs` file ([#537](https://github.com/reagent-project/reagent/issues/537))
    - Projects using Reagent should declare dependency to React themselves
    using the method they choose (cljsjs packages, npm).
- Removed additional error logging from Reagent render method wrapper,
as React logs errors since version 16. ([#531](https://github.com/reagent-project/reagent/issues/531))
    - This removes unnecessary "Error rendering component" messages when
    exception is thrown during render call.
- Fix `*assert*` check in `with-let` macro
- Add `reaction` macro to `reagent.core` (same as one in `reagent.ratom`)
- Add `parse-tag` method to Compiler protocol, and `parse-tag` option to
`create-compiler` function. This can be used to customize how e.g. class names are
parsed from the Hiccup element keywords. ([#532](https://github.com/reagent-project/reagent/pull/532) by @bendyorke)

Note: Tests aren't being run against Cljsjs React packages in Node, as there are some
strange problems with that environment. It is better to use npm React
when running on Node anyway. ([#530](https://github.com/reagent-project/reagent/issues/530))

### Bugfixes

- Remove unnecessary return value after throw from deprecated `render` function ([#533](https://github.com/reagent-project/reagent/issues/533))
- Read `:key` from props map for `:input` elements ([#529](https://github.com/reagent-project/reagent/issues/529))
- Fix `with-let` macro calling body after first render, even if
binding value expressions throw errors ([#525](https://github.com/reagent-project/reagent/issues/529))
    - Now binding value expressions are run again until they succeed,
    and thus also throw the error for further renders and prevent
    body being called.

### Documentation

- Documentation fixes and improvements by:
    - @oakmac
    - @chancerussell
    - @green-coder

## 1.0.0 (2020-12-21)

**[compare](https://github.com/reagent-project/reagent/compare/v0.10.0...v1.0.0)**

Special thanks for this release to [Clojurist Together](https://www.clojuriststogether.org/)
for funding the work on React function components work and @roman01la for
ideas on the implementation. Also thanks to everyone who tested the new features
and reported results.

Changes in this entry are compared to the 0.10.0 release.

### Features and changes

- **Option to render Reagent components as React function components instead of
class components**
    - To ensure backwards compatibility by default, Reagent works as previously and
    by default creates class components.
    - New Compiler object can be created and passed to functions to control
    how Reagent converts Hiccup-style markup to React components and classes:
    `(r/create-compiler {:function-components true})`
    - This function components implementation supports RAtoms and passes
    the same test suite as class components, except for a few differences.
    - Passing this options to `render`, `as-element` and other calls will control how
    that markup tree will be converted.
    - `(r/set-default-compiler! compiler)` call can be used to set the default
    compiler object for all calls.
    - [Read more](./doc/ReagentCompiler.md)
    - [Check example](./examples/functional-components-and-hooks/src/example/core.cljs)
- Added `:f>` shortcut to create Function component from ClojureScript
function, and `:r>` (raw) shortcut to use React components, without
props conversion done by `:>`. Hiccup children are automatically
converted to React element calls. ([#494](https://github.com/reagent-project/reagent/issues/494))
- **DOM related functions have been removed from `reagent.core` namespace.**
    - `render` and other DOM functions are available in `reagent.dom` namespace.
    - This is to make non-DOM environments (React-native) first class targets with Reagent,
    as requiring `react-dom` causes problems in such environments.
    - There is deprecated no-op `render` function on core ns, this will show
    deprecation warning during compilation and throw runtime error about
    function being moved. This should be easier to debug than just
    warning about missing var and calling null fn on runtime. ([#501](https://github.com/reagent-project/reagent/issues/501))
- Change RAtom (all types) print format to be readable using ClojureScript reader,
similar to normal Atom ([#439](https://github.com/reagent-project/reagent/issues/439))
    - Old print output: `#<Atom: 0>`
    - New print output: `#object[clojure.ratom.RAtom {:val 0}]`
    - Still not readable by default, requires custom reader for `object` tag.
converted to React element calls. ([#494](https://github.com/reagent-project/reagent/issues/494))
- Replaced `findDOMNode` use in Reagent input workaround with ref, to ensure
[StrictMode](https://reactjs.org/docs/strict-mode.html) compatibility ([#490](https://reactjs.org/docs/strict-mode.html))
    - Fix using ref object from `react/createRef` with controlled inputs ([#521](https://github.com/reagent-project/reagent/issues/521))
- Update default React version to 17.0.1 ([#518](https://github.com/reagent-project/reagent/pull/518))

### Bugfixes

- Fixed merge-props adding `:class` property to result even if no argument
defined `:class` ([#479](https://github.com/reagent-project/reagent/pull/479) by @achikin)
- Fix using `:className` property together with keyword class shortcut ([#433](https://github.com/reagent-project/reagent/issues/433))
- Fix incorrect missing React key warnings with `:>` ([#399](https://github.com/reagent-project/reagent/issues/399))
- Fix `requestAnimationFrame` call in Firefox extension context ([#438](https://github.com/reagent-project/reagent/issues/438))


### Documentation

- New [react-mde example](./examples/react-mde/) by @vitorqb
- Documentation fixes and improvements by:
    - @dominicfreeston
    - @MokkeMeguru
    - @lucywang000
    - @zelark
    - @davidjameshumphreys
    - @vgautamm
    - @bjrnt
    - @LeifAndersen
    - @mikew1
    - @suud
    - @nahuel

## 1.0.0-rc1 (2020-11-26)

**[compare](https://github.com/reagent-project/reagent/compare/v1.0.0-alpha2...v1.0.0-rc1)**

- Update default React version to 17.0.1
- DOM related functions have been removed from `reagent.core` namespace.
    - There is deprecated no-op `render` function on core ns, this will show
    deprecation warning during compilation and throw runtime error about
    function being moved. This should be easier to debug than just
    warning about missing var and calling null fn on runtime.

## 1.0.0-alpha2 (2020-05-13)

**[compare](https://github.com/reagent-project/reagent/compare/v1.0.0-alpha1...v1.0.0-alpha2)**

- Renamed `:functional-components?` option to `:function-components` ([#496](https://github.com/reagent-project/reagent/issues/496))
- Added `:f>` shortcut to create Function component from ClojureScript
function.
- Added `:r>` (raw) shortcut to use React components, without
props conversion done by `:>`. Hiccup children are automatically
converted to React element calls. ([#494](https://github.com/reagent-project/reagent/issues/494))
- Replaced `findDOMNode` use in Reagent input workaround with ref, to ensure
[StrictMode](https://reactjs.org/docs/strict-mode.html) compatibility ([#490](https://reactjs.org/docs/strict-mode.html))

## 1.0.0-alpha1 (2020-04-26)

**[compare](https://github.com/reagent-project/reagent/compare/v0.10.0...v1.0.0-alpha1)**

The changes in this release are quite big and change lots of things in
Reagent implementation, please test and report problems but it is probably good
idea to wait for a bit before using this in production. There could be more
changes before final version.

### Features and changes

- **Option to render Reagent components as React functional components instead of
class components**
    - To ensure backwards compatibility by default, Reagent works as previously and
    by default creates class components.
    - New Compiler object can be created and passed to functions to control
    how Reagent converts Hiccup-style markup to React components and classes:
    `(r/create-compiler {:functional-components? true})`
    - This function components implementation supports RAtoms and passes
    the same test suite as class components, except for a few differences.
    - Passing this options to `render`, `as-element` and other calls will control how
    that markup tree will be converted.
    - `(r/set-default-compiler! compiler)` call can be used to set the default
    compiler object for all calls.
    - [Read more](./doc/ReagentCompiler.md)
    - [Check example](./examples/functional-components-and-hooks/src/example/core.cljs)
- DOM related functions have been removed from `reagent.core` namespace.
    - These were deprecated in the previous release.
- Change RAtom (all types) print format to be readable using ClojureScript reader,
similar to normal Atom ([#439](https://github.com/reagent-project/reagent/issues/439))
    - Old print output: `#<Atom: 0>`
    - New print output: `#object[clojure.ratom.RAtom {:val 0}]`
    - Still not readable by default, requires custom reader for `object` tag.

### Bugfixes

- Fixed merge-props adding `:class` property to result even if no argument
defined `:class` ([#479](https://github.com/reagent-project/reagent/pull/479))
- Fix using `:className` property together with keyword class shortcut ([#433](https://github.com/reagent-project/reagent/issues/433))
- Fix incorrect missing React key warnings with `:>` ([#399](https://github.com/reagent-project/reagent/issues/399))
- Fix `requestAnimationFrame` call in Firefox extension context ([#438](https://github.com/reagent-project/reagent/issues/438))

## 0.10.0 (2020-03-06)

**[compare](https://github.com/reagent-project/reagent/compare/v0.9.1...v0.10.0)**

Main feature of this release is to deprecate functions that are going to be
removed in the future releases, to make transition easier.

- Update default React version to 16.13.0
- All DOM related functions (notably `render` and `dom-node`) are now deprecated in
`reagent.core` namespace and versions in `reagent.dom` namespace should be used
instead. Deprecated functions will be removed in the next version.
This is to make non-DOM environments (React-native) first class targets with Reagent,
as requiring `react-dom` causes problems in such environments.
- Removed deprecated `reagent.interop` namespace (macros `$`, `$!`, `unchecked-aget` and `unchecked-aset`)
- Removed `reagent.core/component-path`. The implementation depended on internal React
details and using just Component `displayName` achieves nearly the same.
- `Error rendering component` messages no longer contain component stack information.
Instead one should use [an Error Boundary](https://reactjs.org/docs/error-boundaries.html#component-stack-traces)
to catch the problem and look at the error information `componentStack` property.

## 0.9.1 (2020-01-15)

**[compare](https://github.com/reagent-project/reagent/compare/v0.9.0...v0.9.1)**

Removed broken untracked files from the package.

## 0.9.0 (2020-01-15)

**[compare](https://github.com/reagent-project/reagent/compare/v0.8.1...v0.9.0)**

**[compare against rc4](https://github.com/reagent-project/reagent/compare/v0.9.0-rc4...v0.9.0)**

**Package includes broken files**

Includes one small improvement, no fixes since rc4.

- Include cursor path in `cursor` assert message ([#472](https://github.com/reagent-project/reagent/pull/472))

## 0.9.0-rc4 (2019-12-17)

**[compare](https://github.com/reagent-project/reagent/compare/v0.9.0-rc3...v0.9.0-rc4)**

Fixes the last known (this far) regression in 0.9.

- Use Component constructor to keep track of component mount order for RAtom updates ([#462](https://github.com/reagent-project/reagent/pull/462))
- Add support for the static [Class.contextType](https://reactjs.org/docs/context.html#classcontexttype) property ([#467](https://github.com/reagent-project/reagent/pull/467))

## 0.9.0-rc3 (2019-11-19)

**[compare](https://github.com/reagent-project/reagent/compare/v0.9.0-rc2...v0.9.0-rc3)**

Fixed a shortcoming of using JS interop forms introduced in 0.9.0-rc1:

- Add type hints for JS interop calls, so that externs inference works for Shadow-CLJS and other users. ([#461](https://github.com/reagent-project/reagent/pull/461))

## 0.9.0-rc2 (2019-10-17)

**[compare](https://github.com/reagent-project/reagent/compare/v0.9.0-rc1...v0.9.0-rc2)**

Fixed two bugs introduced in 0.9.0-rc1.

- Fix `gobj/set` call missing one parameter ([#454](https://github.com/reagent-project/reagent/issues/454), bug introduced by [#325](https://github.com/reagent-project/reagent/issues/325))
- Fix logging missing `:key` errors where problematic form contains function literals ([#452](https://github.com/reagent-project/reagent/issues/452))

## 0.9.0-rc1 (2019-09-10)

**[compare](https://github.com/reagent-project/reagent/compare/v0.8.1...v0.9.0-rc1)**

- Default to React 16.9
- Fix using `with-let` macro in namespaces with `*warn-on-infer*` enabled ([#420](https://github.com/reagent-project/reagent/issues/420))
- Fix using metadata to set React key with Fragment shortcut (`:<>`) ([#401](https://github.com/reagent-project/reagent/issues/401))
- Create React Component without `create-react-class` ([#416](https://github.com/reagent-project/reagent/issues/416))
    - `React.Component` doesn't have `getInitialState` method, but this is implemented by
    Reagent for compatibility with old components.
    - `constructor` can be used to initialize components (e.g. set the state)
- Allow any number of arguments for `reagent.core/merge-props` and
ensure `:class` is merged correctly when it is defined as collection. ([#412](https://github.com/reagent-project/reagent/issues/412))
- Add `reagent.core/class-names` utility functions which can be used
to normalize and combine `:class` values (similar to `classnames` JS library)
- Fix comparing Reagent `PartialFn` to `nil` ([#385](https://github.com/reagent-project/reagent/issues/385))
- Reagent no longer abuses `aget` or `aset` for accessing objects, and instead
uses correct Object interop forms, allowing use of ClojureScript `:checked-arrays :warn` option. ([#325](https://github.com/reagent-project/reagent/issues/325))
- Deprecated `reagent.interop` namespace
    - It is better to use proper object interop forms or `goog.object` functions instead.
- Drop `:export` metadata from `force-update-all` function
- `componentWillReceiveProps`, `componentWillUpdate` and `componentWillMount` lifecycle methods are deprecated
    - Using these directly will show warning, using `UNSAFE_` prefixed version will silence the warning.
    - These methods will continue to work with React 16.9 and 17.
    - Reagent implementation has been changed to use `componentDidMount` instead of
    `componentWillMount` to manage RAtoms.

## 0.8.1 (2018-05-15)

**[compare](https://github.com/reagent-project/reagent/compare/v0.8.0...v0.8.1)**

- Fix problem which caused using e.g. `:class` property with custom HTML element to break normal elements
- Fix problem using keyword or symbol as `:class` together with element tag class shorthand, e.g. `[:p.a {:class :b}]` ([#367](https://github.com/reagent-project/reagent/issues/367))
- Added support for using keywords and symbols in `:class` collection
- Removed component type assertion for `:>` ([#369](https://github.com/reagent-project/reagent/issues/369), [#372](https://github.com/reagent-project/reagent/pull/372))
  - This caused problems with React Context where component is Plain JS object with special properties
  - `React/createElement` will still provide error if `:>` is used with invalid values
- Handle exceptions from `not=` in Reagent `component-did-update` method ([#350](https://github.com/reagent-project/reagent/pull/350), [#344](https://github.com/reagent-project/reagent/pull/344))

## 0.8.0 (2018-04-19)

**[compare](https://github.com/reagent-project/reagent/compare/v0.8.0-rc1...v0.8.0)**

- Reagent documentation is now maintained as part of the repository, in [doc](./doc) folder.
- Default to React 16
- Apply vector metadata to the outermost element when using nesting shorthand ([#262](https://github.com/reagent-project/reagent/issues/262))
- Add `:<>` shorthand for [React Fragments](https://reactjs.org/docs/fragments.html) ([#352](https://github.com/reagent-project/reagent/pull/352)])
- Fix `:class` property with custom elements ([#322](https://github.com/reagent-project/reagent/issues/322))
- `:class` property now supports collections of strings ([#154](https://github.com/reagent-project/reagent/pull/154))
- Added `IWithMeta` to `RAtom` ([#314](https://github.com/reagent-project/reagent/pull/314))
- Support for using Reagent together with React from npm

#### Read [0.8 upgrade guide](./doc/0.8-upgrade.md) for more information.

## 0.8.0-rc1 (2018-04-11)

**[compare](https://github.com/reagent-project/reagent/compare/v0.8.0-alpha2...v0.8.0-rc1)**

Unless defaulting to React 16 causes problems, final release should follow soon.

- Reagent documentation is now maintained as part of the repository, in [doc](./doc) folder.
- Default to React 16
- Apply vector metadata to the outermost element when using nesting shorthand ([#262](https://github.com/reagent-project/reagent/issues/262))
- Add `:<>` shorthand for [React Fragments](https://reactjs.org/docs/fragments.html) ([#352](https://github.com/reagent-project/reagent/pull/352)])
- Fix `:class` property with custom elements ([#322](https://github.com/reagent-project/reagent/issues/322))
- Remove synthetic input support (added in previous alpha) ([#351](https://github.com/reagent-project/reagent/pull/351))

## 0.8.0-alpha2 (2017-10-20)

**[compare](https://github.com/reagent-project/reagent/compare/v0.8.0-alpha1...v0.8.0-alpha2)**

- Reagent still uses React 15 by default, but tests are now running and pass with React 16
    - [Error boundaries](https://reactjs.org/blog/2017/07/26/error-handling-in-react-16.html)
      are [supported](https://github.com/reagent-project/reagent/blob/master/test/reagenttest/testreagent.cljs#L1003-L1004)
      ([#272](https://github.com/reagent-project/reagent/issues/272))
    - Update Cljsjs dependencies or Node packages to use React 16
- `:class` property now supports collections of strings
  ([#154](https://github.com/reagent-project/reagent/pull/154))
- ~~Support for marking React components as `synthetic-input`, which will fix
  problems with cursor jumping around
  ([#282](https://github.com/reagent-project/reagent/pull/282))~~
    - This was reverted due to not working for all use cases ([#351](https://github.com/reagent-project/reagent/pull/351))
- Added `IWithMeta` to `RAtom` ([#314](https://github.com/reagent-project/reagent/pull/314))

## 0.8.0-alpha1 (2017-07-31)

**[compare](https://github.com/reagent-project/reagent/compare/v0.7.0...v0.8.0-alpha1)**

**BREAKING**: Requires ClojureScript version 1.9.854

This version changes how Reagent depends on React. New ClojureScript
improves support for [npm packages](https://clojurescript.org/news/2017-07-12-clojurescript-is-not-an-island-integrating-node-modules)
and also improves the way code can refer to objects from foreign-libs,
making the transition from foreign libs, like Cljsjs packages, to npm easy: [global exports](https://clojurescript.org/news/2017-07-30-global-exports)

Previously Reagent required foreign-lib namespace `cljsjs.react` and a few others.
This worked well when using Cljsjs React package, but in other environments,
like Node, React-native and when using npm packages, users had to
exclude Cljsjs packages and create empty files providing these `cljsjs.*` namespaces.

With global-exports, foreign-libs can be used like they were real namespaces:

```cljs
(ns ... (:require [react-dom :as react-dom]))

(react-dom/render ...)
```

The same code will in all the environments, and is compiled different based on
compile target and on how the dependency is provided. When targeting
browser and using foreign libs, ClojureScript compiler uses the `:global-exports`
definition to resolve the function from global JS var:

```js
var a = window.ReactDOM;
a.render(...)
```

When targeting browser but using node_modules with Closure module processing,
the CommonJS (or ES6) module is converted to a Closure module, named
by `module$` and the path of the file, and the generated code is same as
if this was a Cljs or Closure module:

```js
module$foo$bar$react$react-dom.render(...)
```

Then targeting NodeJS the object is retrieved using `require` call:

```js
var a = require("react-dom");
a.render(...)
```

This change requires use of ClojureScript 1.9.854, using the latest Cljsjs
React packages (15.6.1-1), and it is not yet sure how well other React
libraries work with these changes, or how this will work with React-native.
Currently it looks like all the Cljsjs React libraries need to be updated
to use require `react` instead of `cljsjs.react`, as the foreign-lib
namespace was renamed to match the npm package.

React-with-addons bundle [has been deprecated](https://reactjs.org/docs/addons.html) and Cljsjs no longer provides new versions
of that package. The latest React-with-addons version won't work with Reagent 0.8.
For animation utils use [react-transition-group](https://github.com/cljsjs/packages/tree/master/react-transition-group) package instead.
[React-dom/test-utils](https://reactjs.org/docs/test-utils.html) and
[react-addons-perf](https://reactjs.org/docs/perf.html) are not
currently packaged as browserified files, so their use would require Webpack,
or they might work with Closure module processing (TODO: Provide example).

#### Read [0.8 upgrade guide](./doc/0.8-upgrade.md) for more information.

#### Which libraries work together with Reagent 0.8:

| JS library type | Foreign library (Cljsjs) | Node module |
|---|---|---|
| Library updated to require `react`, `react-dom` names | Yes | Yes |
| Library requiring `cljsjs.react` and `cljsjs.react.dom` names | Yes | No |

Examples of libraries not yet updated: Devcards, Sablono. These will for now only work when using Cljsjs React.

## 0.7.0 (2017-06-27)

**[compare](https://github.com/reagent-project/reagent/compare/v0.6.2...v0.7.0)**

- Fixed a warning with recent ClojureScript (1.9.660+) versions about
a variadic method signature in `reagent/impl/util.cljs`.
    - `reagent.core/partial` and `wrap` used a bad deftype ([#303](https://github.com/reagent-project/reagent/pull/303))
- React updated to 15.5.4 ([#292](https://github.com/reagent-project/reagent/issues/292))
    - Uses [create-react-class](https://www.npmjs.com/package/create-react-class) instead of
    deprecated `React.createClass`
    - Reagent has now dependency on `cljsjs/create-react-class`, if you are using other
    methods to provide React, you need to exclude this Cljsjs dependency and provide the library yourself.
- Self-host compatibility ([#283](https://github.com/reagent-project/reagent/pull/283))
    - Removed deprecated `reagent.interop/.'` and `reagent.interop/.!` macros
- Improved assert messages all around ([#301](https://github.com/reagent-project/reagent/pull/301)).

## 0.6.2 (2017-05-19)

**[compare](https://github.com/reagent-project/reagent/compare/v0.6.1...v0.6.2)**

- React updated to 15.4.2
    - Fixes a problem with `number` inputs, ([#289](https://github.com/reagent-project/reagent/issues/289), [facebook/react#8717](https://github.com/facebook/react/issues/8717))

## 0.6.1 (2017-03-10)

**[compare](https://github.com/reagent-project/reagent/compare/v0.6.0...v0.6.1)**

- Fix :ref on inputs ([#259](https://github.com/reagent-project/reagent/issues/259))
- React updated to 15.4.0 ([#275](https://github.com/reagent-project/reagent/issues/275), [#276](https://github.com/reagent-project/reagent/issues/276))
- **BREAKING:** `reagent.core` no longer provides `render-to-string` or `render-to-static-markup` functions
    - `reagent.dom.server` includes the same functions
    - This is due to change in React packaging, including React-dom-server would increase the file size considerably, so now it is only included when `reagent.dom.server` is used

## 0.6.0

- React updated to 15.2.1

- Fix input on-change events in IE11


## 0.6.0-rc

- React updated to 15.1.0

- Symbols and keywords are now allowed in Hiccup content. They are converted using `name`.

- Any object hat satisfies IPrintWithWriter is also allowed, and is converted using `pr-str`.

- Bug fixes, improved error handling/reporting, and lots of testing.


## 0.6.0-alpha

### Breaking changes

- Reagent now depends on `cljsjs/react-dom` and `cljsjs/react-dom-server`, rather than on `cljsjs/react` directly.

- Reactions are now asynchronous, just like Reagent components. `flush` forces outstanding reactions to run.

- Reactions now only trigger updates of dependent components if their value change, as reported by `=` (previously, `identical?` was used).

- The macros `.'` and `.!` in `reagent.interop` have been renamed to `$` and `$!` respectively.


### News

- React updated to 0.14.3

- Added `reagent.dom` and `reagent.dom.server` namespaces, corresponding to new React packages.

- `create-class` now returns a normal React class, that can be used directly from javascript.

- Add `track`: turns a function call into a reactive value.

- Add `track!`: eager version of `track`.

- Add `dispose!`: stop the derefable returned by `track!` from updating.

- Add `with-let` macro: simpler handling of lifecycle in components and reactions.

- Add `rswap!`: works like `swap!`, except that recursive calls are allowed, and they always return nil.

- `cursor` now shares state between all instances corresponding to a given set of parameters.

- `next-tick` now evokes its argument function at a clearly defined time (immediately before rendering, which is in turn triggered using requestAnimationFrame).

- `after-update` is a new function, similar to `next-tick`, except that the function is evoked immediately after rendering.

- Support `[:> nativeComp {:foo "bar"}]`

- Reagent now falls back to using `require` if global `React` is undefined, to simplify use with e.g webpack and node.js.



## 0.5.1

- React updated to 0.13.3

- Deprecate calling the result of `create-class` as a function (i.e always use hiccup forms to call Reagent components).

- Hiccup syntax has been extended to allow nested elements to be defined using '>' as part of the keyword name.

- Add `force-update` for completeness.

- Try harder to maintain cursor position in inputs.

- Simplify examples, taking advantage of new figwheel.

- Better warnings and error messages.


## 0.5.0

- React updated to 0.12.2

- Reagent no longer bundles React. Instead it uses cljsjs/react as a dependency. This means that you should no longer specify React in `:preamble` in your project.clj.

- ClojureScript 0.0-2816 or later is required.

- `adapt-react-class` makes it easier to use "native" React components with Reagent.

- `reactify-component` makes it easier to use Reagent components in JSX.

- `cursor` is re-written, to be more efficient and flexible.

- `render` now forces a deep update of all components, to make it more convenient to use with e.g. figwheel.

- Renamed `as-component` to `as-element`, to match React's new terminology better (old name still works, though, for backward compatiblity).

- Stop wrapping native components. This reduces the number of components created a lot, and can speed up some things substantially (especially render-to-string, that is not bound by browser performance). This is made possible by a new way of keeping track of which order to re-render dirty components.

- Added `create-element` to make it easier to embed native React
components in Reagent ones.

- Arguments to components are now compared using simple `=`, instead of the old, rather complicated heuristics. **NOTE**: This means all arguments to a component function must be comparable with `=` (which means that they cannot be for example infinite `seq`s).

- Reagent now creates all React components using `React.createElement` (required for React 0.12).

- `render-component` is now `render`, and `render-component-to-string` is `render-to-string`, in order to match React 0.12 (but the old names still work).

- Add `render-to-static-markup`. This works exactly like `render-to-string`, except that it doesn't produce `data-react-id` etc.

- `create-class` now takes a Reagent-style render function (i.e with the same arguments you pass to the component), called `:reagent-render`.



## 0.4.3

- React updated to 0.11.2

- Add reagent.core/cursor

- Add javascript interop macros .' and .!

- Add force-update-all to make LightTable integration easier

- Some performance optimizations


## 0.4.2

- Allow multi-methods as component functions.

- Tweak performance by avoiding `clojure.core/memoize`.

- Bugfix: Allow on-change handler on controlled inputs to keep value unchanged.


## 0.4.1

- Made Reagent compatible with ClojureScript 0.0-2173. `reagent.core/atom` now implements the necessary IAtom, ISwap and IReset protocols. Reagent should still be compatible with older ClojureScript versions, but you will get a lot of compilation warnings.


## 0.4.0

- Breaking change: Component functions can get arbitrary arguments, and not just vectors and maps. This is a breaking change, but behaviour is unchanged if you pass a map as the first argument (as in all the examples in the old documentation).

- React updated to 0.9.0.

- You can now use any object that satisfies `ifn?` as a component function, and not just plain functions. That includes functions defined with deftype, defrecord, etc, as well as collections like maps.

- `reagent.core/set-state` and `reagent.core/replace-state` are now implemented using a `reagent.core/atom`, and are consequently async.

- Keys associated with items in a seq (e.g ”dynamic children” in React parlance) can now be specified with meta-data, as well as with a `:key` item in the first parameter as before. In other words, these two forms are now equivalent: `^{:key foo} [:li bar]` and `[:li {:key foo} bar]`.

- Performance has been improved. For example, there is now practically no overhead for tracking derefs in components that don’t use atoms. Allocations and memory use have also been reduced.

- Intro and examples have been tweaked a little to take advantage of the new calling conventions.


## 0.3.0

- Changes in application state are now rendered asynchronously, using requestAnimationFrame.

- Reagent now does proper batching of updates corresponding to changed atoms, i.e parents are rendered before children, and children are only re-rendered once.

- Add `reagent.core/flush` to render changes immediately.

- Bugfix: Allow dynamic id with hiccup-style class names.


## 0.2.1

- Bugfix: allow data-* and aria-* attributes to be passed through unchanged.


## 0.2.0

- Rename Cloact to Reagent, due to popular disgust with the old name...
