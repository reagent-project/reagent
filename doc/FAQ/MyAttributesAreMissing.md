# Why is my attribute (like `autoFocus`) missing?

Why isn't my attribute `xyz` showing up on `<some-element>`?

You might be spelling it incorrectly. 

React supports [camelCased HTML attributes](https://reactjs.org/docs/dom-elements.html#all-supported-html-attributes),
but the equivalent in Reagent is dashed and lower cased. 

For example, with Reagent, you use `auto-focus`, instead of `autoFocus`. And 
you use `col-span` instead of React's `colSpan`.
