(ns reagenttest.testwrap
  (:require [clojure.test :as t :refer-macros [is deftest]]
            [reagent.core :as r]
            [reagenttest.utils :as u :refer [with-mounted-component found-in]]))

(deftest test-wrap-basic
  (let [state (r/atom {:foo 1})
        ws (fn [] (r/wrap (:foo @state)
                          swap! state assoc :foo))]
    (let [w1 (ws) w2 (ws)]
      (is (= @w1 1))
      (is (= w1 w2))
      (reset! w1 1)
      (is (= @w1 1))
      (is (= @w1 @w2))
      (is (not= w1 w2)))

    (let [w1 (ws) w2 (ws)]
      (is (= @w1 1))
      (is (= w1 w2))
      (reset! w2 1)
      (is (= @w2 1))
      (is (= @w1 @w2))
      (is (not= w1 w2))
      (reset! w1 1))

    (let [w1 (ws) w2 (ws)]
      (is (= @w1 1))
      (is (= w1 w2))
      (is (= w2 w1))
      (reset! w1 2)
      (is (= @w1 2))
      (is (= (:foo @state) 2))
      (is (not= @w1 @w2))
      (is (not= w1 w2))
      (is (not= w2 w1))
      (reset! w1 1)
      (is (= (:foo @state) 1)))

    (let [w1 (ws) w2 (ws)]
      (is (= @w1 1))
      (is (= w1 w2))
      (reset! w1 2)
      (reset! w2 2)
      (is (= @w1 2))
      (is (= (:foo @state) 2))
      (is (= @w2 2))
      (is (= @w1 @w2))
      (is (not= w1 w2))
      (reset! w1 1))))

(deftest test-wrap-equality
  (let [a (r/atom 1)
        b (r/atom 2)]
    (is (= (r/wrap @a swap! a assoc :foo)
           (r/wrap @a swap! a assoc :foo)))
    (is (not= (r/wrap @a swap! a assoc :foo)
              (r/wrap @b swap! a assoc :foo)))
    (is (not= (r/wrap @a swap! a assoc :foo)
              (r/wrap @a swap! b assoc :foo)))
    (is (not= (r/wrap @a swap! a assoc :foo)
              (r/wrap @a swap! a identity :foo)))
    (is (not= (r/wrap @a swap! a assoc :foo)
              (r/wrap @a swap! a assoc :bar)))

    (is (= (r/wrap @a update-in [:foo :bar] inc)
           (r/wrap @a update-in [:foo :bar] inc)))

    (is (= (r/wrap @a identity)
           (r/wrap @a identity)))
    (is (not= (r/wrap @a identity)
              (r/wrap @b identity)))

    (is (= (r/wrap @a reset! a)
           (r/wrap @a reset! a)))))

(deftest test-wrap-returns
  (let [n (fn [] :foobar)
        a (r/atom {:k 1})
        b (r/wrap {:k 1} n)]
    (is (not= a b))
    (is (not= b a))
    (is (= (swap! a update-in [:k] inc)
           (swap! b update-in [:k] inc)))
    (is (= @a @b {:k 2}))
    (is (= (swap! a assoc :k 3 :l 4 :m 7 :n 8 :o)
           (swap! b assoc :k 3 :l 4 :m 7 :n 8 :o)))
    (is (= (reset! a 23)
           (reset! b 23)))
    (is (= @a @b))
    (is (= (swap! a inc)
           (swap! b inc)))
    (is (= @a @b 24))))

(deftest test-wrap
  (when r/is-client
    (let [state (r/atom {:foo {:bar {:foobar 1}}})
          ran (r/atom 0)
          grand-state (clojure.core/atom nil)
          grand-child (fn [v]
                        (swap! ran inc)
                        (reset! grand-state v)
                        [:div (str "value:" (:foobar @v) ":")])
          child (fn [v]
                  [grand-child (r/wrap (:bar @v)
                                     swap! v assoc :bar)])
          parent (fn []
                   [child (r/wrap (:foo @state)
                                swap! state assoc :foo)])]
      (t/async done
        (u/with-mounted-component-async [parent] done
          (fn [c div done]
            (u/run-fns-after-render
              (fn []
                (is (found-in #"value:1:" div))
                (is (= @ran 1))

                (reset! @grand-state {:foobar 2}))
              (fn []
                (is (= @state {:foo {:bar {:foobar 2}}}))
                (is (= @ran 2))
                (is (found-in #"value:2:" div))

                (swap! state update-in [:foo :bar] assoc :foobar 3))
              (fn []
                (is (= @ran 3))
                (is (found-in #"value:3:" div))
                (reset! state {:foo {:bar {:foobar 3}}
                               :foo1 {}}))
              (fn []
                (is (= @ran 3))
                (reset! @grand-state {:foobar 3}))
              (fn []
                (is (= @ran 3))

                (reset! state {:foo {:bar {:foobar 2}}
                               :foo2 {}}))
              (fn []
                (is (found-in #"value:2:" div))
                (is (= @ran 4))

                (reset! @grand-state {:foobar 2}))
              (fn []
                (is (found-in #"value:2:" div))
                (is (= @ran 5))

                (reset! state {:foo {:bar {:foobar 4}}})
                (reset! @grand-state {:foobar 4}))
              (fn []
                (is (found-in #"value:4:" div))
                (is (= @ran 6))

                (reset! @grand-state {:foobar 4}))
              (fn []
                (is (found-in #"value:4:" div))
                (is (= @ran 7)))
              done)))))))

(deftest test-cursor
  (when r/is-client
    (let [state (r/atom {:a {:v 1}
                         :b {:v 2}})
          a-count (r/atom 0)
          b-count (r/atom 0)
          derefer (fn derefer [cur count]
                    (swap! count inc)
                    [:div "" @cur])
          comp (fn test-cursor []
                 [:div
                  [derefer (r/cursor state [:a]) a-count]
                  [derefer (r/cursor state [:b]) b-count]])]
      (t/async done
        (u/with-mounted-component-async [comp] done
          (fn [c div done]
            (u/run-fns-after-render
              (fn []
                (is (= @a-count 1))
                (is (= @b-count 1))


                (swap! state update-in [:a :v] inc)
                (is (= @a-count 1)))
              (fn []
                (is (= @a-count 2))
                (is (= @b-count 1))

                (reset! state {:a {:v 2} :b {:v 2}}))
              (fn []
                (is (= @a-count 2))
                (is (= @b-count 1))

                (reset! state {:a {:v 3} :b {:v 2}}))
              (fn []
                (is (= @a-count 3))
                (is (= @b-count 1)))
              done)))))))

(deftest test-fn-cursor
 (let [state (r/atom {:a {:v 1}
                      :b {:v 2}})
       statec (r/cursor state [])
       a-count (r/atom 0)
       b-count (r/atom 0)
       derefer (fn derefer [cur count]
                 [:div "" @cur])
       f (fn [[x y]] (swap! y inc) (get-in @statec x))
       ac (r/cursor f [[:a] a-count])
       bc (r/cursor f [[:b] b-count])
       comp (fn test-cursor []
              [:div
               [derefer ac]
               [derefer bc]])]
   (with-mounted-component [comp]
     (fn [c div]
       (is (= @a-count 1))
       (is (= @b-count 1))

       (swap! state update-in [:a :v] inc)
       (is (= @a-count 1))
       (is (= @b-count 1))

       (r/flush)
       (is (= @a-count 2))
       (is (= @b-count 2))

       (reset! state {:a {:v 2} :b {:v 2}})
       (r/flush)
       (is (= @a-count 2))
       (is (= @b-count 2))

       (reset! state {:a {:v 3} :b {:v 2}})
       (r/flush)
       (is (= @a-count 3))
       (is (= @b-count 3))))))
