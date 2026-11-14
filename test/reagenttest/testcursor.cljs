(ns reagenttest.testcursor
  (:require [clojure.test :as t :refer-macros [is deftest testing]]
            [reagent.ratom :as rv :refer-macros [run! reaction]]
            [reagent.core :as r]))

;; this repeats all the atom tests but using cursors instead

(defn fixture [f]
  (r/flush)
  (set! rv/debug true)
  (f)
  (set! rv/debug false))

(t/use-fixtures :once fixture)

(defn running []
  (rv/running))

(defn dispose [v] (rv/dispose! v))

(def testite 10)

(deftest basic-cursor
  (let [runs (running)
        start-base (rv/atom {:a {:b {:c 0}}})
        start (r/cursor start-base [:a :b :c])
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
    (r/flush)
    (is (= @count 1) "constrain ran")
    (is (= @out 2))
    (reset! start 1)
    (r/flush)
    (is (= @out 3))
    (is (<= 2 @count 3))
    (dispose const)
    (is (= @start-base {:a {:b {:c 1}}}))
    (is (= (running) runs))))

(deftest double-dependency
  (let [runs (running)
        start-base (rv/atom {:a {:b {:c 0}}})
        start (r/cursor start-base [:a :b :c])
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
    (is (= @start-base {:a {:b {:c 1}}}))
    (dispose c3)
    (is (= (running) runs))))

(deftest test-from-reflex
  (let [runs (running)]
    (let [!ctr-base (rv/atom {:x {:y 0 :z 0}})
          !counter (r/cursor !ctr-base [:x :y])
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
      (is (= @!ctr-base {:x {:y 2 :z 0}}))
      (dispose co))
    (let [!x-base (rv/atom {:a {:b 0 :c {:d 0}}})
          !x (r/cursor !x-base [:a :c :d])
          !co (rv/make-reaction #(inc @!x) :auto-run true)]
      (is (= 1 @!co) "CO has correct value on first deref")
      (swap! !x inc)
      (is (= 2 @!co) "CO auto-updates")
      (is (= {:a {:b 0 :c {:d 1}}} @!x-base))
      (dispose !co))
    (is (= runs (running)))))

(deftest test-unsubscribe
  (dotimes [x testite]
    (let [runs (running)
          a-base (rv/atom {:test {:unsubscribe 0 :value 42}})
          a (r/cursor a-base [:test :unsubscribe])
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
          a (r/cursor a-base [:a :b])
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
          a (r/cursor a-base [:a])
          b (reaction (inc @a))
          c (reaction (dec @a))
          d (run! [@b @c])]
      (is (= @d [1 -1]))
      (dispose d))
    (let [a-base (rv/atom 0)
          a (r/cursor a-base [])
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
          a-base (rv/atom {:a 0 :b 0})
          a (r/cursor a-base [:a])
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
      (is (= (+ 6 runs) (running)))
      (is (= @count-b 1))
      (is (= {:a 0 :b 0} @a-base))
      (reset! a -1)
      (r/flush)
      (is (= @res 1))
      (is (= @disposed nil))
      (is (= @count-b 2))
      (is (= (+ 6 runs) (running)) "still running")
      (is (= {:a -1 :b 0} @a-base))
      (reset! a 2)
      (r/flush)
      (is (= @res 1))
      (is (= @disposed true))
      (is (= (+ 4 runs) (running)) "less running count")
      (is (= {:a 2 :b 0} @a-base))

      (reset! disposed nil)
      (reset! a -1)
      (r/flush)
      ;; This fails sometimes on node. I have no idea why.
      (is (= 1 @res) "should be one again")
      (is (= @disposed nil))
      (is (= {:a -1 :b 0} @a-base))
      (reset! a 2)
      (r/flush)
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
        a (r/cursor a-base [:set])
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

(deftest test-equality
  (let [a (r/atom {:foo "bar"})
        a1 (r/atom {:foo "bar"})
        c (r/cursor a [:foo])
        foo (fn
              ([path] (get-in @a path))
              ([path v] (swap! a assoc-in path v)))
        foobar (fn
                 ([path] (get-in @a path))
                 ([path v] (swap! a assoc :foobar v)))
        c2 (r/cursor foo [:foo])
        c3 (r/cursor foobar [:foo])]

    (is (= @c "bar"))
    (is (= @c2 "bar"))
    (is (= @c3 "bar"))
    (is (= c (r/cursor a [:foo])))
    (is (not= c (r/cursor a1 [:foo])))
    (is (not= c (r/cursor a [:foobar])))
    (is (= c2 (r/cursor foo [:foo])))
    (is (= c3 (r/cursor foobar [:foo])))
    (is (= c2 c2))
    (is (not= c2 (r/cursor foobar [:foo])))
    (is (not= c3 (r/cursor foo [:foo])))
    (is (not= c2 (r/cursor foo [:foobar])))

    (reset! c2 "foobar")
    (is (= @c2 "foobar"))
    (is (= @c "foobar"))
    (is (= @a {:foo "foobar"}))

    (reset! c "bar")
    (is (= @c2 "bar"))
    (is (= @c "bar"))
    (is (= @a {:foo "bar"}))
    (is (= c (r/cursor a [:foo])))
    (is (= c2 (r/cursor foo [:foo])))

    (reset! c3 "foo")
    (is (= @a {:foo "bar" :foobar "foo"}))))

(deftest test-wrap
  (let [a (r/atom {:foo "bar"})
        w (r/wrap (:foo @a) swap! a assoc :foo)]
    (is (= @w "bar"))
    (is (= w (r/wrap "bar" swap! a assoc :foo)))
    (is (not= w (r/wrap "foobar" swap! a assoc :foo)))
    (is (not= w (r/wrap "bar" swap! a assoc :foobar)))
    (is (not= w (r/wrap "bar" reset! a assoc :foo)))

    (reset! w "foobar")
    (is (= @w "foobar"))
    (is (= @a {:foo "foobar"}))
    (is (not= w (r/wrap "bar" swap! a assoc :foo)))
    (is (not= w (r/wrap "foobar" swap! a assoc :foo)))))

(deftest cursor-values
  (let [test-atom (r/atom {:a {:b {:c {:d 1}}}})
        test-cursor (r/cursor test-atom [:a :b :c :d])
        test-cursor2 (r/cursor test-atom [])
        runs (running)] ;; nasty edge case

    ;; get the initial values
    (is (= (get-in @test-atom [:a :b :c :d])
           @test-cursor))

    (is (= (get-in @test-atom [])
           @test-cursor2))

    ;; now we update the cursor with a reset
    (reset! test-cursor 2)
    (is (= @test-cursor 2))
    (is (= (get-in @test-atom [:a :b :c :d]) 2))

    (reset! test-cursor2 3)
    (is (= @test-cursor2 3))
    (is (= @test-atom 3))
    (reset! test-atom {:a {:b {:c {:d 1}}}}) ;; restore test-atom

    ;; swap
    (reset! test-cursor {}) ;; empty map
    (swap! test-cursor assoc :z 3)
    (is (= @test-cursor {:z 3}))
    (is (= (get-in @test-atom [:a :b :c :d])
           {:z 3}))

    (reset! test-cursor2 {}) ;; empty map
    (swap! test-cursor2 assoc :z 3)
    (is (= @test-cursor2 {:z 3}))
    (is (= (get-in @test-atom [])
           {:z 3}))

    (reset! test-cursor {}) ;; empty map
    (swap! test-cursor assoc :x 1 :y 2 :z 3 :w)
    (is (= @test-cursor {:x 1 :y 2 :z 3 :w nil}))
    (is (= (get-in @test-atom [:a :b :c :d])
           {:x 1 :y 2 :z 3 :w nil}))

    (is (= runs (running)))))

(deftest cursor-atom-behaviors
  (let [test-atom (r/atom {:a {:b {:c {:d 1}}}})
        test-cursor (r/cursor test-atom [:a :b :c :d])
        witness (r/atom nil)
        runs (running)]
    ;; per the description, reset! should return the new values
    (is (= {}
           (reset! test-cursor {})))

    ;; per the description, swap! should return the new values
    (is (= {:z [1 2 3]}
           (swap! test-cursor assoc :z [1 2 3])))

    ;; watches should behave like with a normal atom
    (reset! test-cursor "old")
    (add-watch test-cursor :w #(reset! witness
                                       {:key %1 :ref %2 :old %3 :new %4}))
    (reset! test-cursor "new") ;; this should trigger the watch function
    (is (= (:key @witness) :w))
    ;; cursor reports that the reaction is the current atom,
    ;; but I guess that's ok
    (is (= (:old @witness) "old"))
    (is (= (:new @witness) "new"))
    (is (= @(:ref @witness) @test-cursor))
    (is (= (:new @witness) "new"))

    (reset! test-atom {:a {:b {:c {:d "newer"}}}})
    ;; watch doesn't run until the value is realized
    (is (= (:new @witness) "new"))
    (is (= @test-cursor "newer"))
    @test-cursor
    (is (= (:old @witness) "new"))
    (is (= (:new @witness) "newer"))
    @test-cursor
    (is (= (:old @witness) "new"))
    (is (= (:new @witness) "newer"))

    ;; can we remove the watch?
    (remove-watch test-cursor :w)
    (reset! test-cursor "removed")
    (is (= (:new @witness) "newer")) ;; shouldn't have changed
    (is (= (running) runs))))

(deftest wrap-atom-behaviors
  (let [test-atom (r/atom "foo")
        test-wrap (r/wrap @test-atom reset! test-atom)
        witness (r/atom nil)]
    ;; per the description, reset! should return the new values
    (is (= {}
           (reset! test-wrap {})))
    (is (= @test-wrap @test-atom))

    ;; per the description, swap! should return the new values
    (is (= {:z [1 2 3]}
           (swap! test-wrap assoc :z [1 2 3])))
    (is (= @test-wrap @test-atom))

    ;; watches should behave like with a normal atom
    (reset! test-wrap "old")
    (add-watch test-wrap :w #(reset! witness
                                     {:key %1 :ref %2 :old %3 :new %4}))
    (reset! test-wrap "new") ;; this should trigger the watch function
    (is (= (:key @witness) :w))
    ;; cursor reports that the reaction is the current atom,
    ;; but I guess that's ok
    (is (= (:old @witness) "old"))
    (is (= (:new @witness) "new"))
    (is (= (:ref @witness) test-wrap))
    (is (= (:new @witness) "new"))

    ;; can we remove the watch?
    (remove-watch test-wrap :w)
    (reset! test-wrap "removed")
    (is (= (:new @witness) "new")) ;; shouldn't have changed
    (is (= @test-wrap @test-atom))))

(deftest test-cursor-swap
  (let [a (r/atom {:b 1})
        b (r/cursor a [:b])]
    (is (= 1 @b))
    (is (= 2 (swap! b inc)))

    (swap! a update-in [:b] inc)
    (is (= 4 (swap! b inc)))
    (is (= 4 @b))))

(deftest test-double-reset
  (let [a (r/atom {:foo {:active? false}})
        c (r/cursor a [:foo])
        f (fn []
            (swap! c assoc :not-pristine true)
            (swap! a update-in [:foo :active?] not)
            (r/flush))
        spy (r/atom nil)
        r (run!
           (reset! spy (:active? @c)))]
    (is (= @spy false))
    (f)
    (is (= @spy true))
    (f)
    (is (= @spy false))
    (dispose r)))

(def assert-enabled? (try (assert false)
                          false
                          (catch :default _ true)))

(deftest cursor-assert-test
  (when assert-enabled?
    (is (thrown-with-msg? :default #"src must be a reactive atom or a function, not nil while attempting to get path: \[:foo :bar\]" (r/cursor nil [:foo :bar])))))

;; https://github.com/reagent-project/reagent/issues/244
;; Cursors are lazy: the cursor value (and so its watches) only updates
;; when the cursor is dereferenced, or changed through the cursor.
(deftest cursor-watch-is-lazy
  (let [base (r/atom {:a 1 :b 1})
        c (r/cursor base [:a])
        calls (atom [])]
    (add-watch c :w (fn [_ _ o n] (swap! calls conj [o n])))

    (swap! base assoc :a 2)
    (r/flush)
    (is (= [] @calls) "base atom change doesn't reach watches of a cursor that isn't dereferenced")

    (is (= 2 @c))
    (is (= [[nil 2]] @calls) "deref updates the cursor state and notifies watches; old value is the last seen value")

    (swap! base assoc :a 3)
    (swap! base assoc :a 4)
    (is (= [[nil 2]] @calls))
    (is (= 4 @c))
    (is (= [[nil 2] [2 4]] @calls) "intermediate values are not seen")

    (reset! c 5)
    (is (= [[nil 2] [2 4] [4 5]] @calls) "changes through the cursor notify watches immediately")
    (swap! c inc)
    (is (= [[nil 2] [2 4] [4 5] [5 6]] @calls))
    (is (= {:a 6 :b 1} @base))))

(deftest cursor-watch-with-reactive-deref
  (let [runs (running)
        base (r/atom {:a 1 :b 1})
        c (r/cursor base [:a])
        calls (atom [])
        _ (add-watch c :w (fn [_ _ o n] (swap! calls conj [o n])))
        ;; Keep the cursor dereferenced in a reactive context
        t (r/track! (fn [] @c))]
    (is (= [[nil 1]] @calls))

    (swap! base assoc :a 2)
    (is (= [[nil 1]] @calls) "notification happens on the next flush, not synchronously")
    (r/flush)
    (is (= [[nil 1] [1 2]] @calls))

    (swap! base assoc :b 2)
    (r/flush)
    (is (= [[nil 1] [1 2]] @calls) "changes to other paths don't notify")

    (swap! base assoc :a 3)
    (swap! base assoc :a 4)
    (r/flush)
    (is (= [[nil 1] [1 2] [2 4]] @calls) "changes are batched until flush")

    (r/dispose! t)

    (swap! base assoc :a 5)
    (r/flush)
    (is (= [[nil 1] [1 2] [2 4]] @calls) "after dispose! the cursor is lazy again")

    (remove-watch c :w)
    (is (= runs (running)))))

(deftest cursor-change-alternatives
  (testing "track! that derefs the cursor"
    (let [runs (running)
          base (r/atom {:a 1 :b 1})
          c (r/cursor base [:a])
          seen (atom [])
          t (r/track! (fn [] (swap! seen conj @c)))]
      (is (= [1] @seen))
      (swap! base assoc :a 2)
      (r/flush)
      (swap! base assoc :b 2)
      (r/flush)
      (is (= [1 2] @seen))
      (r/dispose! t)
      (is (= runs (running)))))

  (testing "watch the base atom directly"
    (let [base (r/atom {:a 1 :b 1})
          seen (atom [])]
      (add-watch base :a-changed
                 (fn [_ _ old new]
                   (let [o (get-in old [:a])
                         n (get-in new [:a])]
                     (when-not (identical? o n)
                       (swap! seen conj [o n])))))
      (swap! base assoc :a 2)
      (swap! base assoc :b 2)
      (swap! base assoc :a 3)
      (is (= [[1 2] [2 3]] @seen) "synchronous, sees every change")
      (remove-watch base :a-changed))))
