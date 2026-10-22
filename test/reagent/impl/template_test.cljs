(ns reagent.impl.template-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [reagent.core :as r]
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

(r/defc fn-component []
  [:div])

(defn regular-comp [])

(def adapted
  (r/adapt-react-class "div"))

(deftest valid-tag?-test
  (testing "hiccup like tag"
    (is (true? (tmpl/valid-tag? :div)))
    (is (true? (tmpl/valid-tag? "div"))))

  (testing "reagent fn component"
    (is (true? (tmpl/valid-tag? fn-component))))

  (testing "regular component"
    (is (true? (tmpl/valid-tag? regular-comp)))
    (testing "anything ifn like"
      ;; Does this make sense?
      ;; Probably can't be changed.
      (is (true? (tmpl/valid-tag? {})))
      (is (true? (tmpl/valid-tag? [])))))

  (testing "adapted react component"
    (is (true? (tmpl/valid-tag? adapted))))

  (testing "anything else"
    (is (false? (tmpl/valid-tag? nil)))
    (is (false? (tmpl/valid-tag? #js {})))
    (is (false? (tmpl/valid-tag? #js [])))
    (is (false? (tmpl/valid-tag? '())))))
