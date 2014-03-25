(ns testinterop
  (:require [cemerick.cljs.test :as t :refer-macros [is deftest]]
            [reagent.debug :refer-macros [dbg]]
            [reagent.interop :refer-macros [.' .! fvar fvar? oget oset odo]]))

(deftest interop-basic
  (let [o #js{:foo "foo"
              :foobar #js{:bar "bar"}
              :bar-foo "barfoo"}]
    (is (= "foo" (oget o :foo)))
    (is (= "bar" (oget o :foobar :bar)))
    (is (= "barfoo" (oget o :bar-foo)))

    (oset o :foo "foo1")
    (is (= "foo1" (oget o :foo)))

    (oset o :foo "foo2")
    (is (= "foo2" (oget o :foo)))

    (oset o :foobar :bar "bar1")
    (is (= "bar1" (oget o :foobar :bar)))))

(deftest interop-call
  (let [o #js{:bar "bar1"
              :foo (fn [x]
                     (this-as this
                              (str x (oget this :bar))))}
        o2 #js{:o o}]
    (is (= "ybar1" (odo o :foo "y")))
    (is (= "xxbar1" (odo o2 [:o :foo] "xx")))
    (is (= "abar1" (-> o2
                       (oget :o :foo)
                       (odo [] "a"))))

    (is (= "bar1" (odo o :foo)))
    (is (= "bar1" (odo o [:foo])))
    (is (= "bar1" (odo o2 [:o :foo])))

    (oset o :bar "bar2")
    (is (= "bar2" (odo o :foo)))

    (is (= "1bar2" (odo (oget o :foo)
                        :call o 1)))

    (is (= "yy" (odo identity [] "yy")))))

(deftest iterop-quote
  (let [o #js{:foo "foo"
              :foobar #js{:bar "bar"}
              :bar-foo "barfoo"}]
    (is (= "foo" (.' o :foo)))
    (is (= "bar" (.' o :foobar.bar)))
    (is (= "barfoo" (.' o :bar-foo)))

    (is (= "foo" (.' o -foo)))
    (is (= "bar" (.' o -foobar.bar)))
    (is (= "barfoo" (.' o -bar-foo)))

    (.! o :foo "foo1")
    (is (= "foo1" (.' o :foo)))

    (.! o -foo "foo2")
    (is (= "foo2" (.' o -foo)))

    (.! o :foobar.bar "bar1")
    (is (= "bar1" (.' o :foobar.bar)))

    (.! o -foobar.bar "bar1")
    (is (= "bar1" (.' o -foobar.bar)))))

(deftest interop-quote-call
  (let [o #js{:bar "bar1"
              :foo (fn [x]
                     (this-as this
                              (str x (.' this :bar))))}
        o2 #js{:o o}]
    (is (= "ybar1" (.' o foo "y")))
    (is (= "xxbar1" (.' o2 o.foo "xx")))
    (is (= "abar1" (-> o2
                       (.' :o)
                       (.' foo "a"))))

    (is (= "bar1" (.' o foo)))
    (is (= "bar1" (.' o2 o.foo)))

    (.! o :bar "bar2")
    (is (= "bar2" (.' o foo)))

    (is (= "1bar2" (.' (.' o :foo)
                       call o 1)))))

(def f nil)

(deftest interop-fvar
  (set! f (fn [] "foo"))
  (let [f' (fvar f)]
    (is (= "foo" (f')))

    (set! f (fn [] "foobar"))
    (is (= "foobar" (f')))

    (is (identical? f' (fvar f)))))

