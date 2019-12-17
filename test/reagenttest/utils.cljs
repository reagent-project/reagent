(ns reagenttest.utils
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

(defn with-mounted-component [comp f]
  (when r/is-client
    (let [div (.createElement js/document "div")]
      (try
        (let [c (rdom/render comp div)]
          (f c div))
        (finally
          (rdom/unmount-component-at-node div)
          (r/flush))))))

(defn with-mounted-component-async [comp done f]
  (when r/is-client
    (let [div (.createElement js/document "div")
          c (rdom/render comp div)]
      (f c div (fn []
                 (rdom/unmount-component-at-node div)
                 (r/flush)
                 (done))))))

(defn run-fns-after-render [& fs]
  ((reduce (fn [cb f]
             (fn []
               (r/after-render (fn []
                                 (f)
                                 (cb)))))
           (reverse fs))))
