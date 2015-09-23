(ns reagenttest.testwithlet
  (:require [cljs.test :as t :refer-macros [is deftest testing]]
            [reagent.ratom :as rv :refer [track track! dispose!]
             :refer-macros [with-let]]
            [reagent.debug :refer-macros [dbg]]
            [reagent.core :as r :refer [flush]]))

(defn running []
  (r/flush)
  (set! rv/debug true)
  (rv/running))

(deftest basic-with-let
  (let [runs (running)
        n1 (atom 0)
        n2 (atom 0)
        n3 (atom 0)
        a (r/atom 10)
        f1 (fn []
             (with-let [v (swap! n1 inc)]
               (swap! n2 inc)
               [@a v]
               (finally
                 (swap! n3 inc))))
        r (atom nil)
        t (track! (fn [] (reset! r @(track f1))))]
    (is (= [[10 1] 1 1 0] [@r @n1 @n2 @n3]))
    (swap! a inc)
    (is (= [[10 1] 1 1 0] [@r @n1 @n2 @n3]))
    (flush)
    (is (= [[11 1] 1 2 0] [@r @n1 @n2 @n3]))
    (is (= [11 1] @t))

    (dispose! t)
    (is (= [[11 1] 1 2 1] [@r @n1 @n2 @n3]))
    (is (= runs (running)))

    (swap! a inc)
    (flush)
    (is (= [[11 1] 1 2 1] [@r @n1 @n2 @n3]))
    (is (= [12 2] @t))
    (is (= [[12 2] 2 3 2] [@r @n1 @n2 @n3]))
    (is (= runs (running)))))
