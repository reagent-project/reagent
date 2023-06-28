(ns example.compiler
  (:require ["@emotion/react" :as emotion]
            [goog.object :as obj]
            [reagent.impl.protocols :as p]
            [reagent.impl.template :as t]))

(defn css-array-value [clj-css]
  (cond
    (map? clj-css)
    (t/convert-prop-value clj-css)

    (fn? clj-css)
    (fn [t] (t/convert-prop-value (clj-css t)))

    :else
    clj-css))

(defn cssfn [jsprops clj-css]
  (if-let [css (obj/get jsprops "css")]
    (cond
      (array? css)
      (do
        (obj/set jsprops "css" (reduce (fn [a v]
                                         (.push a (css-array-value v))
                                         a) #js[] clj-css))
        jsprops)

      (fn? css)
      (do (obj/set jsprops "css" (fn [t] (t/convert-prop-value (css t))))
          jsprops)

      :else jsprops)
    jsprops))

(defn make-element
  "Similar to t/make-element, but using emotion/jsx instead of react/createElement.
  Uses cssfn to automatically convert to js from :css that are functions (using theme)."
  [this [_ {:keys [css]} :as argv] component jsprops first-child]
  (let [jsprops (cssfn jsprops css)]
    (case (- (count argv) first-child)
      0 (emotion/jsx component jsprops)

      1 (emotion/jsx component jsprops
          (p/as-element this (nth argv first-child nil)))

      (.apply emotion/jsx nil
        (reduce-kv (fn [a k v]
                     (when (>= k first-child)
                       (.push a (p/as-element this v)))
                     a)
          #js [component jsprops] argv)))))

(defn emotion-compiler [opts]
  (let [id (gensym "reagent-compiler")
        fn-to-element (if (:function-components opts)
                        t/maybe-function-element
                        t/reag-element)
        parse-fn (get opts :parse-tag t/cached-parse)]
    (reify p/Compiler
      (get-id [_this] id)
      (parse-tag [this tag-name tag-value]
        (parse-fn this tag-name tag-value))
      (as-element [this x]
        (t/as-element this x fn-to-element))
      (make-element [this argv component jsprops first-child]
        (make-element this argv component jsprops first-child)))))
