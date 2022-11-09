(ns reagenttest.utils
  (:require-macros reagenttest.utils)
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.dom.server :as server]
            [reagent.impl.template :as tmpl]))

;; The code from deftest macro will refer to these
(def class-compiler tmpl/class-compiler)
(def fn-compiler (r/create-compiler {:function-components true}))

(def ^:dynamic *test-compiler* nil)
(def ^:dynamic *test-compiler-name* nil)

(defn as-string [comp]
  (server/render-to-static-markup comp *test-compiler*))

(defn with-mounted-component
  ([comp f]
   (with-mounted-component comp *test-compiler* f))
  ([comp compiler f]
   (let [div (.createElement js/document "div")]
     (try
       (let [c (if compiler
                 (rdom/render comp div compiler)
                 (rdom/render comp div))]
         (f c div))
       (finally
         (rdom/unmount-component-at-node div)
         (r/flush))))))

(defn with-mounted-component-async
  [comp done compiler f]
  (let [div (.createElement js/document "div")
        c (if compiler
            (rdom/render comp div compiler)
            (rdom/render comp div))]
    (f c div (fn []
               (rdom/unmount-component-at-node div)
               (r/flush)
               (done)))))

(defn run-fns-after-render [& fs]
  ((reduce (fn [cb f]
             (fn []
               (r/after-render (fn []
                                 (f)
                                 (cb)))))
           (reverse fs))))
