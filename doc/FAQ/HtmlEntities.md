# Using HTML entities

React will escape HTML entities (like `&nbsp;`, `&times;`) in the text elements.

You could just use literal character: `×` or unicode code, which is converted to
the character by Cljs compiler: `\u00D7`.

HTML entities work in React JSX because JSX will unescape the entity code to
literal character.

You can do the same in ClojureScript by using `goog.string/unescapeEntities`:

```cljs
(ns example
  (:require [goog.string :as gstr]))

(defn comp []
  [:h1 "Foo" (gstr/unescapeEntities "&times;")])
```

Note: Yes, this can be inconvenient, but Reagent can't do this automatically as
finding and replacing entities during runtime would be slow.
