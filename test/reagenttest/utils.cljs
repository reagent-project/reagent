(ns reagenttest.utils
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.impl.template :as tmpl]))

(defn with-mounted-component
  ([comp f]
   (with-mounted-component comp tmpl/default-compiler f))
  ([comp compiler f]
   (when r/is-client
     (let [div (.createElement js/document "div")]
       (try
         (let [c (if compiler
                   (rdom/render comp div compiler)
                   (rdom/render comp div))]
           (f c div))
         (finally
           (rdom/unmount-component-at-node div)
           (r/flush)))))))

(defn with-mounted-component-async
  ([comp done f]
   (with-mounted-component-async comp done nil f))
  ([comp done compiler f]
   (when r/is-client
     (let [div (.createElement js/document "div")
           c (if compiler
               (rdom/render comp div compiler)
               (rdom/render comp div))]
       (f c div (fn []
                  (rdom/unmount-component-at-node div)
                  (r/flush)
                  (done)))))))

(defn run-fns-after-render [& fs]
  ((reduce (fn [cb f]
             (fn []
               (r/after-render (fn []
                                 (f)
                                 (cb)))))
           (reverse fs))))
