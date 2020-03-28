(ns reagent.impl.template-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [reagent.impl.template :as tmpl]
            [goog.object :as gobj]))

(deftest cached-prop-name
  (is (= "className"
         (tmpl/cached-prop-name :class))))

(deftest cached-custom-prop-name
  (is (= "class"
         (tmpl/cached-custom-prop-name :class))))

;; Cljs.test prints better error if check is Cljs function
(defn js-equal? [a b]
  (gobj/equals a b))

(deftest convert-props-test
  (is (js-equal? #js {:className "a"}
                 (tmpl/convert-props {:class "a"} (tmpl/HiccupTag. nil nil nil false))))
  (is (js-equal? #js {:class "a"}
                 (tmpl/convert-props {:class "a"} (tmpl/HiccupTag. nil nil nil true))))
  (is (js-equal? #js {:className "a b" :id "a"}
                 (tmpl/convert-props {:class "b"} (tmpl/HiccupTag. nil "a" "a" false)))))
