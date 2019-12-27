### Question

How can I use an entity like "nbsp"?

### Answer

If you try to do this:
```clj
[:div "hello" "&nbsp;" "there"]     ;; <--- note: attempt to use an entity
```
then you will see the string for the entity. Which is not what you want. 

Instead you should do this:

  1. Require in goog's string module...

  ```clj
  (:require [goog.string :as gstring])
  ```

  2. Use it like this ... 

  ```clj
   [:div "hello" (gstring/unescapeEntities "&nbsp;") "there"]
  ```

**Note:** `unescapeEntities` relies on the DOM to produce a string with unescape entities;
in `nodejs` the DOM is unlikely to be available (unless you try using
[`jsdom`](https://www.npmjs.com/package/jsdom-global)).

***

Up:  [FAQ Index](../README.md)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
