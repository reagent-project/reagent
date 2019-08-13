(ns reagent.impl.util-test
  (:require [clojure.test :refer [deftest is testing]]
            [reagent.impl.util :as util]))

(deftest class-names-test
  (is (= nil
         (util/class-names)
         (util/class-names nil)
         (util/class-names [])
         (util/class-names nil [])))
  (is (= "a b"
         (util/class-names ["a" "b"])
         (util/class-names "a" "b")))
  (is (= "a"
         (util/class-names :a)
         (util/class-names [:a])
         (util/class-names nil "a")
         (util/class-names [] nil "a")))
  (is (= "a b c d"
         (util/class-names "a" "b" nil ["c" "d"]))))

(deftest dash-to-prop-name-test
  (is (= "tabIndex" (util/dash-to-prop-name :tab-index)))
  (is (= "data-foo-bar" (util/dash-to-prop-name :data-foo-bar))))

(deftest dash-to-method-name-test
  (is (= "componentDidMount"
         (util/dash-to-method-name :component-did-mount)))
  (is (= "componentDidMount"
         (util/dash-to-method-name :componentDidMount)))
  (is (= "UNSAFE_componentDidMount"
         (util/dash-to-method-name :unsafe-component-did-mount)))
  (is (= "UNSAFE_componentDidMount"
         (util/dash-to-method-name :unsafe_componentDidMount))))

; (simple-benchmark []
;                   (do (util/class-names "a" "b")
;                       (util/class-names nil "a")
;                       (util/class-names "a" nil))
;                   10000)

; (simple-benchmark []
;                   (util/class-names "a" "b" nil "c" "d")
;                   10000)

(deftest merge-props-test
  (testing "no arguments"
    (is (nil? (util/merge-props))))

  (testing "one argument"
    (is (nil? (util/merge-props nil)))
    (is (= {:foo :bar} (util/merge-props {:foo :bar}))))

  (testing "two arguments"
    (is (= {:disabled false :style {:flex 1 :flex-direction "row"} :class "foo bar"}
           (util/merge-props {:disabled true :style {:flex 1} :class "foo"}
                             {:disabled false :style {:flex-direction "row"} :class "bar"}))))

  (testing "n arguments"
    (is (= {:disabled false
            :checked true
            :style {:align-items "flex-end"
                    :justify-content "center"}
            :class "foo bar baz quux"}
           (util/merge-props {:disabled false
                              :checked false
                              :style {:align-items "flex-end"}
                              :class "foo"}
                             {:disabled false
                              :checked false
                              :class "bar"}
                             {:disabled true
                              :style {:justify-content "center"}
                              :class "baz"}
                             {:disabled false
                              :checked true
                              :class "quux"}
                             nil))))

  (testing ":class"
    (is (= {:class "foo bar baz quux"}
           (util/merge-props {:class "foo bar"}
                             {:class ["baz" "quux"]})
           (util/merge-props nil {:class ["foo" "bar" "baz" "quux"]})
           (util/merge-props {:class ["foo" "bar" "baz" "quux"]})))))

(deftest partial-fn-test
  (is (= (util/make-partial-fn println ["a"])
         (util/make-partial-fn println ["a"])))

  (is (not (= (util/make-partial-fn println ["a"])
              nil))))
