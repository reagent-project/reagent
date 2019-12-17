(ns reagenttest.testratom
  (:require [clojure.test :as t :refer-macros [is deftest testing]]
            [reagent.ratom :as rv :refer-macros [run! reaction]]
            [reagent.debug :as debug :refer-macros [dbg]]
            [reagent.core :as r]))

(defn fixture [f]
  (r/flush)
  (set! rv/debug true)
  (f)
  (set! rv/debug false))

(t/use-fixtures :once fixture)

(defn running []
  (rv/running))

(def testite 10)

(defn dispose [v]
  (rv/dispose! v))

(def perf-check 0)
(defn ratom-perf []
  (dbg "ratom-perf")
  (set! rv/debug false)
  (dotimes [_ 10]
    (let [nite 100000
          a (rv/atom 0)
          mid (reaction (quot @a 10))
          res (run!
               (inc @mid))]
      (time (dotimes [x nite]
              (swap! a inc)
              (rv/flush!)))
      (dispose res))))

(enable-console-print!)
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
    (r/flush)
    (is (= @out 3))
    (is (<= 2 @count 3))
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
    (r/flush)
    (is (= @c3-count 0))
    (is (= @c3 1))
    (is (= @c3-count 1) "t1")
    (swap! start inc)
    (r/flush)
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
      (r/flush)
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
  (dotimes [x testite]
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
      (is (= (+ 4 runs) (running)))
      (is (= @count-b 1))
      (reset! a -1)
      (r/flush)
      (is (= @res 1))
      (is (= @disposed nil))
      (is (= @count-b 2))
      (is (= (+ 4 runs) (running)) "still running")
      (reset! a 2)
      (r/flush)
      (is (= @res 1))
      (is (= @disposed true))
      (is (= (+ 2 runs) (running)) "less running count")

      (reset! disposed nil)
      (reset! a -1)
      (r/flush)
      ;; This fails sometimes on node. I have no idea why.
      (is (= 1 @res) "should be one again")
      (is (= @disposed nil))
      (reset! a 2)
      (r/flush)
      (is (= @res 1))
      (is (= @disposed true))
      (dispose cns)
      (is (= @disposed-c true))
      (is (= @disposed-cns true))
      (is (= runs (running))))))

(deftest test-add-dispose
  (dotimes [x testite]
    (let [runs (running)
          a (rv/atom 0)
          disposed (rv/atom nil)
          disposed-c (rv/atom nil)
          disposed-cns (rv/atom nil)
          count-b (rv/atom 0)
          b (rv/make-reaction (fn []
                                (swap! count-b inc)
                                (inc @a)))
          c (rv/make-reaction #(if (< @a 1) (inc @b) (dec @a)))
          res (rv/atom nil)
          cns (rv/make-reaction #(reset! res @c)
                                :auto-run true)]
      (rv/add-on-dispose! b (fn [r]
                              (is (= r b))
                              (reset! disposed true)))
      (rv/add-on-dispose! c #(reset! disposed-c true))
      (rv/add-on-dispose! cns #(reset! disposed-cns true))
      @cns
      (is (= @res 2))
      (is (= (+ 4 runs) (running)))
      (is (= @count-b 1))
      (reset! a -1)
      (r/flush)
      (is (= @res 1))
      (is (= @disposed nil))
      (is (= @count-b 2))
      (is (= (+ 4 runs) (running)) "still running")
      (reset! a 2)
      (r/flush)
      (is (= @res 1))
      (is (= @disposed true))
      (is (= (+ 2 runs) (running)) "less running count")

      (reset! disposed nil)
      (reset! a -1)
      (r/flush)
      (is (= 1 @res) "should be one again")
      (is (= @disposed nil))
      (reset! a 2)
      (r/flush)
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

(deftest catching
  (let [runs (running)
        a (rv/atom false)
        catch-count (atom 0)
        b (reaction (if @a (throw (js/Error. "fail"))))
        c (run! (try @b (catch :default e
                          (swap! catch-count inc))))]
    (debug/track-warnings
     (fn []
       (is (= @catch-count 0))
       (reset! a false)
       (r/flush)
       (is (= @catch-count 0))
       (reset! a true)
       (r/flush)
       (is (= @catch-count 1))
       (reset! a false)
       (r/flush)
       (is (= @catch-count 1))))
    (dispose c)
    (is (= runs (running)))))

(deftest test-rswap
  (let [a (atom {:foo 1})]
    (is (nil? (r/rswap! a update-in [:foo] inc)))
    (is (= (:foo @a) 2))
    (is (nil? (r/rswap! a identity)))
    (is (= (:foo @a) 2))
    (is (nil? (r/rswap! a #(assoc %1 :foo %2) 3)))
    (is (= (:foo @a) 3))
    (is (nil? (r/rswap! a #(assoc %1 :foo %3) 0 4)))
    (is (= (:foo @a) 4))
    (is (nil? (r/rswap! a #(assoc %1 :foo %4) 0 0 5)))
    (is (= (:foo @a) 5))
    (is (nil? (r/rswap! a #(assoc %1 :foo %5) 0 0 0 6)))
    (is (= (:foo @a) 6))
    (let [disp (atom nil)
          f (fn [o v]
              (assert (= v :add))
              (if (< (:foo o) 10)
                (do
                  (is (nil? (@disp v)))
                  (update-in o [:foo] inc))
                o))
          _ (reset! disp #(r/rswap! a f %))]
      (@disp :add)
      (is (= (:foo @a) 10)))))

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
    (r/flush)
    (is (= (:derived @state) 3))
    (swap! state assoc :data {:a 11, :b 22})
    (r/flush)
    (is (= (:derived @state) 33))
    (dispose rxn)
    (is (= runs (running)))))

(deftest exception-recover
  (let [runs (running)
        state (rv/atom 1)
        count (rv/atom 0)
        r (run!
           (swap! count inc)
           (when (> @state 1)
             (throw (js/Error. "oops"))))]
    (is (= @count 1))
    (is (thrown? :default (do
                            (swap! state inc)
                            (rv/flush!))))
    (is (= @count 2))
    (swap! state dec)
    (rv/flush!)
    (is (= @count 3))
    (dispose r)
    (is (= runs (running)))))

(deftest exception-recover-indirect
  (let [runs (running)
        state (rv/atom 1)
        count (rv/atom 0)
        ref (reaction
             (when (= @state 2)
               (throw (js/Error. "err"))))
        r (run!
           (swap! count inc)
           @ref)]
    (is (= @count 1))
    (is (thrown? :default (do
                            (swap! state inc)
                            (rv/flush!))))
    (is (= @count 2))
    (is (thrown? :default @ref))
    (swap! state inc)
    (rv/flush!)
    (is (= @count 3))
    (dispose r)
    (is (= runs (running)))))

(deftest exception-side-effect
  (let [runs (running)
        state (r/atom {:val 1})
        rstate (reaction @state)
        spy (atom nil)
        r1 (run! @rstate)
        r2 (let [val (reaction (:val @rstate))]
             (run!
              (reset! spy @val)
              (is (some? @val))))
        r3 (run!
            (when (:error? @rstate)
              (throw (js/Error. "Error detected!"))))]
    (swap! state assoc :val 2)
    (r/flush)
    (swap! state assoc :error? true)
    (is (thrown? :default (r/flush)))
    (r/flush)
    (r/flush)
    (dispose r1)
    (dispose r2)
    (dispose r3)
    (is (= runs (running)))))

(deftest exception-reporting
  (let [runs (running)
        state (r/atom {:val 1})
        rstate (reaction (:val @state))
        r1 (run!
            (when (= @rstate 13)
              (throw (ex-info "fail" nil))))]
    (swap! state assoc :val 13)
    (is (thrown? :default
                 (r/flush)))
    (swap! state assoc :val 2)
    (r/flush)
    (dispose r1)
    (is (= runs (running)))))

(deftest ratom-with-meta
  (let [value {:val 1}
        meta-value {:meta-val 1}
        state (with-meta (r/atom value) meta-value)]
    (is (= (meta state) meta-value))
    (is (= @state value))))
