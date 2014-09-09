(ns testcursor
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing)]
                   [reagent.ratom :refer [run! reaction]]
                   [reagent.debug :refer [dbg]])
  (:require [cemerick.cljs.test :as t]
            [reagent.ratom :as rv]))

;; this repeats all the atom tests but using cursors instead

(set! rv/debug true)

(defn running [] (rv/running))
(defn dispose [v] (rv/dispose! v))

(defn ratom-perf []
  (dbg "ratom-perf")
  (let [a (rv/atom {})
        mid (reaction (inc @a))
        res (run!
             (inc @mid))]
    (time (dotimes [x 100000]
            (swap! a inc)))
    (dispose res)))

;; (ratom-perf)

(deftest basic-cursor
  (let [runs (running)
        start-base (rv/atom {:a {:b {:c 0}}})
        start (rv/cursor [:a :b :c] start-base)
        sv (reaction @start)
        comp (reaction @sv (+ 2 @sv))
        c2 (reaction (inc @comp))
        count (rv/atom 0)
        out (rv/atom 0)
        res (reaction
             (swap! count inc)
             @sv @c2 @comp)
        const (run!
               (reset! out @res))]
    (is (= @count 1) "constrain ran")
    (is (= @out 2))
    (reset! start 1)
    (is (= @out 3))
    (is (= @count 4))
    (dispose const)
    (is (= @start-base {:a {:b {:c 1}}}))
    (is (= (running) runs))))

(deftest double-dependency
  (let [runs (running)
        start-base (rv/atom {:a {:b {:c 0}}})
        start (rv/cursor [:a :b :c] start-base)
        c3-count (rv/atom 0)
        c1 (reaction @start 1)
        c2 (reaction @start)
        c3 (rv/make-reaction
            (fn []
              (swap! c3-count inc)
              (+ @c1 @c2))
            :auto-run true)]
    (is (= @c3-count 0))
    (is (= @c3 1))
    (is (= @c3-count 1) "t1")
    (swap! start inc)
    (is (= @c3-count 2) "t2")
    (is (= @c3 2))
    (is (= @c3-count 2) "t3")
    (is (= @start-base {:a {:b {:c 1}}}))
    (dispose c3)
    (is (= (running) runs))))

(deftest test-from-reflex
  (let [runs (running)]
    (let [!ctr-base (rv/atom {:x {:y 0 :z 0}})
          !counter (rv/cursor [:x :y] !ctr-base)
          !signal (rv/atom "All I do is change")
          co (run!
              ;;when I change...
              @!signal
              ;;update the counter
              (swap! !counter inc))]
      (is (= 1 @!counter) "Constraint run on init")
      (reset! !signal "foo")
      (is (= 2 @!counter)
          "Counter auto updated")
      (is (= @!ctr-base {:x {:y 2 :z 0}}))
      (dispose co))
    (let [!x-base (rv/atom {:a {:b 0 :c {:d 0}}})
          !x (rv/cursor [:a :c :d] !x-base)
          !co (rv/make-reaction #(inc @!x) :auto-run true)]
      (is (= 1 @!co) "CO has correct value on first deref") 
      (swap! !x inc) 
      (is (= 2 @!co) "CO auto-updates")
      (is (= {:a {:b 0 :c {:d 1}}} @!x-base))
      (dispose !co))
    (is (= runs (running)))))


(deftest test-unsubscribe
  (dotimes [x 10]
    (let [runs (running)
          a-base (rv/atom {:test {:unsubscribe 0 :value 42}})
          a (rv/cursor [:test :unsubscribe] a-base)
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
          res (run!
               (if (< @a2 1) @b @c))]
      (is (= @res (+ 2 @a)))
      (is (= @b-changed 1))
      (is (= @c-changed 0))
             
      (reset! a -1)
      (is (= @res (+ 2 @a)))
      (is (= @b-changed 2))
      (is (= @c-changed 0))
      (is (= @a-base {:test {:unsubscribe -1 :value 42}}))
             
      (reset! a 2)
      (is (= @res (+ 10 @a)))
      (is (<= 2 @b-changed 3))
      (is (= @c-changed 1))
      (is (= @a-base {:test {:unsubscribe 2 :value 42}}))
             
      (reset! a 3)
      (is (= @res (+ 10 @a)))
      (is (<= 2 @b-changed 3))
      (is (= @c-changed 2))
      (is (= @a-base {:test {:unsubscribe 3 :value 42}}))
             
      (reset! a 3)
      (is (= @res (+ 10 @a)))
      (is (<= 2 @b-changed 3))
      (is (= @c-changed 2))
      (is (= @a-base {:test {:unsubscribe 3 :value 42}}))
             
      (reset! a -1)
      (is (= @res (+ 2 @a)))
      (is (= @a-base {:test {:unsubscribe -1 :value 42}}))
      (dispose res)
      (is (= runs (running))))))

(deftest maybe-broken
  (let [runs (running)]
    (let [runs (running)
          a-base (rv/atom {:a {:b 0 :c {:d 42}}})
          a (rv/cursor [:a :b] a-base)
          b (reaction (inc @a))
          c (reaction (dec @a))
          d (reaction (str @b))
          res (rv/atom 0)
          cs (run!
              (reset! res @d))]
      (is (= @res "1"))
      (dispose cs))
    ;; should be broken according to https://github.com/lynaghk/reflex/issues/1
    ;; but isnt
    (let [a-base (rv/atom {:a 0})
          a (rv/cursor [:a] a-base)
          b (reaction (inc @a))
          c (reaction (dec @a))
          d (run! [@b @c])]
      (is (= @d [1 -1]))
      (dispose d))
    (let [a-base (rv/atom 0)
          a (rv/cursor [] a-base)
          b (reaction (inc @a))
          c (reaction (dec @a))
          d (run! [@b @c])
          res (rv/atom 0)]
      (is (= @d [1 -1]))
      (let [e (run! (reset! res @d))]
        (is (= @res [1 -1]))
        (dispose e))
      (dispose d))
    (is (= runs (running)))))

(deftest test-dispose
  (dotimes [x 10]
    (let [runs (running)
          a-base (rv/atom {:a 0 :b 0})
          a (rv/cursor [:a] a-base)
          disposed (rv/atom nil)
          disposed-c (rv/atom nil)
          disposed-cns (rv/atom nil)
          count-b (rv/atom 0)
          b (rv/make-reaction (fn []
                                (swap! count-b inc)
                                (inc @a))
                              :on-dispose #(reset! disposed true))
          c (rv/make-reaction #(if (< @a 1) (inc @b) (dec @a))
                              :on-dispose #(reset! disposed-c true))
          res (rv/atom nil)
          cns (rv/make-reaction #(reset! res @c)
                                :auto-run true
                                :on-dispose #(reset! disposed-cns true))]
      @cns
      (is (= @res 2))
      (is (= (+ 3 runs) (running)))
      (is (= @count-b 1))
      (is (= {:a 0 :b 0} @a-base))
      (reset! a -1)
      (is (= @res 1))
      (is (= @disposed nil))
      (is (= @count-b 2))
      (is (= (+ 3 runs) (running)) "still running")
      (is (= {:a -1 :b 0} @a-base))
      (reset! a 2)
      (is (= @res 1))
      (is (= @disposed true))
      (is (= (+ 2 runs) (running)) "less running count")
      (is (= {:a 2 :b 0} @a-base))

      (reset! disposed nil)
      (reset! a -1)
      ;; This fails sometimes on node. I have no idea why.
      (is (= 1 @res) "should be one again")
      (is (= @disposed nil))
      (is (= {:a -1 :b 0} @a-base))
      (reset! a 2)
      (is (= @res 1))
      (is (= @disposed true))
      (dispose cns)
      (is (= @disposed-c true))
      (is (= @disposed-cns true))
      (is (= {:a 2 :b 0} @a-base))
      (is (= runs (running))))))

(deftest test-on-set
  (let [runs (running)
        a-base (rv/atom {:set 0})
        a (rv/cursor [:set] a-base)
        b (rv/make-reaction #(+ 5 @a)
                            :auto-run true
                            :on-set (fn [oldv newv]
                                      (reset! a (+ 10 newv))))]
    @b
    (is (= 5 @b))
    (is (= {:set 0} @a-base))
    (reset! a 1)
    (is (= 6 @b))
    (is (= {:set 1} @a-base))
    (reset! b 1)
    (is (= 11 @a))
    (is (= 16 @b))
    (dispose b)
    (is (= {:set 11} @a-base))
    (is (= runs (running)))))

