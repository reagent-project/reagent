(ns testreagent
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing)]
                   [reagent.ratom :refer [reaction]]
                   [reagent.debug :refer [dbg println log]])
  (:require [cemerick.cljs.test :as t]
            [reagent.core :as reagent :refer [atom]]
            [reagent.ratom :as rv]))

(defn running [] (rv/running))

(def isClient (not (nil? (try (.-document js/window)
                              (catch js/Object e nil)))))

(def rflush reagent/flush)

(defn add-test-div [name]
  (let [doc js/document
        body (.-body js/document)
        div (.createElement doc "div")]
    (.appendChild body div)
    div))

(defn with-mounted-component [comp f]
  (when isClient
    (let [div (add-test-div "_testreagent")]
      (let [comp (reagent/render-component comp div #(f comp div))]
        (reagent/unmount-component-at-node div)
        (reagent/flush)))))

(defn found-in [re div]
  (let [res (.-innerHTML div)]
    (if (re-find re res)
      true
      (do (println "Not found: " res)
          false))))

(deftest really-simple-test
  (when isClient
    (let [ran (atom 0)
          really-simple (fn []
                          (swap! ran inc)
                          [:div "div in really-simple"])]
      (with-mounted-component [really-simple nil nil]
        (fn [c div]
          (swap! ran inc)
          (is (found-in #"div in really-simple" div))))
      (is (= 2 @ran)))))

(deftest test-simple-callback
  (when isClient
    (let [ran (atom 0)
          comp (reagent/create-class
                {:component-did-mount #(swap! ran inc)
                 :render
                 (fn [this]
                   (let [props (reagent/props this)]
                     (is (map? props))
                     (is (= props ((reagent/argv this) 1)))
                     (is (= 1 (first (reagent/children this))))
                     (is (= 1 (count (reagent/children this))))
                     (swap! ran inc)
                     [:div (str "hi " (:foo props) ".")]))})]
      (with-mounted-component (comp {:foo "you"} 1)
        (fn [C div]
          (swap! ran inc)
          (is (found-in #"hi you" div))))
      (is (= 3 @ran)))))

(deftest test-state-change
  (when isClient
    (let [ran (atom 0)
          comp (reagent/create-class
                {:get-initial-state (fn [] {:foo "initial"})
                 :render
                 (fn []
                   (let [this (reagent/current-component)]
                     (swap! ran inc)
                     [:div (str "hi " (:foo (reagent/state this)))]))})]
      (with-mounted-component (comp)
        (fn [C div]
          (swap! ran inc)
          (is (found-in #"hi initial" div))

          (reagent/replace-state C {:foo "there"})
          (rflush)
          (is (found-in #"hi there" div))

          (reagent/set-state C {:foo "you"})
          (rflush)
          (is (found-in #"hi you" div))))
      (is (= 4 @ran)))))

(deftest test-ratom-change
  (when isClient
    (let [ran (atom 0)
          runs (running)
          val (atom 0)
          secval (atom 0)
          v1 (reaction @val)
          comp (fn []
                 (swap! ran inc)
                 [:div (str "val " @v1 @val @secval)])]
      (with-mounted-component [comp]
        (fn [C div]
          (reagent/flush)
          (is (not= runs (running)))
          (is (found-in #"val 0" div))
          (is (= 1 @ran))

          (reset! secval 1)
          (reset! secval 0)
          (reset! val 1)
          (reset! val 2)
          (reset! val 1)
          (reagent/flush)
          (is (found-in #"val 1" div))
          (is (= 2 @ran))

          ;; should not be rendered
          (reset! val 1)
          (reagent/flush)
          (is (found-in #"val 1" div))
          (is (= 2 @ran))))
      (is (= runs (running)))
      (is (= 2 @ran)))))

(deftest batched-update-test []
  (when isClient
    (let [ran (atom 0)
          v1 (atom 0)
          v2 (atom 0)
          c2 (fn [{val :val}]
               (swap! ran inc)
               (is (= @v1 val))
               [:div @v2])
          c1 (fn []
               (swap! ran inc)
               [:div @v1
                [c2 {:val @v1}]])]
      (with-mounted-component [c1]
        (fn [c div]
          (rflush)
          (is (= @ran 2))
          (swap! v2 inc)
          (is (= @ran 2))
          (rflush)
          (is (= @ran 3))
          (swap! v1 inc)
          (rflush)
          (is (= @ran 5))
          (swap! v2 inc)
          (swap! v1 inc)
          (rflush)
          (is (= @ran 7))
          (swap! v1 inc)
          (swap! v1 inc)
          (swap! v2 inc)
          (rflush)
          (is (= @ran 9)))))))

(deftest init-state-test
  (when isClient
    (let [ran (atom 0)
          really-simple (fn []
                          (let [this (reagent/current-component)]
                            (swap! ran inc)
                            (reagent/set-state this {:foo "foobar"})
                            (fn []
                              [:div (str "this is "
                                         (:foo (reagent/state this)))])))]
      (with-mounted-component [really-simple nil nil]
        (fn [c div]
          (swap! ran inc)
          (is (found-in #"this is foobar" div))))
      (is (= 2 @ran)))))

(deftest shoud-update-test
  (when isClient
    (let [parent-ran (atom 0)
          child-ran (atom 0)
          child-props (atom nil)
          f (fn [])
          f1 (fn [])
          child (fn [p]
                  (swap! child-ran inc)
                  [:div (:val p)])
          parent(fn []
                  (swap! parent-ran inc)
                  [:div "child-foo" [child @child-props]])]
      (with-mounted-component [parent nil nil]
        (fn [c div]
          (rflush)
          (is (= @child-ran 1))
          (is (found-in #"child-foo" div))
          (do (reset! child-props {:style {:display :none}})
              (rflush))
          (is (= @child-ran 2))
          (do (reset! child-props {:style {:display :none}})
              (rflush))
          (is (= @child-ran 2) "keyw is equal")
          (do (reset! child-props {:class :foo}) (rflush))
          (is (= @child-ran 3))
          (do (reset! child-props {:class :foo}) (rflush))
          (is (= @child-ran 3))
          (do (reset! child-props {:class 'foo}) (rflush))
          (is (= @child-ran 4) "symbols are different from keyw")
          (do (reset! child-props {:class 'foo}) (rflush))
          (is (= @child-ran 4) "symbols are equal")
          (do (reset! child-props {:style {:color 'red}}) (rflush))
          (is (= @child-ran 5))
          (do (reset! child-props {:on-change (reagent/partial f)})
              (rflush))
          (is (= @child-ran 6))
          (do (reset! child-props {:on-change (reagent/partial f)})
              (rflush))
          (is (= @child-ran 6))
          (do (reset! child-props {:on-change (reagent/partial f1)})
              (rflush))
          (is (= @child-ran 7)))))))

(deftest dirty-test
  (when isClient
    (let [ran (atom 0)
          state (atom 0)
          really-simple (fn []
                          (swap! ran inc)
                          (if (= @state 1)
                            (reset! state 3))
                          [:div (str "state=" @state)])]
      (with-mounted-component [really-simple nil nil]
        (fn [c div]
          (is (= 1 @ran))
          (is (found-in #"state=0" div))
          (reset! state 1)
          (rflush)
          (is (= 2 @ran))
          (is (found-in #"state=3" div))))
      (is (= 2 @ran)))))

(defn as-string [comp]
  (reagent/render-component-to-string comp))

(deftest to-string-test []
  (let [comp (fn [props]
               [:div (str "i am " (:foo props))])]
    (is (re-find #"i am foobar"
                 (as-string [comp {:foo "foobar"}])))))

(deftest data-aria-test []
  (is (re-find #"data-foo"
               (as-string [:div {:data-foo "x"}])))
  (is (re-find #"aria-foo"
               (as-string [:div {:aria-foo "x"}])))
  (is (not (re-find #"enctype"
                    (as-string [:div {"enc-type" "x"}])))
      "Strings are passed through to React.")
  (is (re-find #"enctype"
               (as-string [:div {"encType" "x"}]))
      "Strings are passed through to React, and have to be camelcase.")
  (is (re-find #"enctype"
               (as-string [:div {:enc-type "x"}]))
      "Strings are passed through to React, and have to be camelcase."))

(deftest dynamic-id-class []
  (is (re-find #"id=.foo"
               (as-string [:div#foo {:class "bar"}])))
  (is (re-find #"class=.foo bar"
               (as-string [:div.foo {:class "bar"}])))
  (is (re-find #"class=.foo bar"
               (as-string [:div.foo.bar])))
  (is (re-find #"id=.foo"
               (as-string [:div#foo.foo.bar])))
  (is (re-find #"class=.xxx bar"
               (as-string [:div#foo.xxx.bar])))
  (is (re-find #"id=.foo"
               (as-string [:div.bar {:id "foo"}])))
  (is (re-find #"id=.foo"
               (as-string [:div.bar.xxx {:id "foo"}])))
  (is (re-find #"id=.foo"
               (as-string [:div#bar {:id "foo"}]))
      "Dynamic id overwrites static"))

(deftest ifn-component []
  (let [comp {:foo [:div "foodiv"]
              :bar [:div "bardiv"]}]
    (is (re-find #"foodiv"
                 (as-string [:div [comp :foo]])))
    (is (re-find #"bardiv"
                 (as-string [:div [comp :bar]])))))

(deftest symbol-string-tag []
  (is (re-find #"foobar"
               (as-string ['div "foobar"])))
  (is (re-find #"foobar"
               (as-string ["div" "foobar"])))
  (is (re-find #"id=.foo"
               (as-string ['div#foo "x"])))
  (is (re-find #"id=.foo"
               (as-string ["div#foo" "x"])))
  (is (re-find #"class=.foo bar"
               (as-string ['div.foo {:class "bar"}])))
  (is (re-find #"class=.foo bar"
               (as-string ["div.foo.bar"])))
  (is (re-find #"id=.foo"
               (as-string ['div#foo.foo.bar]))))

(deftest partial-test []
  (let [p1 (reagent/partial vector 1 2)]
    (is (= (p1 3) [1 2 3]))
    (is (= p1 (reagent/partial vector 1 2)))
    (is (ifn? p1))
    (is (= (reagent/partial vector 1 2) p1))
    (is (not= p1 (reagent/partial vector 1 3)))))
