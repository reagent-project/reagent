(ns testinterop
  (:require [cemerick.cljs.test :as t :refer-macros [is deftest]]
            [reagent.debug :refer-macros [dbg]]
            [reagent.interop :refer-macros [.' .! fvar fvar?]]))


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
  (set! f (fn [& args] (into ["foo"] args)))
  (let [f' (fvar f)]
    (is (= ["foo"] (f')))
    (is (= ["foo" 1] (f' 1)))
    (is (= ["foo" 1 2] (f' 1 2)))
    (is (= ["foo" 1 2 3] (f' 1 2 3)))

    (set! f (fn [] "foobar"))
    (is (= "foobar" (f')))

    (is (identical? f' (fvar f)))

    (is (fvar? f'))
    (is (not (fvar? f)))
    (is (fn? f'))))
