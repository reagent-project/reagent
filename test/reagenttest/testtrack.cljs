(ns reagenttest.testtrack
  (:require [clojure.test :as t :refer-macros [is deftest testing]]
            [reagent.ratom :as rv :refer [track] :refer-macros [run!]]
            [reagent.debug :as debug]
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

(defn sync []
  (r/flush))

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
    (r/flush)
    (is (= @out 8))
    (is (<= 2 @count 3))
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
    (is (<= 2 @count 3))
    (dispose const)
    (swap! start inc)
    (sync)
    (is (<= 2 @count 3))
    (is (= @const 11))
    (is (<= 3 @count 4))
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
    (sync)
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
    (debug/track-warnings
     (fn []
       (is (= @catch-count 0))
       (reset! a false)
       (sync)
       (is (= @catch-count 0))
       (reset! a true)
       (sync)
       (is (= @catch-count 1))
       (reset! a false)
       (sync)
       (is (= @catch-count 1))))
    (dispose c)
    (is (= runs (running)))))

(deftest track-equality
  (let [f1 (fn [])
        f2 (fn [])
        t r/track]
    (is (= (t f1) (t f1)))
    (is (not= (t f1) (t f2)))
    (is (not= (hash (t f1)) (hash (t f2))))
    (is (= (t f1 "foo") (t f1 "foo")))
    (is (not= (t f2 "foo") (t f1 "foo")))
    (is (not= (t f1 "foo") (t f1 "foobar")))
    (is (= (t f1 "foo" 1) (t f1 "foo" 1)))
    (is (not= (t f1 "foo" 2) (t f1 "foo" 1)))
    (is (= (t f1 2 "foo" 1) (t f1 2 "foo" 1)))
    (is (not= (t f1 2 "foo" 1) (t f2 2 "foo" 1)))
    (is (not= (t f1 2 "foo" 1) (t f1 2 "foo" 3)))
    (is (not= (hash (t f1 2 "foo" 1)) (hash (t f1 2 "foo" 3))))))

(deftest track-identity
  (let [runs (running)
        ts (atom {})
        t r/track
        trigger (r/atom 1)
        f1 (fn [& args]
             (r/with-let [k [:f1 args]
                          _ (is (nil? (@ts k)))
                          _ (swap! ts assoc k k)]
               @trigger
               (finally
                 (is (= k (@ts k)))
                 (swap! ts dissoc k))))
        f2' (fn [& args]
              (r/with-let [k [(t f1) args]
                           _ (is (nil? (@ts k)))
                           _ (swap! ts assoc k k)]
                @trigger
                (finally
                  (is (= k (@ts k)))
                  (swap! ts dissoc k))))
        f2 (fn [& args]
             @(apply t f2' args))
        refs (r/atom nil)
        run (r/track! #(doseq [i @refs]
                         @i))
        check (fn [n & args]
                (reset! refs args)
                (r/flush)
                (is (= (count @ts) n))
                (swap! trigger inc)
                (r/flush)
                (is (= (count @ts) n)))]
    (check 1 (t f1))
    (check 1 (t f1) (t f1))
    (check 2 (t f1) (t f1 1))
    (check 2 (t f1 1) (t f1 1) (t f1 2))
    (check 2 (t f1 1) (t f1 1) (t f2 1))
    (check 0)
    (check 2 (t f2 1) (t f2 1) (t f1 1))
    (check 2 (t f2 2) (t f2 2) (t f1 2))
    (check 2 (t f2 2 3) (t f2 2 3) (t f1 2 3))
    (check 1 (t f2 2 3) (t f2 2 3) (t f2 2 3))
    (check 5 (t f1) (t f1 1) (t f1 2) (t f1 1 2) (t f1 1 2 3))
    (check 4 (t f1) (t f1 1) (t f1 2) (t f1 1 2) (t f1 1 2))

    (r/dispose! run)
    (is (= 0 (count @ts)))
    (is (= runs (running)))))
