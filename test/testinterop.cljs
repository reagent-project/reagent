(ns testinterop
  (:require [cemerick.cljs.test :as t :refer-macros [is deftest]]
            [reagent.debug :refer-macros [dbg]]
            [reagent.interop :refer-macros [get. set. call. jval]]))

(deftest interop-basic
  (let [o #js {:foo "foo"
               :foobar #js {:bar "bar"}}]
    (is (= "foo" (get. o :foo)))
    (is (= "foo" (get. o [:foo])))
    (is (= "bar" (get. o [:foobar :bar])))

    (set. o :foo "foo1")
    (is (= "foo1" (get. o :foo)))

    (set. o [:foo] "foo2")
    (is (= "foo2" (get. o :foo)))

    (set. o [:foobar :bar] "bar1")
    (is (= "bar1" (get. o [:foobar :bar])))))

(deftest interop-call
  (let [o #js {:bar "bar1"
               :foo (fn [x]
                      (this-as this
                               (str x (get. this :bar))))}
        o2 #js {:o o}]
    (is (= "ybar1" (call. o :foo "y")))
    (is (= "xxbar1" (call. o2 [:o :foo] "xx")))
    (is (= "abar1" (-> o2
                       (get. [:o :foo])
                       (call. [] "a"))))
    
    (is (= "bar1" (call. o :foo)))
    (is (= "bar1" (call. o [:foo])))
    (is (= "bar1" (call. o2 [:o :foo])))

    (set. o :bar "bar2")
    (is (= "bar2" (call. o :foo)))

    (is (= "1bar2" (call. (get. o :foo)
                          :call o 1)))

    (is (= "yy" (call. identity [] "yy")))))

(deftest interop-val
  (set! js/someGlobal "foo")
  (is (= "foo" (jval :someGlobal)))
  (is (nil? (jval :nonExistingGlobal))))
