(ns reagenttest.utils
  (:require [reagent.core :as r]))

(defn with-mounted-component [comp f]
  (when r/is-client
    (let [div (.createElement js/document "div")]
      (try
        (let [c (r/render comp div)]
          (f c div))
        (finally
          (r/unmount-component-at-node div)
          (r/flush))))))

(defn with-mounted-component-async [comp done f]
  (when r/is-client
    (let [div (.createElement js/document "div")
          c (r/render comp div)]
      (f c div (fn []
                 (r/unmount-component-at-node div)
                 (r/flush)
                 (done))))))

(defn run-fns-after-render [& fs]
  ((reduce (fn [cb f]
             (fn []
               (r/after-render (fn []
                                 (f)
                                 (cb)))))
           (reverse fs))))

(defn found-in [re div]
  (let [res (.-innerHTML div)]
    (if (re-find re res)
      true
      (do (println "Not found: " res)
          false))))

