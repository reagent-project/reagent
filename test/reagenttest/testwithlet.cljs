(ns reagenttest.testwithlet
  (:require [clojure.test :as t :refer-macros [is deftest testing]]
            [reagent.ratom :as rv]
            [reagent.debug :as d]
            [reagent.core :as r :refer [flush track track! dispose!] :refer-macros [with-let]]
            [clojure.walk :as w]))

;; Test code generated from with-let macro
;; https://github.com/reagent-project/reagent/issues/420
(set! *warn-on-infer* true)

(defn fixture [f]
  (set! rv/debug true)
  (f)
  (set! rv/debug false))

(t/use-fixtures :once fixture)

(defn running []
  (r/flush)
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

    (is (= [12 3] (f1)))
    (is (= [[12 2] 3 4 3] [@r @n1 @n2 @n3]))

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

(deftest non-reactive-with-let
  (let [n1 (atom 0)
        n2 (atom 0)
        n3 (atom 0)
        n4 (atom 0)
        f1 (fn []
             (with-let []
               (swap! n2 inc)))
        f2 (fn []
             (with-let [v (swap! n1 inc)]
               v))
        f3 (fn []
             (with-let [v (swap! n1 inc)]
               (swap! n2 inc)
               (finally (swap! n3 inc))))
        f4 (fn []
             (with-let []
               (finally (swap! n3 inc)
                        (swap! n4 inc))))
        f5 (fn []
             [(f1) (f2) (f4)])
        tst (fn [f]
              [(f) @n1 @n2 @n3])]
    (is (= [1 0 1 0] (tst f1)))
    (is (= [1 1 1 0] (tst f2)))
    (is (= [2 2 2 1] (tst f3)))
    (is (= 0 @n4))
    (is (= [nil 2 2 2] (tst f4)))
    (is (= 1 @n4))
    (is (= [[3 3 nil] 3 3 3] (tst f5)))))

(deftest with-let-args
  (let [runs (running)
        active (atom 0)
        n1 (atom 0)
        f1 (fn [x y]
             (with-let [_ (swap! active inc)
                        v (r/atom @x)]
               (swap! n1 inc)
               (+ y @v)
               (finally
                 (reset! v nil)
                 (swap! active dec))))
        f2 (fn [x y]
             (with-let [t1 (track f1 x y)
                        t2 (track f1 x y)]
               (let [v @(track f1 x y)]
                 (is (= v @t1 @t2))
                 v)))
        f2t (partial track f2)
        res (atom nil)
        val (r/atom 1)
        valtrack (track deref val)
        t (track! #(reset! res (let [v valtrack]
                                 (if (> @v 2)
                                   [@(f2t v 10)]
                                   [@(f2t val 0)
                                    @(f2t val 0)
                                    @(f2t v 10)
                                    (f1 v 10)]))))]
    (is (= [1 1 11 11] @res))
    (is (= [3 3] [@n1 @active]))
    (reset! val 1)
    (flush)
    (is (= [1 1 11 11] @res))
    (is (= [3 3] [@n1 @active]))

    (swap! val inc)
    (is (= [3 3] [@n1 @active]))
    (flush)
    (is (= [6 3] [@n1 @active]))
    (is (= [1 1 11 11] @res))

    (swap! val inc)
    (flush)
    (is (= [6 1] [@n1 @active]))
    (is (= [11] @res))

    (dispose! t)
    (is (= [6 0] [@n1 @active]))
    (is (= runs (running)))))

(deftest with-let-warning
  (when (d/dev?)
    (let [f1 (fn []
               (with-let [a 1]))
          w (d/track-warnings
             (fn []
               (track! (fn []
                         (f1)
                         (f1)))))]
      (is (= w {:error (list (str "Warning: The same with-let is being "
                                  "used more than once in the same reactive "
                                  "context."))})))))

(deftest with-let-nested
  (let [runs (running)
        init (atom 0)
        destroy (atom 0)
        state (r/atom 0)
        exec (atom 0)
        f (fn []
            (with-let [i (swap! init inc)]
              (is (= i 1))
              (with-let [j (swap! init inc)]
                (is (= j 2))
                @state
                (swap! exec inc)
                (finally
                  (is (= @destroy 1))
                  (swap! destroy inc)))
              (finally
                (is (= @destroy 0))
                (swap! destroy inc))))
        t (track! (fn []
                    (when (< @state 2)
                      @(track f))))]
    (is (= @init 2))
    (is (= @exec 1))
    (swap! state inc)
    (flush)
    (is (= @exec 2))
    (is (= @init 2))
    (is (= @destroy 0))
    (swap! state inc)
    (flush)
    (is (= @destroy 2))
    (dispose! t)
    (is (= @destroy 2))
    (is (= runs (running)))))
