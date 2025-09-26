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

(r/defc state-1 []
  (let [[x set-x] (hooks/use-state 0)]
    (reset! update-state set-x)
    (swap! ran inc)
    [:div "foo " x]))

(r/defc state-2 []
  (let [[x set-x] (hooks/use-state {:foo 0})]
    (reset! update-state set-x)
    (swap! ran inc)
    [:div "foo " (:foo x)]))

(deftest ^:dom use-state-test
  (u/async
    (testing "update state, pure value"
      (reset! ran 0)
      (u/with-render [div [state-1]]
        (is (= 1 @ran))
        (is (= "foo 0" (.-innerText div)))

        (u/act (@update-state 1))
        (is (= 2 @ran))
        (is (= "foo 1" (.-innerText div)))))

    (testing "update state, clojure value"
      (reset! ran 0)
      (u/with-render [div [state-2]]
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

(def effect-ran (atom 0))

(r/defc effect-1 [x]
  (hooks/use-effect (fn []
                      (swap! effect-ran inc)
                      nil)
                    [x])
  (swap! ran inc)
  [:div "foo " (:foo x)])

(deftest ^:dom use-effect-test
  (u/async
    (testing "update state, pure value"
      (reset! ran 0)
      (reset! effect-ran 0)
      (let [x (r/atom {:foo 0})
            y (r/atom 0)
            c (fn []
                [effect-1 @x @y])]
        (u/with-render [div [c]]
          (is (= 1 @ran))
          (is (= 1 @effect-ran))
          (is (= "foo 0" (.-innerText div)))

          (u/act (swap! x assoc :foo 1))
          (is (= 2 @ran))
          (is (= 2 @effect-ran))
          (is (= "foo 1" (.-innerText div)))

          ;; use-effect wrapper considers clojure equality
          (u/act (swap! x assoc :foo 1)
                 (swap! y inc))
          (is (= 3 @ran))
          ;; effect didn't ran again because dependency value is the same
          (is (= 2 @effect-ran))
          (is (= "foo 1" (.-innerText div))))))))
