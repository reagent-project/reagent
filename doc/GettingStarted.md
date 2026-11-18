---
reviewed: 2026-11-18
---

# Getting Started

This guide creates a minimal single-page application with Reagent, using
[shadow-cljs](https://github.com/thheller/shadow-cljs) to compile
ClojureScript and npm to provide React.

You need:

- [Node.js](https://nodejs.org/) (includes npm)
- Java 21 or newer, which the ClojureScript compiler runs on

## Project files

Create a new directory with the following files:

```
my-app/
├── package.json
├── shadow-cljs.edn
├── public/
│   └── index.html
└── src/
    └── my_app/
        └── core.cljs
```

### `package.json`

React is provided from npm. shadow-cljs is installed as a dev dependency so
that `npx shadow-cljs` uses the version of this project.

```json
{
  "name": "my-app",
  "private": true,
  "scripts": {
    "watch": "shadow-cljs watch app",
    "release": "shadow-cljs release app"
  },
  "dependencies": {
    "react": "19.3.1",
    "react-dom": "19.3.1"
  },
  "devDependencies": {
    "shadow-cljs": "3.2.1"
  }
}
```

### `shadow-cljs.edn`

The ClojureScript dependencies, including Reagent, are listed here. The `:app`
build compiles `src` into `public/js/main.js`, and `:dev-http` serves the
`public` directory at <http://localhost:8080>.

```clojure
{:source-paths ["src"]

 :dependencies [[reagent "2.0.1"]]

 :dev-http {8080 "public"}

 :builds
 {:app {:target :browser
        :output-dir "public/js"
        :asset-path "/js"
        :modules {:main {:init-fn my-app.core/init}}}}}
```

### `public/index.html`

```html
<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8">
    <title>My App</title>
  </head>
  <body>
    <div id="app"></div>
    <script src="/js/main.js"></script>
  </body>
</html>
```

### `src/my_app/core.cljs`

Note that the namespace `my-app.core` lives in the file `my_app/core.cljs`:
dashes in namespace names become underscores in file names.

```clojure
(ns my-app.core
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdomc]))

;; Application state: a Reagent atom. Components that deref it
;; re-render automatically when it changes.
(defonce click-count (r/atom 0))

(defn counter []
  [:div
   [:p "The button has been clicked " @click-count " times."]
   [:button {:on-click #(swap! click-count inc)}
    "Click me"]])

(defn app []
  [:div
   [:h1 "Hello, Reagent!"]
   [counter]])

;; React root, created once and reused when the code is reloaded.
(defonce root (rdomc/create-root (js/document.getElementById "app")))

(defn ^:dev/after-load mount []
  (rdomc/render root [app]))

(defn init []
  (mount))
```

Components are plain functions that return [Hiccup](UsingHiccupToDescribeHTML.md)
— vectors describing HTML elements and other components. `[counter]` uses the
`counter` component, like `<Counter />` in JSX.

`init` is called once when the page loads (`:init-fn` in `shadow-cljs.edn`).
The `^:dev/after-load` metadata makes shadow-cljs call `mount` again after
each code change during development. `defonce` keeps the state and the React
root across reloads, so the counter value survives code changes.

## Running

Install the npm dependencies and start the development build:

```bash
npm install
npx shadow-cljs watch app
```

The first run downloads the ClojureScript dependencies, which takes a while.
Once the build is completed, open <http://localhost:8080>. Edit `core.cljs`
and save, and the page updates without a reload.

The watch process also starts an nREPL server (port is written to
`.shadow-cljs/nrepl.port`) which you can connect to from your editor. See
the [shadow-cljs User's Guide](https://shadow-cljs.github.io/docs/UsersGuide.html)
for editor integration.

## Building for production

```bash
npx shadow-cljs release app
```

This writes an optimized `public/js/main.js`. Deploy the contents of `public`
to any static file host.

## Other starting points

[reagent-template](https://github.com/reagent-project/reagent-template)
creates a larger [Leiningen](https://leiningen.org/) project: the same
shadow-cljs and React 19 setup, plus a Clojure (Ring and Jetty) web server,
[Reitit](https://metosin.github.io/reitit/) routing on the server and client,
and an uberjar build. It requires JDK 21+ and Node.js.

```bash
lein new reagent my-app
```

Add `+test` for cljs.test with browser and Karma test runners, `+cider` for
CIDER middleware, or `+less` / `+sass` for CSS compilation. See the
[template README](https://github.com/reagent-project/reagent-template#usage)
for details.

## Where to go next

- [Using Hiccup to Describe HTML](UsingHiccupToDescribeHTML.md)
- [Creating Reagent Components](CreatingReagentComponents.md)
- [Managing State: atoms, cursors, Reactions, and tracking](ManagingState.md)
- [Interop with React](InteropWithReact.md) — using React components from npm
  in Reagent, and [React Features](ReactFeatures.md) such as hooks
- The [documentation index](README.md) lists all tutorials and FAQs
- [API documentation](https://cljdoc.org/d/reagent/reagent/CURRENT)
