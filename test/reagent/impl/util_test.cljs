(ns reagent.impl.util-test
  (:require [clojure.test :refer [deftest is testing]]
            [reagent.impl.util :as util]))

(deftest merge-props-test
  (testing "It handles no arguments"
    (is (nil? (util/merge-props))))
  (testing "It handles one argument"
    (is (nil? (util/merge-props nil)))
    (is (= {:foo :bar} (util/merge-props {:foo :bar}))))
  (testing "It handles two arguments"
    (is (= {:disabled false :style {:flex 1 :flex-direction "row"} :class "foo bar"}
           (util/merge-props {:disabled true :style {:flex 1} :class "foo"}
                             {:disabled false :style {:flex-direction "row"} :class "bar"}))))
  (testing "It handles n arguments"
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
                             nil)))))
