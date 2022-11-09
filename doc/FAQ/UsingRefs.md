### Question

When using Reagent, how do I use React's `refs`?

### Answer

Credit: this entry is entirely based on Paulus Esterhazy's [Reagent Mysteries series](https://presumably.de/reagent-mysteries-part-3-manipulating-the-dom.html)

We'll start with a code fragment, because it is worth a 1000 words:

```cljs
(defn video-ui []
  (let [!video (clojure.core/atom nil)]    ;; stores the
    (fn [{:keys [src]}]
      [:div
       [:div
        [:video {:src src
                 :style {:width 400}
                 :ref (fn [el]
                        (reset! !video el))}]]
       [:div
        [:button {:on-click (fn []
                              (when-let [video @!video] ;; not nil?
                                (if (.-paused video)
                                  (.play video)
                                  (.pause video))))}
         "Toogle"]]])))
```

Notes:
   1. This example uses a Form-2 component, which allows us to retain state outside of the renderer `fn`.  The same technique would work with a Form-3 component.
   2. We capture state in `!video`. In this example, the state we capture is a reference to a [video HTML element](https://developer.mozilla.org/en-US/docs/Web/HTML/Element/video).
   3. `!video` is a `clojure.core/atom` and not a `reagent.core/atom`.  We use a normal Clojure `atom` because refs never change during the lifecycle of a component and if we used a reagent atom, it would cause an unnecessary re-render when the ref callback mutates the atom.
   4. On the `:video` component there's a `:ref` callback function which establishes the state in `!video`.  You can attach a ref callback to any of the Hiccup elements.
   5. Thereafter, `@!video` is used with the `:button's` `:on-click` to manipulate the `video` DOM methods.
   6. For full notes [read Paulus' blog post](https://presumably.de/reagent-mysteries-part-3-manipulating-the-dom.html)
   7. For more background on callback refs, see [React's documentation](https://reactjs.org/docs/refs-and-the-dom.html)

***

Up:  [FAQ Index](../README.md)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
