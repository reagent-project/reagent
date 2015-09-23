(ns reagenttest.testwithlet
  (:require [cljs.test :as t :refer-macros [is deftest testing]]
            [reagent.ratom :as rv :refer [track track! dispose!]
             :refer-macros [with-let]]
            [reagent.debug :refer-macros [dbg]]
            [reagent.core :as r :refer [flush]]
            [clojure.walk :as w]))

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
        t (track! (fn []
                    (reset! r @(track f1))))]
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


(deftest test-with-let-args
  (let [runs (running)
        n1 (atom 0)
        n2 (atom 0)
        a (r/atom 0)
        ran (fn []
              (swap! n2 inc)
              @a)
        f1 #(with-let []
              (ran)
              [])
        f2 #(with-let [x1 (swap! n1 inc)]
              (ran)
              [x1])
        f3 #(with-let [x1 (swap! n1 inc)
                       x2 (swap! n1 inc)]
              (ran)
              [x1 x2])
        f4 #(with-let [x1 (swap! n1 inc)
                       x2 (swap! n1 inc)
                       x3 (swap! n1 inc)]
              (ran)
              [x1 x2 x3])
        f5 #(with-let [x1 (swap! n1 inc)
                       x2 (swap! n1 inc)
                       x3 (swap! n1 inc)
                       x4 (swap! n1 inc)]
              (ran)
              [x1 x2 x3 x4])
        f6 #(with-let [x1 (swap! n1 inc)
                       x2 (swap! n1 inc)
                       x3 (swap! n1 inc)
                       x4 (swap! n1 inc)
                       x5 (swap! n1 inc)]
              (ran)
              [x1 x2 x3 x4 x5])
        f7 #(with-let [x1 (swap! n1 inc)
                       x2 (swap! n1 inc)
                       x3 (swap! n1 inc)
                       x4 (swap! n1 inc)
                       x5 (swap! n1 inc)
                       x6 (swap! n1 inc)]
              (ran)
              [x1 x2 x3 x4 x5 x6])
        r (atom nil)
        all (fn [] {:f1 @(track f1)
                    :f2 @(track f2)
                    :f3 @(track f3)
                    :f4 @(track f4)
                    :f5 @(track f5)
                    :f6 @(track f6)
                    :f7 @(track f7)})
        t (track! (fn [] (reset! r (all))))
        expected {:f1 []
                  :f2 [1]
                  :f3 [2 3]
                  :f4 [4 5 6]
                  :f5 [7 8 9 10]
                  :f6 [11 12 13 14 15]
                  :f7 [16 17 18 19 20 21]}]
    (is (< runs (running)))
    (is (= @n2 7))
    (is (= @r expected))
    (is (= (all) expected))
    (is (= @t expected))
    (swap! a inc)
    (is (= @n2 7))
    (flush)
    (is (= @n2 14))
    (is (= @r expected))
    (is (= (all) expected))
    (is (= @t expected))
    (is (= @n2 14))
    (dispose! t)
    (is (= runs (running)))
    (is (= @r expected))
    (is (= @n2 14))
    (is (= (all) (w/postwalk #(if (number? %) (+ 21 %) %)
                             expected)))
    (is (= @n2 21))
    (is (= @t (w/postwalk #(if (number? %) (+ 42 %) %)
                          expected)))
    (is (= @n2 28))
    (is (= runs (running)))))
