# Forcing Component re-creation?

## React key with a stateful component

React key can be used to provide identity for components even outside of lists.
If the key changes, the component is recreated triggering all the implemented
stateful methods (did-unmount, did-mount etc.):

```clj
(defn comp []
  [:div
   ^{:key dynamic-id}
   [stateful-component]])
```
