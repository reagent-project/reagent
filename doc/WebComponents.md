# Web Components (Custom Elements)

Custom elements work in Reagent like any other HTML element: use the
element name as the Hiccup tag keyword. Nothing needs to be registered on
the Reagent side, Reagent passes the tag name to React as a string and the
browser handles the rest.

```clojure
[:my-element {:class "fancy"} "Hello"]
```

Reagent treats every tag that contains a `-` as a custom element (custom
element names [must contain a hyphen](https://html.spec.whatwg.org/multipage/custom-elements.html#valid-custom-element-name)).
Props for custom elements are converted slightly differently from props for
built-in elements, and React 19 decides whether each prop is set as an
attribute, a property or an event listener. The sections below describe how.

The property and event handling described below requires React 19. React 18
sets all custom element props as attributes and doesn't add listeners for
custom events.

## `:class`, `:id` and the tag shorthand

`:class` works the same as with other elements, including the `.class` and `#id`
tag shorthand and collections (see [Using Hiccup to Describe
HTML](UsingHiccupToDescribeHTML.md#special-interpretation-of-class-attribute)).
The prop is passed to React as `class` (not `className`), which React sets as
the `class` attribute:

```clojure
[:my-element.a {:class ["b" "c"]}]
;; => <my-element class="a b c"></my-element>
```

## Prop names

Keyword prop names are converted from kebab-case to camelCase, like with other
elements, except for `data-` and `aria-` props. String keys are passed as is:

```clojure
[:my-element {:my-attr "a"
              "my-attr" "b"
              :data-foo-bar "c"}]
;; => <my-element myAttr="a" my-attr="b" data-foo-bar="c"></my-element>
```

HTML attribute names are case-insensitive, so in the browser `myAttr` becomes
the attribute `myattr`. **Use a string key for dashed attribute names**,
like `"my-attr"` above.

## Attributes vs. properties (React 19)

For each prop that isn't an event handler, React 19 checks if the element has a
property with that name (`name in element`):

- If it does, the value is set as a JS property. This is how you pass
  non-string data, like arrays and objects, to a web component.
- Otherwise the value is set as an attribute. `true` sets an empty attribute,
  `false` leaves it out.

```clojure
[:my-list {:items ["a" "b"] ;; element has `items` property => el.items = ["a", "b"]
           :compact true}]  ;; no such property => attribute compact=""
```

Prop values are converted to JS like for other elements: vectors to arrays,
maps to objects, keywords to strings. A JS value (e.g. `#js {...}`) is passed as
is.

The property check only works if the custom element is already defined
(`customElements.define`) when React creates the DOM element. If the element is
defined later, all props are set as attributes.

## Events

Prop names that are known React events, like `:on-click`, work as usual and
receive React synthetic events.

For other props whose name starts with `on` and whose value is a function,
React 19 adds an event listener (`addEventListener`) for the event named by the
rest of the prop name, **case-sensitively**. The handler receives the native
event, e.g. a `CustomEvent` with `.-detail`.

Most custom events use lowercase or dashed names, so use a string key:

```clojure
[:my-element {"onmy-event" (fn [e] (js/console.log (.-detail e)))}]
;; listens to "my-event"
```

A keyword like `:on-my-event` is first converted to `onMyEvent`, which listens
to the event `MyEvent`, not `my-event`.

## Server-side rendering

When rendering to a string, props are rendered as attributes with the same name
conversion as above, so `:my-attr` renders as `myAttr="..."`. Use string keys
when the attribute name matters.

## Rendering Reagent inside a custom element

Reagent components can also be used to implement custom elements, by creating
a React root inside the element (e.g. in its shadow root) and rendering into
it with `reagent.dom.client/render`. See the
[shadow-dom-input example](https://github.com/reagent-project/reagent/tree/master/examples/shadow-dom-input).
