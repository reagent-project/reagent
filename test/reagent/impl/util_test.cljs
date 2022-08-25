(ns reagent.impl.util-test
  (:require [clojure.test :refer [deftest is testing]]
            [reagent.debug :refer [dev?]]
            [reagent.core :as r]
            [reagent.impl.util :as util]))

(deftest class-names-test
  (is (= nil
         (r/class-names)
         (r/class-names nil)
         (r/class-names [])
         (r/class-names nil [])))
  (is (= "a b"
         (r/class-names ["a" "b"])
         (r/class-names "a" "b")))
  (is (= "a"
         (r/class-names :a)
         (r/class-names [:a])
         (r/class-names nil "a")
         (r/class-names [] nil "a")))
  (is (= "a b c d"
         (r/class-names "a" "b" nil ["c" "d"]))))

(deftest dash-to-prop-name-test
  (is (= "tabIndex" (util/dash-to-prop-name :tab-index)))
  (is (= "data-foo-bar" (util/dash-to-prop-name :data-foo-bar)))
  (is (= "string-Value" (util/dash-to-prop-name "string-Value")))
  (is (= "aBC" (util/dash-to-prop-name :a-b-c))))

(deftest dash-to-method-name-test
  (is (= "string-Value"
         (util/dash-to-method-name "string-Value")))
  (is (= "componentDidMount"
         (util/dash-to-method-name :component-did-mount)))
  (is (= "componentDidMount"
         (util/dash-to-method-name :componentDidMount)))
  (is (= "UNSAFE_componentDidMount"
         (util/dash-to-method-name :unsafe-component-did-mount)))
  (is (= "UNSAFE_componentDidMount"
         (util/dash-to-method-name :unsafe_componentDidMount)))
  (is (= "aBC"
         (util/dash-to-method-name :a-b-c))))

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
    (is (nil? (r/merge-props))))

  (testing "one argument"
    (is (nil? (r/merge-props nil)))
    (is (= {:foo :bar} (r/merge-props {:foo :bar}))))

  (testing "two arguments"
    (is (= {:disabled false :style {:flex 1 :flex-direction "row"} :class "foo bar"}
           (r/merge-props {:disabled true :style {:flex 1} :class "foo"}
                          {:disabled false :style {:flex-direction "row"} :class "bar"})))

    (is (= {:disabled true}
           (r/merge-props nil {:disabled true})
           (r/merge-props {:disabled true} nil) )))

  (testing "two arguments without classes"
    (is (= {:disabled false :style {:flex 1 :flex-direction "row"}}
           (r/merge-props {:disabled true :style {:flex 1}}
                          {:disabled false :style {:flex-direction "row"}}))))

  (testing "n arguments"
    (is (= {:disabled false
            :checked true
            :style {:align-items "flex-end"
                    :justify-content "center"}
            :class "foo bar baz quux"}
           (r/merge-props {:disabled false
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
           (r/merge-props {:class "foo bar"}
                          {:class ["baz" "quux"]})
           (r/merge-props nil {:class ["foo" "bar" "baz" "quux"]})
           (r/merge-props {:class ["foo" "bar" "baz" "quux"]} nil)
           (r/merge-props {:class ["foo" "bar" "baz" "quux"]})
           (r/merge-props {:class "foo bar"} {:class ["baz"]} {:class ["quux"]}))))

  (when (dev?)
    (testing "assertion"
      (is (thrown-with-msg? js/Error #"Assert failed: Property must be a map, not" (r/merge-props #js {} {:class "foo"}))))))

(deftest partial-fn-test
  (is (= (util/make-partial-fn println ["a"])
         (util/make-partial-fn println ["a"])))

  (is (= (hash (util/make-partial-fn println ["a"]))
         (hash (util/make-partial-fn println ["a"]))))

  (testing "partial fn invoke"
    ;; Test all IFn arities
    (doseq [c (range 0 23)]
      (is (= (seq (repeat c "a")) ((util/make-partial-fn (fn [& args] args) (repeat c "a")))))))

  (is (not (= (util/make-partial-fn println ["a"])
              nil))))

(deftest fun-name-test
  (when (dev?)
    (is (= "reagent.impl.util_test.foobar"
           (util/fun-name (fn foobar [] 1)))))

  (is (= "foobar"
         (let [f (fn [] 1)]
           (set! (.-displayName f) "foobar")
           (util/fun-name f)))) )

(defn foo [m]
  [:h1])

(deftest react-key-from-vec-test
  (is (= 1 (util/react-key-from-vec ^{:key 1} [:foo "bar"])))
  (is (= 1 (util/react-key-from-vec [:foo {:key 1} "bar"])))
  (is (= 1 (util/react-key-from-vec [:> "div" {:key 1} "bar"])))
  (is (= 1 (util/react-key-from-vec [:f> "div" {:key 1} "bar"])))
  (is (= 1 (util/react-key-from-vec [:r> "div" #js {:key 1} "bar"])))

  ;; TODO: What should happen in this case?
  (is (= 1 (util/react-key-from-vec [foo {:key 1}])))

  (is (= nil (util/react-key-from-vec [:r> "div" nil "bar"])))

  (testing "false as key"
    (is (= false (util/react-key-from-vec ^{:key false} [:foo "bar"])))
    (is (= false (util/react-key-from-vec [:foo {:key false} "bar"])))
    (is (= false (util/react-key-from-vec [:> "div" {:key false} "bar"])))
    (is (= false (util/react-key-from-vec [:f> "div" {:key false} "bar"])))
    (is (= false (util/react-key-from-vec [:r> "div" #js {:key false} "bar"])))))
