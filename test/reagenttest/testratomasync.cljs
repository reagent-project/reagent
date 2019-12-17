(ns reagenttest.testratomasync
  (:require [clojure.test :as t :refer-macros [is deftest testing]]
            [reagent.ratom :as rv :refer-macros [reaction]]
            [reagent.debug :as debug]
            [reagent.core :as r]))

(defn fixture [f]
  (set! rv/debug true)
  (f)
  (set! rv/debug false))

(t/use-fixtures :once fixture)

(defn running []
  (rv/running))

(def testite 10)

(defn dispose [v]
  (rv/dispose! v))

(defn sync [] (r/flush))

(defn ar [f] (rv/track! f))

(deftest basic-ratom
  (sync)
  (let [runs (running)
        start (rv/atom 0)
        sv (reaction @start)
        comp (reaction @sv (+ 2 @sv))
        c2 (reaction (inc @comp))
        count (rv/atom 0)
        out (rv/atom 0)
        res (reaction
             (swap! count inc)
             @sv @c2 @comp)
        const (ar (fn []
                    (reset! out @res)))]
    (is (= @count 1) "constrain ran")
    (is (= @out 2))
    (reset! start 1)
    (is (= @count 1))
    (sync)
    (is (= @out 3))
    (is (<= 2 @count 3))
    (reset! start 2)
    (dispose const)
    (is (= (running) runs) "did dispose")
    (sync)
    (is (= (running) runs) "should not awaken")))

(deftest double-dependency
  (sync)
  (let [runs (running)
        start (rv/atom 0)
        c3-count (rv/atom 0)
        c1 (reaction @start 1)
        c2 (reaction @start)
        c3 (ar
            (fn []
              (swap! c3-count inc)
              (+ @c1 @c2)))]
    (is (= @c3 1))
    (is (= @c3-count 1) "t1")
    (swap! start inc)
    (is (= @c3-count 1))
    (sync)
    (is (= @c3-count 2) "t2")
    (is (= @c3 2))
    (is (= @c3-count 2) "t3")
    (dispose c3)
    (is (= (running) runs))
    (sync)
    (is (= @c3 2))
    (is (= (running) runs))))

(deftest test-from-reflex
  (sync)
  (let [runs (running)]
    (let [!counter (rv/atom 0)
          !signal (rv/atom "All I do is change")
          co (ar (fn []
                   ;;when I change...
                   @!signal
                   ;;update the counter
                   (swap! !counter inc)))]
      (is (= 1 @!counter) "Constraint run on init")
      (reset! !signal "foo")
      (sync)
      (is (= 2 @!counter)
          "Counter auto updated")
      (dispose co))
    (let [!x (rv/atom 0)
          !co (ar #(inc @!x))]
      @!co
      (is (= 1 @!co) "CO has correct value on first deref") 
      (swap! !x inc)
      (sync)
      (is (= 2 @!co) "CO auto-updates")
      (dispose !co))
    (is (= runs (running)))))


(deftest test-unsubscribe
  (sync)
  (dotimes [x testite]
    (let [runs (running)
          a (rv/atom 0)
          a1 (reaction (inc @a))
          a2 (reaction @a)
          b-changed (rv/atom 0)
          c-changed (rv/atom 0)
          b (reaction
             (swap! b-changed inc)
             (inc @a1))
          c (reaction
             (swap! c-changed inc)
             (+ 10 @a2))
          res (atom nil)
          resr (ar (fn []
                     (reset! res
                             (if (< @a2 1) @b @c))))]
      (is (= @res (+ 2 @a)))
      (is (= @b-changed 1))
      (is (= @c-changed 0))
             
      (reset! a -1)
      (is (= @b-changed 1))
      (sync)

      (is (= @b-changed 2))
      (is (= @c-changed 0))
      (is (= @res (+ 2 @a)))
             
      (reset! a 2)
      (sync)
      (is (<= 2 @b-changed 3))
      (is (= @c-changed 1))
      (is (= @res (+ 10 @a)))
             
      (reset! a 3)
      (sync)
      (is (<= 2 @b-changed 3))
      (is (= @c-changed 2))
      (is (= @res (+ 10 @a)))
             
      (reset! a 3)
      (sync)
      (is (<= 2 @b-changed 3))
      (is (= @c-changed 2))
      (is (= @res (+ 10 @a)))
             
      (reset! a -1)
      (sync)
      (is (= @res (+ 2 @a)))
      (dispose resr)
      (is (= runs (running))))))

(deftest maybe-broken
  (sync)
  (let [runs (running)]
    (let [runs (running)
          a (rv/atom 0)
          b (reaction (inc @a))
          c (reaction (dec @a))
          d (reaction (str @b))
          res (rv/atom 0)
          cs (ar
              #(reset! res @d))]
      @cs
      (is (= @res "1"))
      (dispose cs))
    ;; should be broken according to https://github.com/lynaghk/reflex/issues/1
    ;; but isnt
    (let [a (rv/atom 0)
          b (reaction (inc @a))
          c (reaction (dec @a))
          d (ar (fn [] [@b @c]))]
      (is (= @d [1 -1]))
      (dispose d))
    (let [a (rv/atom 0)
          b (reaction (inc @a))
          c (reaction (dec @a))
          d (ar (fn [] [@b @c]))
          res (rv/atom 0)]
      (is (= @d [1 -1]))
      (let [e (ar #(reset! res @d))]
        @e
        (is (= @res [1 -1]))
        (dispose e))
      (dispose d))
    (is (= runs (running)))))

(deftest test-dispose
  (dotimes [x testite]
    (let [runs (running)
          a (rv/atom 0)
          disposed (rv/atom nil)
          disposed-c (rv/atom nil)
          disposed-cns (rv/atom nil)
          count-b (rv/atom 0)
          count-c (atom 0)
          b (rv/make-reaction (fn []
                                (swap! count-b inc)
                                (inc @a))
                              :on-dispose #(reset! disposed true))
          c (rv/make-reaction (fn []
                                (swap! count-c inc)
                                (if (< @a 1) (inc @b) (+ 100 @a)))
                              :on-dispose #(reset! disposed-c true))
          res (rv/atom nil)
          main (rv/make-reaction #(reset! res @c)
                                 :on-dispose #(reset! disposed-cns true))
          cns (rv/track! #(deref main))]
      (is (= @res 2))
      (is (= @count-b 1))
      (is (= @count-c 1))
      (reset! a -1)
      (is (= @res 2))
      (sync)
      (is (= @res 1))
      (is (= @disposed nil))
      (is (= @count-b 2))
      (is (= @count-c 2))
      (reset! a 2)
      (is (= @res 1) "unchanged")
      (sync)
      (is (= @res 102) "new value")
      (is (= @count-c 3))
      (is (= @disposed true) "should be disposed")

      (reset! disposed nil)
      (reset! a -1)
      (sync)
      (is (= 1 @res) "should be one again")
      (is (= @disposed nil))
      (reset! a 2)
      (sync)
      (is (= @res 102))
      (is (= @disposed true))
      (dispose cns)
      (is (= @disposed-c true))
      (is (= @disposed-cns true))
      (is (= runs (running))))))

(deftest test-on-set
  (sync)
  (let [runs (running)
        a (rv/atom 0)
        b (rv/make-reaction #(+ 5 @a)
                            :auto-run true
                            :on-set (fn [oldv newv]
                                      (reset! a (+ 10 newv))))]
    (sync)
    (is (= 5 @b))
    (reset! a 1)
    (sync)
    (is (= 6 @b))
    (reset! b 1)
    (sync)
    (is (= 11 @a))
    (is (= 16 @b))
    (dispose b)
    (is (= runs (running)))))

(deftest non-reactive-deref
  (let [runs (running)
        a (rv/atom 0)
        b (rv/make-reaction #(+ 5 @a))]
    (is (= @b 5))
    (is (= runs (running)))

    (reset! a 1)
    (is (= @b 6))
    (is (= runs (running)))))

(deftest catching
  (let [runs (running)
        a (rv/atom false)
        catch-count (atom 0)
        b (reaction (if @a (throw (js/Error. "reaction fail"))))
        c (ar (fn [] (try @b (catch js/Object e
                               (swap! catch-count inc)))))]
    (debug/track-warnings
     (fn []
       (is (= @catch-count 0))
       (reset! a false)
       @c
       (is (= @catch-count 0))
       (reset! a true)
       (is (= @catch-count 0))
       (sync)
       (is (= @catch-count 1))
       (dispose c)))
    (is (= runs (running)))))
