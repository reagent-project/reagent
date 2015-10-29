(ns reagenttest.testratom
  (:require [cljs.test :as t :refer-macros [is deftest testing]]
            [reagent.ratom :as rv :refer-macros [run! reaction]]
            [reagent.debug :refer-macros [dbg]]
            [reagent.core :as r]))

(defn running []
  (set! rv/debug true)
  (rv/running))

(defn dispose [v]
  (rv/dispose! v))

(defn ratom-perf []
  (dbg "ratom-perf")
  (let [a (rv/atom 0)
        mid (reaction (inc @a))
        res (run!
             (inc @mid))]
    (time (dotimes [x 100000]
            (swap! a inc)))
    (dispose res)))

;; (ratom-perf)

(deftest basic-ratom
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
        const (run!
               (reset! out @res))]
    (is (= @count 1) "constrain ran")
    (is (= @out 2))
    (reset! start 1)
    (is (= @out 3))
    (is (= @count 4))
    (dispose const)
    (is (= (running) runs))))

(deftest double-dependency
  (let [runs (running)
        start (rv/atom 0)
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
    (dispose c3)
    (is (= (running) runs))))

(deftest test-from-reflex
  (let [runs (running)]
    (let [!counter (rv/atom 0)
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
      (dispose co))
    (let [!x (rv/atom 0)
          !co (rv/make-reaction #(inc @!x) :auto-run true)]
      (is (= 1 @!co) "CO has correct value on first deref") 
      (swap! !x inc) 
      (is (= 2 @!co) "CO auto-updates")
      (dispose !co))
    (is (= runs (running)))))


(deftest test-unsubscribe
  (dotimes [x 10]
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
          res (run!
               (if (< @a2 1) @b @c))]
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
    (let [a (rv/atom 0)
          b (reaction (inc @a))
          c (reaction (dec @a))
          d (run! [@b @c])]
      (is (= @d [1 -1]))
      (dispose d))
    (let [a (rv/atom 0)
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
          a (rv/atom 0)
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
      (reset! a -1)
      (is (= @res 1))
      (is (= @disposed nil))
      (is (= @count-b 2))
      (is (= (+ 3 runs) (running)) "still running")
      (reset! a 2)
      (is (= @res 1))
      (is (= @disposed true))
      (is (= (+ 2 runs) (running)) "less running count")

      (reset! disposed nil)
      (reset! a -1)
      ;; This fails sometimes on node. I have no idea why.
      (is (= 1 @res) "should be one again")
      (is (= @disposed nil))
      (reset! a 2)
      (is (= @res 1))
      (is (= @disposed true))
      (dispose cns)
      (is (= @disposed-c true))
      (is (= @disposed-cns true))
      (is (= runs (running))))))

(deftest test-on-set
  (let [runs (running)
        a (rv/atom 0)
        b (rv/make-reaction #(+ 5 @a)
                            :auto-run true
                            :on-set (fn [oldv newv]
                                      (reset! a (+ 10 newv))))]
    @b
    (is (= 5 @b))
    (reset! a 1)
    (is (= 6 @b))
    (reset! b 1)
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

(deftest reset-in-reaction
  (let [runs (running)
        state (rv/atom {})
        c1 (reaction (get-in @state [:data :a]))
        c2 (reaction (get-in @state [:data :b]))
        rxn (rv/make-reaction
             #(let [cc1 @c1
                    cc2 @c2]
                (swap! state assoc :derived (+ cc1 cc2))
                nil)
             :auto-run true)]
    @rxn
    (is (= (:derived @state) 0))
    (swap! state assoc :data {:a 1, :b 2})
    (is (= (:derived @state) 3))
    (swap! state assoc :data {:a 11, :b 22})
    (is (= (:derived @state) 33))
    (dispose rxn)
    (is (= runs (running)))))

;; (deftest catching
;;   (let [runs (running)
;;         a (rv/atom false)
;;         catch-count (atom 0)
;;         b (reaction (if @a (throw {})))
;;         c (run! (try @b (catch js/Object e
;;                           (swap! catch-count inc))))]
;;     (is (= @catch-count 0))
;;     (reset! a false)
;;     (is (= @catch-count 0))
;;     (reset! a true)
;;     (is (= @catch-count 1))))
