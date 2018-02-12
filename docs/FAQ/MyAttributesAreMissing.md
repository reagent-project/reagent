### Question

Why isn't my attribute `xyz` showing up on <some-element>?  (where is `xyz` is something like `autoFocus`)

### Answer

With Reagent, you use `auto-focus`, not `autoFocus`. As a rule, you take a camelCased HTML attribute 
(recognised by React) and you put a dash before the Capital, and then don't have capital. So it becomes `camel-cased`. 

And then there are a couple of gotchas like `colspan`.  React recognises `colSpan`, so in Reagent 
you must use the rule above and provide it as `col-span`. 

***

Up:  [FAQ Index](README.md)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
