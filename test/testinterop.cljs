(ns testinterop
  (:require [cemerick.cljs.test :as t :refer-macros [is deftest]]
            [reagent.debug :refer-macros [dbg]]
            [reagent.interop :refer-macros [jget jset jcall jval]]))

(deftest interop-basic
  (let [o #js {:foo "foo"
               :foobar #js {:bar "bar"}}]
    (is (= "foo" (jget o :foo)))
    (is (= "foo" (jget o [:foo])))
    (is (= "bar" (jget o [:foobar :bar])))

    (jset o :foo "foo1")
    (is (= "foo1" (jget o :foo)))

    (jset o [:foo] "foo2")
    (is (= "foo2" (jget o :foo)))

    (jset o [:foobar :bar] "bar1")
    (is (= "bar1" (jget o [:foobar :bar])))))

(deftest interop-call
  (let [o #js {:bar "bar1"
               :foo (fn [x]
                      (this-as this
                               (str x (jget this :bar))))}
        o2 #js {:o o}]
    (is (= "ybar1" (jcall o :foo "y")))
    (is (= "xxbar1" (jcall o2 [:o :foo] "xx")))
    (is (= "abar1" (-> o2
                       (jget [:o :foo])
                       (jcall [] "a"))))
    
    (is (= "bar1" (jcall o :foo)))
    (is (= "bar1" (jcall o [:foo])))
    (is (= "bar1" (jcall o2 [:o :foo])))

    (jset o :bar "bar2")
    (is (= "bar2" (jcall o :foo)))

    (is (= "1bar2" (jcall (jget o :foo)
                          :call o 1)))

    (is (= "yy" (jcall identity [] "yy")))))

(deftest interop-val
  (set! js/someGlobal "foo")
  (is (= "foo" (jval :someGlobal)))
  (is (nil? (jval :nonExistingGlobal))))
