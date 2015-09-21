(ns reagenttest.testtrack
  (:require [cljs.test :as t :refer-macros [is deftest testing]]
            [reagent.ratom :as rv :refer [track] :refer-macros [run! reaction]]
            [reagent.debug :refer-macros [dbg]]
            [reagent.core :as r]))

(defn running []
  (set! rv/debug true)
  (rv/running))

(def testite 10)

(defn dispose [v]
  (rv/dispose! v))

(defn sync [] (r/flush))

(enable-console-print!)


(deftest basic-ratom
  (let [runs (running)
        start (rv/atom 0)
        svf (fn [] @start)
        sv (track svf)
        compf (fn [x] @sv (+ x @sv))
        comp (track compf 2)
        c2f (fn [] (inc @comp))
        count (rv/atom 0)
        out (rv/atom 0)
        resf (fn []
               (swap! count inc)
               (+ @sv @(track c2f) @comp))
        res (track resf)
        const (run!
               (reset! out @res))]
    (is (= @count 1) "constrain ran")
    (is (= @out 5))
    (reset! start 1)
    (is (= @out 8))
    (is (= @count 2))
    (dispose const)
    (is (= (running) runs))))

(deftest test-track!
  (sync)
  (let [runs (running)
        start (rv/atom 0)
        svf (fn [] @start)
        sv (track svf)
        compf (fn [x] @sv (+ x @sv))
        comp (track compf 2)
        c2f (fn [] (inc @comp))
        count (rv/atom 0)
        out (rv/atom 0)
        resf (fn []
               (swap! count inc)
               (+ @sv @(track c2f) @comp))
        res (track resf)
        const (rv/track!
               #(reset! out @res))]
    (is (= @count 1) "constrain ran")
    (is (= @out 5))
    (reset! start 1)
    (is (= @count 1))
    (sync)
    (is (= @out 8))
    (is (= @count 2))
    (dispose const)
    (swap! start inc)
    (sync)
    (is (= @count 2))
    (is (= @const 11))
    (is (= @count 3))
    (is (= (running) runs))))

(deftest double-dependency
  (let [runs (running)
        start (rv/atom 0)
        c3-count (rv/atom 0)
        c1f (fn [] @start 1)
        c2f (fn [] @start)
        c3 (rv/make-reaction
            (fn []
              (swap! c3-count inc)
              (+ @(track c1f) @(track c2f)))
            :auto-run true)]
    (is (= @c3-count 0))
    (is (= @c3 1))
    (is (= @c3-count 1) "t1")
    (swap! start inc)
    (is (= @c3-count 2) "t2")
    (is (= @c3 2))
    (is (= @c3-count 2) "t3")
    (dispose c3)
    (is (= (running) runs))))

(deftest test-from-reflex
  (let [runs (running)]
    (let [!x (rv/atom 0)
          f #(inc @!x)
          !co (run! @(track f))]
      (is (= 1 @!co) "CO has correct value on first deref")
      (swap! !x inc)
      (is (= 2 @!co) "CO auto-updates")
      (dispose !co))
    (is (= runs (running)))))


(deftest test-unsubscribe
  (dotimes [x testite]
    (let [runs (running)
          a (rv/atom 0)
          af (fn [x] (+ @a x))
          a1 (track af 1)
          a2 (track af 0)
          b-changed (rv/atom 0)
          c-changed (rv/atom 0)
          mf (fn [v x spy]
               (swap! spy inc)
               (+ @v x))
          res (run!
               (if (< @a2 1)
                 @(track mf a1 1 b-changed)
                 @(track mf a2 10 c-changed)))]
      (is (= @res (+ 2 @a)))
      (is (= @b-changed 1))
      (is (= @c-changed 0))

      (reset! a -1)
      (is (= @res (+ 2 @a)))
      (is (= @b-changed 2))
      (is (= @c-changed 0))

      (reset! a 2)
      (is (= @res (+ 10 @a)))
      (is (<= 2 @b-changed 3))
      (is (= @c-changed 1))

      (reset! a 3)
      (is (= @res (+ 10 @a)))
      (is (<= 2 @b-changed 3))
      (is (= @c-changed 2))

      (reset! a 3)
      (is (= @res (+ 10 @a)))
      (is (<= 2 @b-changed 3))
      (is (= @c-changed 2))

      (reset! a -1)
      (is (= @res (+ 2 @a)))
      (dispose res)
      (is (= runs (running))))))

(deftest maybe-broken
  (let [runs (running)]
    (let [runs (running)
          a (rv/atom 0)
          f (fn [x] (+ x @a))
          b (track f 1)
          c (track f -1)
          d (track #(str @b))
          res (rv/atom 0)
          cs (run!
              (reset! res @d))]
      (is (= @res "1"))
      (dispose cs))
    ;; should be broken according to https://github.com/lynaghk/reflex/issues/1
    ;; but isnt
    (let [a (rv/atom 0)
          f (fn [x] (+ x @a))
          b (track f 1)
          d (run! [@b @(track f -1)])]
      (is (= @d [1 -1]))
      (dispose d))
    (let [a (rv/atom 0)
          f (fn [x] (+ x @a))
          c (track f -1)
          d (run! [@(track f 1) @c])
          res (rv/atom 0)]
      (is (= @d [1 -1]))
      (let [e (run! (reset! res @d))]
        (is (= @res [1 -1]))
        (dispose e))
      (dispose d))
    (is (= runs (running)))))

(deftest non-reactive-deref
  (let [runs (running)
        a (rv/atom 0)
        b (track #(+ 5 @a))]
    (is (= @b 5))
    (is (= runs (running)))

    (reset! a 1)
    (is (= @b 6))
    (is (= runs (running)))))

(deftest catching
  (let [runs (running)
        a (rv/atom false)
        catch-count (atom 0)
        b (track #(if @a (throw (js/Error. "fail"))))
        c (run! (try @b (catch :default e
                          (swap! catch-count inc))))]
    (set! rv/silent true)

    (is (= @catch-count 0))
    (reset! a false)
    (is (= @catch-count 0))
    (reset! a true)
    (is (= @catch-count 1))
    (reset! a false)
    (is (= @catch-count 1))

    (set! rv/silent false)
    (dispose c)
    (is (= runs (running)))))
