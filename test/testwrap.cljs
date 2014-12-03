(ns testwrap
  (:require [cemerick.cljs.test :as t :refer-macros [is deftest]]
            [reagent.debug :refer-macros [dbg println log]]
            [reagent.core :as r :refer [atom wrap]]))

(defn add-test-div [name]
  (let [doc js/document
        body (.-body js/document)
        div (.createElement doc "div")]
    (.appendChild body div)
    div))

(defn with-mounted-component [comp f]
  (when r/is-client
    (let [div (add-test-div "_testreagent")]
      (let [comp (r/render-component comp div #(f comp div))]
        (r/unmount-component-at-node div)
        (r/flush)))))

(defn found-in [re div]
  (let [res (.-innerHTML div)]
    (if (re-find re res)
      true
      (do (println "Not found: " res)
          false))))

(deftest test-wrap-basic
  (let [state (atom {:foo 1})
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
      (reset! w1 2)
      (is (= @w1 2))
      (is (= (:foo @state) 2))
      (is (not= @w1 @w2))
      (is (not= w1 w2))
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

(deftest test-wrap
  (when r/is-client
    (let [state (atom {:foo {:bar {:foobar 1}}})
          ran (atom 0)
          grand-state (clojure.core/atom nil)
          grand-child (fn [v]
                        (swap! ran inc)
                        (reset! grand-state v)
                        [:div (str "value:" (:foobar @v) ":")])
          child (fn [v]
                  [grand-child (wrap (:bar @v)
                                     swap! v assoc :bar)])
          parent (fn []
                   [child (wrap (:foo @state)
                                swap! state assoc :foo)])]
      (with-mounted-component [parent]
        (fn [c div]
          (is (found-in #"value:1:" div))
          (is (= @ran 1))

          (reset! @grand-state {:foobar 2})
          (is (= @state {:foo {:bar {:foobar 2}}}))
          (r/flush)
          (is (= @ran 2))
          (is (found-in #"value:2:" div))

          (swap! state update-in [:foo :bar] assoc :foobar 3)
          (r/flush)
          (is (= @ran 3))
          (is (found-in #"value:3:" div))

          (reset! state {:foo {:bar {:foobar 3}}
                         :foo1 {}})
          (r/flush)
          (is (= @ran 3))

          (reset! @grand-state {:foobar 3})
          (r/flush)
          (is (= @ran 3))

          (reset! state {:foo {:bar {:foobar 2}}
                         :foo2 {}})
          (r/flush)
          (is (found-in #"value:2:" div))
          (is (= @ran 4))

          (reset! @grand-state {:foobar 2})
          (r/flush)
          (is (found-in #"value:2:" div))
          (is (= @ran 5))

          (reset! state {:foo {:bar {:foobar 4}}})
          (reset! @grand-state {:foobar 4})
          (r/flush)
          (is (found-in #"value:4:" div))
          (is (= @ran 6))

          (reset! @grand-state {:foobar 4})
          (r/flush)
          (is (found-in #"value:4:" div))
          (is (= @ran 7)))))))
