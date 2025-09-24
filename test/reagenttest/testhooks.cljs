(ns reagenttest.testhooks
  (:require [clojure.test :as t :refer-macros [is deftest testing]]
            [reagent.core :as r]
            [reagent.hooks :as hooks]
            [reagent.ratom :as rv]
            [reagenttest.utils :as u]))

(t/use-fixtures :once
                {:before (fn []
                           (set! rv/debug true))
                 :after  (fn []
                           (set! rv/debug false))})

(def ran (atom 0))
(def update-state (atom nil))

(r/defc foo1 []
  (let [[x set-x] (hooks/use-state 0)]
    (reset! update-state set-x)
    (swap! ran inc)
    [:div "foo " x]))

(r/defc foo2 []
  (let [[x set-x] (hooks/use-state {:foo 0})]
    (reset! update-state set-x)
    (swap! ran inc)
    [:div "foo " (:foo x)]))

(deftest ^:dom use-state-test
  (u/async
    (testing "update state, pure value"
      (u/with-render [div [foo1]]
        (is (= 1 @ran))
        (is (= "foo 0" (.-innerText div)))

        (u/act (@update-state 1))
        (is (= 2 @ran))
        (is (= "foo 1" (.-innerText div)))))

    (testing "update state, clojure value"
      (reset! ran 0)
      (u/with-render [div [foo2]]
        (is (= 1 @ran))
        (is (= "foo 0" (.-innerText div)))

        (u/act (@update-state #(assoc % :foo 1)))
        (is (= 2 @ran))
        (is (= "foo 1" (.-innerText div)))

        ;; associng the same value into the clj map shouldn't trigger
        ;; change because the clojure equality is still same
        (u/act (@update-state #(assoc % :foo 1)))
        (is (= 2 @ran))
        (is (= "foo 1" (.-innerText div)))))))

