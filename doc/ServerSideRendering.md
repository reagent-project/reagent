# Server Side Rendering

React itself supports rendering React elements in server-side into
HTML markup: [Server React DOM APIs](https://react.dev/reference/react-dom/server).
This means the code should be running in Node.js or other JS environment
supported by React.

Reagent can be used with these React functions on Node.js to render the Reagent
code into HTML. Reagent homepage and demo site uses [this approach](../prerender/sitetools/prerender.cljs).
Reagent doesn't currently provide helpers or an example on how to do this.

## Rendering on JVM

*Currently* rendering Reagent components on the JVM is out of scope for the project.

It would be possible to to render the hiccup-style markup with just Clojure
code without using React, but the approach has big limitations:

- Any JS libraries (like Material-UI) would be unavailable
- Using React.js features (like hooks) wouldn't work, and would require a stub implementation (this is what React.js itself does)

[Discussion](https://github.com/reagent-project/reagent/discussions/625)
