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

(deftest convert-props-test
  (is (gobj/equals #js {:className "a"}
                   (tmpl/convert-props {:class "a"} #js {:id nil :custom false})))
  (is (gobj/equals #js {:class "a"}
                   (tmpl/convert-props {:class "a"} #js {:id nil :custom true})))
  (is (gobj/equals #js {:className "a b" :id "a"}
                   (tmpl/convert-props {:class "b"} #js {:id "a" :className "a" :custom false}))))
