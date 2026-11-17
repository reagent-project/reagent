(ns reagenttest.testcompiler
  "Custom `p/Compiler` implementation, as shown in doc/ReagentCompiler.md."
  (:require [clojure.test :as t :refer-macros [is deftest testing]]
            [react :as react]
            [reagent.core :as r]
            [reagent.dom.server :as server]
            [reagent.impl.component :as comp]
            [reagent.impl.protocols :as p]
            [reagent.impl.template :as tmpl]))

;; Same code as in the "Custom compiler" section of doc/ReagentCompiler.md

(defn make-element
  "Like `reagent.impl.template/make-element`, but calls `create-element`
  instead of `react/createElement`."
  [create-element compiler argv component jsprops first-child]
  (apply create-element component jsprops
         (map #(p/as-element compiler %) (drop first-child argv))))

(defn create-compiler
  "Like `reagent.core/create-compiler`, but creates the elements with
  `create-element`."
  [opts create-element]
  (let [id (gensym "custom-compiler")
        fn-to-element (if (:function-components opts)
                        tmpl/maybe-function-element
                        tmpl/reag-element)
        parse-fn (get opts :parse-tag tmpl/cached-parse)]
    (reify p/Compiler
      (get-id [_this] id)
      (parse-tag [this tag-name tag-value]
        (parse-fn this tag-name tag-value))
      (as-element [this x]
        (tmpl/as-element this x fn-to-element))
      (make-element [this argv component jsprops first-child]
        (make-element create-element this argv component jsprops first-child))
      (reactify-component [this c]
        (if (:function-components opts)
          (comp/functional-reactify-component this c)
          (comp/as-class c this))))))

;; Tests

(defn title-create-element
  "Like `react/createElement`, but adds a `title` to the HTML elements."
  [type props & children]
  (when (string? type)
    (set! (.-title props) (str "custom " type)))
  (apply react/createElement type props children))

(deftest custom-compiler-test
  (doseq [opts [{} {:function-components true}]]
    (testing (pr-str opts)
      (let [compiler (create-compiler opts title-create-element)
            child (fn [x] [:span.child x])
            js-comp (r/reactify-component (fn [props] [:i (:x props)]) compiler)]
        (testing "HTML elements"
          (is (= "<div title=\"custom div\"></div>"
                 (server/render-to-static-markup [:div] compiler)))
          (is (= "<div title=\"custom div\">foo</div>"
                 (server/render-to-static-markup [:div "foo"] compiler)))
          (is (= "<div id=\"a\" title=\"custom div\"><b title=\"custom b\">foo</b>bar</div>"
                 (server/render-to-static-markup [:div {:id "a"} [:b "foo"] "bar"] compiler))))

        (testing "inputs"
          (is (= "<input title=\"custom input\" value=\"x\"/>"
                 (server/render-to-static-markup [:input {:value "x" :on-change identity}] compiler))))

        (testing "Reagent components and their children"
          (is (= "<div title=\"custom div\"><span class=\"child\" title=\"custom span\">foo</span></div>"
                 (server/render-to-static-markup [:div [child "foo"]] compiler)))
          (is (= "<ul title=\"custom ul\"><li title=\"custom li\">1</li><li title=\"custom li\">2</li></ul>"
                 (server/render-to-static-markup
                  [:ul (for [i [1 2]] ^{:key i} [:li i])]
                  compiler))))

        (testing "fragments and :>"
          (is (= "<b title=\"custom b\">a</b><i title=\"custom i\">b</i>"
                 (server/render-to-static-markup [:<> [:b "a"] [:i "b"]] compiler)))
          (is (= "<i title=\"custom i\">foo</i>"
                 (server/render-to-static-markup [:> js-comp {:x "foo"}] compiler))))

        (testing "r/as-element"
          (is (= "custom p"
                 (.. (r/as-element [:p] compiler) -props -title))))))))
