# Security and Reagent

## Hiccup Data

Reagent uses Clojure data structures to represent the React elements that will
be created and mounted on the page.

In most cases, React does its best to avoid code injection attacks by making it
difficult to accidentally create elements that execute JavaScript code. For
example, `<script>` element children’s JavaScript code text usually isn't executed
(TODO: Does this change in React 19?), and `<img>` `onLoad` and other similar callbacks
must be provided as functions, not strings of JavaScript code.

When using React with JSX, JSX can differentiate between React elements
previously created with JSX and other JavaScript objects. In JavaScript
applications, it shouldn't be possible to represent React elements in API
responses.

```js
<h1>{response.title}</h1>
```

Because Reagent deals directly with Clojure data structures, it is perfectly
valid to build a value somewhere that contains Hiccup, which Reagent will then
turn into React elements. Unfortunately, this also means it is possible to
construct these Hiccup values **IF** your API or data source allows representing
keywords—such as EDN and Transit data.

```cljs
;; Regular use
(let [h [:h1 "Hello"]]
  [:div h [:p "world"]])

;; Data from an API
(def title (r/atom nil))

;; Some code that retrieves an EDN/Transit value from an external API or localStorage
;; and stores the decoded value into the atom:
(reset! title [:div {:dangerouslySetInnerHTML {:__html "<img src=\"err\" onError=\"alert('danger')\"/>"}}])

[:div [:h1 @title]]

;; One safe approach
[:div [:h1 (str @title)]]
```

This does **not** mean users can write these data structures in form inputs
and have those values processed as Hiccup. When you receive a value from a text
field, it is stored as a string in a database and returned as such to the
frontend.

**However**, if another security vulnerability exists—such as a man-in-the-
middle (MITM) attack that modifies the data your API responds with—your Reagent
frontend could process certain values as Hiccup. This could allow external
data sources to return values that get executed in the user's browser.

### Cases Where This Is a Problem

- MITM attack modifying API responses
- Malicious modification of data stored and read from `localStorage` or similar
  sources

All these scenarios require that the data is parsed using EDN, Transit, or
other formats that allow the representation of Hiccup forms (i.e., vectors and
keywords).

If such vulnerabilities exist, they may indicate broader security concerns
beyond just Reagent’s interpretation of data structures as Hiccup.

Many other ClojureScript React libraries aren't vulnerable to this because they
either process the Hiccup data at macro compilation time ([Sablono](https://github.com/r0man/sablono),
[Hicada](https://github.com/rauhs/hicada))
or do not use Hiccup ([UIx](https://github.com/pitch-io/uix), [Helix](https://github.com/lilactown/helix)).

### Workarounds

- **Reagent 1.3.0+**: `:dangerouslySetInnerHTML` values must be created using `reagent.core/unsafe-html`
  function which tags them using a type, that can't be used accidentally from Transit and EDN
    - Other attributes, like `:style` can still be potentially dangerous, as there are
      many attributes that can be used to control what the page looks like.
    - (Representing these values in Transit or EDN would require custom tag and reader configuration.)
- Wrap data from external sources in `str` calls.
- Use JSON format in APIs, it isn't usually possible to represent Hiccup forms in JSON.
- Validate API responses against schema

### Why a Generic Fix Is Not Possible

Reagent can't distinguish between `[:div [:p]]` and `(let [x [:p]] [:div x])`
because the Hiccup interpretation happens at runtime. In both cases,
Reagent sees exactly the same data structure.

Libraries that handle Hiccup at macro compilation time would only see
the `x` symbol in the latter case and could handle it differently.
