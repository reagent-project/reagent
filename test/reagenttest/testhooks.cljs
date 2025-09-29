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
(def child-ran (atom 0))
(def update-state (atom nil))
(def dispatch (atom nil))

(r/defc child [x]
  (swap! child-ran inc)
  [:div " child " x])

(r/defc state-1 []
  (let [[x set-x] (hooks/use-state 0)]
    (reset! update-state set-x)
    (swap! ran inc)
    [:div "foo " x]))

(r/defc state-2 []
  (let [[x set-x] (hooks/use-state {:foo 0})]
    (reset! update-state set-x)
    (swap! ran inc)
    [:div "foo " (:foo x)
     [child (:foo x)]]))

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
      (reset! child-ran 0)
      (u/with-render [div [state-2]]
        (is (= 1 @ran))
        (is (= 1 @child-ran))
        (is (= "foo 0 child 0" (.-innerText div)))

        (u/act (@update-state #(assoc % :foo 1)))
        (testing "new value causes re-render"
          (is (= 2 @ran))
          (is (= 2 @child-ran))
          (is (= "foo 1 child 1" (.-innerText div))))

        (u/act (@update-state #(assoc % :foo 1)))
        (testing "associng equal value to the state shouldn't trigger re-render, but looks the first time does"
          (is (= 3 @ran))
          (is (= 2 @child-ran))
          (is (= "foo 1 child 1" (.-innerText div))))

        (u/act (@update-state #(assoc % :foo 1)))
        (testing "associng equal value to the state shouldn't trigger re-render"
          (is (= 3 @ran))
          (is (= 2 @child-ran))
          (is (= "foo 1 child 1" (.-innerText div))))
        ))))

(def effect-ran (atom 0))

(r/defc effect-0 [x]
  (hooks/use-effect (fn []
                      (swap! effect-ran inc)))
  (swap! ran inc)
  [:div "foo " (:foo x)])

(r/defc effect-1 [x]
  (hooks/use-effect (fn []
                      (swap! effect-ran inc)
                      nil)
                    [x])
  (swap! ran inc)
  [:div "foo " (:foo x)])

(deftest ^:dom use-effect-test
  (u/async
    (testing "no dependencies"
      (reset! ran 0)
      (reset! effect-ran 0)
      (let [x (r/atom {:foo 0})
            y (r/atom 0)
            c (fn []
                [effect-0 @x @y])]
        (u/with-render [div [c]]
          (is (= 1 @ran))
          (is (= 1 @effect-ran))
          (is (= "foo 0" (.-innerText div)))

          (u/act (swap! x assoc :foo 1))
          (testing "render causes effect rerun"
            (is (= 2 @ran))
            (is (= 2 @effect-ran))
            (is (= "foo 1" (.-innerText div)))))))

    (testing "clj dependencies"
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
          (testing "new dependency value causes effect rerun"
            (is (= 2 @ran))
            (is (= 2 @effect-ran))
            (is (= "foo 1" (.-innerText div))))

          ;; use-effect wrapper considers clojure equality
          (u/act (swap! x assoc :foo 1)
                 (swap! y inc))
          (testing "new equal dependency value doesn't cause effect to run again"
            (is (= 3 @ran))
            (is (= 2 @effect-ran))
            (is (= "foo 1" (.-innerText div)))))))))

(r/defc reducer-1 [x]
  (let [[v dispatch*] (hooks/use-reducer (fn [current-state action]
                                           (update current-state :foo + action))
                                         x
                                         (fn [init-arg]
                                           {:foo init-arg}))]
    (swap! ran inc)
    (reset! dispatch dispatch*)
    [:div "foo " (:foo v)
     [child (:foo v)]]))

(deftest ^:dom use-reducer-test
  (u/async
    (testing "update state, clojure value"
      (reset! ran 0)
      (reset! child-ran 0)
      (u/with-render [div [reducer-1 0]]
        (is (= 1 @ran))
        (is (= 1 @child-ran))
        (is (= "foo 0 child 0" (.-innerText div)))

        (u/act (@dispatch 2))
        (testing "new value causes re-render"
          (is (= 2 @ran))
          (is (= 2 @child-ran))
          (is (= "foo 2 child 2" (.-innerText div))))

        (u/act (@dispatch 0))
        (testing "equal value shouldn't trigger re-render, but looks the first time does"
          (is (= 3 @ran))
          (is (= 2 @child-ran))
          (is (= "foo 2 child 2" (.-innerText div))))

        (u/act (@dispatch 0))
        (testing "equal value shouldn't trigger re-render"
          (is (= 4 @ran))
          (is (= 2 @child-ran))
          (is (= "foo 2 child 2" (.-innerText div))))))))


(r/defc ref-1 [x]
  (let [ref-val (hooks/use-ref x)]
    (swap! ran inc)
    [:div "foo " x " " (.-current ref-val)]))

(deftest ^:dom use-ref-test
  (u/async
    (testing "update state, clojure value"
      (reset! ran 0)
      (let [y (r/atom 0)
            x (r/atom 5)
            c (fn []
                ^{:key @y}
                [ref-1 @x])]
        (u/with-render [div [c]]
          (is (= 1 @ran))
          (is (= "foo 5 5" (.-innerText div)))

          (u/act (reset! x 7))
          (is (= 2 @ran))
          (is (= "foo 7 5" (.-innerText div)))

          (u/act (reset! y 1)
                 (reset! x 9))
          (is (= 3 @ran))
          (is (= "foo 9 9" (.-innerText div)))
          )))))
