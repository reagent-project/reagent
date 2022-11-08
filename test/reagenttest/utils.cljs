(ns reagenttest.utils
  (:require ["react" :as react]
            ["react-dom/test-utils" :as react-test]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.impl.template :as tmpl]))

;; Seems to be similar to what React is doing:
;; https://github.com/facebook/react/blob/v18.2.0/packages/react-devtools-shared/src/__tests__/setupTests.js#L93
(defonce original-console-error (.-error js/console))

(set! (.-error js/console)
      (fn [& [first-arg :as args]]
        (cond
          (.startsWith first-arg "Warning: The current testing environment is not configured to support act")
          nil

          :else
          (apply original-console-error args))))

(defn act
  "Run f to trigger Reagent updates,
  will return Promise which will resolve after
  Reagent and React render."
  [f]
  ;; async act doesn't return a real promise (with chainable then),
  ;; so wrap it.
  (js/Promise.
    (fn [resolve reject]
      (.then (react-test/act
               (fn reagent-act-callback []
                 (f)
                 ;; React act callback should return something "thenable" to use
                 ;; async act.
                 (js/Promise. (fn [resolve _reject]
                                (r/after-render (fn reagent-act-after-reagent-flush []
                                                  (resolve)))))))
             resolve
             reject))))

(defn with-mounted-component
  ([comp f]
   (with-mounted-component comp tmpl/default-compiler f))
  ([comp compiler f]
   (when r/is-client
     (let [div (.createElement js/document "div")]
       (try
         (let [f #(f div)
               ;; TODO: Make render return the root.
               _root (if compiler
                       (rdom/render comp div {:compiler compiler
                                              :callback f})
                       (rdom/render comp div f))]
           nil)
         (finally
           (rdom/unmount-component-at-node div)
           (r/flush)))))))

(defn with-mounted-component-async
  ([comp done f]
   (with-mounted-component-async comp done nil f))
  ([comp done compiler f]
   (when r/is-client
     (let [div (.createElement js/document "div")
           f #(f div (fn []
                       (rdom/unmount-component-at-node div)
                       (r/flush)
                       (done)))
           _root (if compiler
                   (rdom/render comp div {:compiler compiler
                                          :callback f})
                   (rdom/render comp div f))]
       nil))))

(defn run-fns-after-render [& fs]
  ((reduce (fn [cb f]
             (fn []
               (r/after-render (fn []
                                 (f)
                                 (cb)))))
           (reverse fs))))
