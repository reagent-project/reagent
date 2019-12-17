(ns reagenttest.testreagent
  (:require [clojure.test :as t :refer-macros [is deftest testing]]
            [react :as react]
            [reagent.ratom :as rv :refer-macros [reaction]]
            [reagent.debug :as debug :refer-macros [dev?]]
            [reagent.core :as r]
            [reagent.dom.server :as server]
            [reagenttest.utils :as u :refer [with-mounted-component found-in]]
            [clojure.string :as string]
            [goog.string :as gstr]
            [goog.object :as gobj]
            [prop-types :as prop-types]))

(def tests-done (atom {}))

(t/use-fixtures :once
                {:before (fn []
                           (set! rv/debug true))
                 :after  (fn []
                           (set! rv/debug false))})

(defn running [] (rv/running))

(def isClient r/is-client)

(def rflush r/flush)

(defn rstr [react-elem]
  (server/render-to-static-markup react-elem))

(defn log-error [& f]
  (debug/error (apply str f)))

(defn wrap-capture-console-error [f]
  (fn []
    (let [org js/console.error]
      (set! js/console.error log-error)
      (try
        (f)
        (finally
          (set! js/console.error org))))))

(deftest really-simple-test
  (when (and isClient
             (not (:really-simple-test @tests-done)))
    (swap! tests-done assoc :really-simple-test true)
    (let [ran (r/atom 0)
          really-simple (fn []
                          (swap! ran inc)
                          [:div "div in really-simple"])]
      (with-mounted-component [really-simple nil nil]
        (fn [c div]
          (swap! ran inc)
          (is (found-in #"div in really-simple" div))
          (r/flush)
          (is (= 2 @ran))
          (r/force-update-all)
          (is (= 3 @ran))))
      (is (= 3 @ran)))))

(deftest test-simple-callback
  (when isClient
    (let [ran (r/atom 0)
          comp (r/create-class
                {:component-did-mount #(swap! ran inc)
                 :render
                 (fn [this]
                   (let [props (r/props this)]
                     (is (map? props))
                     (is (= props ((r/argv this) 1)))
                     (is (= 1 (first (r/children this))))
                     (is (= 1 (count (r/children this))))
                     (swap! ran inc)
                     [:div (str "hi " (:foo props) ".")]))})]
      (with-mounted-component [comp {:foo "you"} 1]
        (fn [C div]
          (swap! ran inc)
          (is (found-in #"hi you" div))))
      (is (= 3 @ran)))))

(deftest test-state-change
  (when isClient
    (let [ran (r/atom 0)
          self (r/atom nil)
          comp (r/create-class
                {:get-initial-state (fn [] {:foo "initial"})
                 :reagent-render
                 (fn []
                   (let [this (r/current-component)]
                     (reset! self this)
                     (swap! ran inc)
                     [:div (str "hi " (:foo (r/state this)))]))})]
      (with-mounted-component [comp]
        (fn [C div]
          (swap! ran inc)
          (is (found-in #"hi initial" div))

          (r/replace-state @self {:foo "there"})
          (r/state @self)

          (rflush)
          (is (found-in #"hi there" div))

          (r/set-state @self {:foo "you"})
          (rflush)
          (is (found-in #"hi you" div))))
      (is (= 4 @ran)))))

(deftest test-ratom-change
  (when isClient
    (let [ran (r/atom 0)
          runs (running)
          val (r/atom 0)
          secval (r/atom 0)
          v1-ran (atom 0)
          v1 (reaction (swap! v1-ran inc) @val)
          comp (fn []
                 (swap! ran inc)
                 [:div (str "val " @v1 @val @secval)])]
      (with-mounted-component [comp]
        (fn [C div]
          (r/flush)
          (is (not= runs (running)))
          (is (found-in #"val 0" div))
          (is (= 1 @ran))

          (reset! secval 1)
          (reset! secval 0)
          (reset! val 1)
          (reset! val 2)
          (reset! val 1)
          (is (= 1 @ran))
          (is (= 1 @v1-ran))
          (r/flush)
          (is (found-in #"val 1" div))
          (is (= 2 @ran) "ran once more")
          (is (= 2 @v1-ran))

          ;; should not be rendered
          (reset! val 1)
          (is (= 2 @v1-ran))
          (r/flush)
          (is (= 2 @v1-ran))
          (is (found-in #"val 1" div))
          (is (= 2 @ran) "did not run")))
      (is (= runs (running)))
      (is (= 2 @ran)))))

(deftest batched-update-test []
  (when isClient
    (let [ran (r/atom 0)
          v1 (r/atom 0)
          v2 (r/atom 0)
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
    (let [ran (r/atom 0)
          really-simple (fn []
                          (let [this (r/current-component)]
                            (swap! ran inc)
                            (r/set-state this {:foo "foobar"})
                            (fn []
                              [:div (str "this is "
                                         (:foo (r/state this)))])))]
      (with-mounted-component [really-simple nil nil]
        (fn [c div]
          (swap! ran inc)
          (is (found-in #"this is foobar" div))))
      (is (= 2 @ran)))))

(deftest shoud-update-test
  (when (and isClient
             (not (:should-update-test @tests-done)))
    (swap! tests-done assoc :should-update-test true)
    (let [parent-ran (r/atom 0)
          child-ran (r/atom 0)
          child-props (r/atom nil)
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
          (reset! child-props {:style {:display :none}})
          (rflush)
          (is (= @child-ran 2))
          (reset! child-props {:style {:display :none}})
          (rflush)
          (is (= @child-ran 2) "keyw is equal")
          (reset! child-props {:class :foo}) (rflush)
          (is (= @child-ran 3))
          (reset! child-props {:class :foo}) (rflush)
          (is (= @child-ran 3))
          (reset! child-props {:class 'foo})
          (is (= @child-ran 4) "symbols are different from keyw")
          (reset! child-props {:class 'foo})
          (is (= @child-ran 4) "symbols are equal")
          (reset! child-props {:style {:color 'red}})
          (is (= @child-ran 5))
          (reset! child-props {:on-change (r/partial f)})
          (rflush)
          (is (= @child-ran 6))
          (reset! child-props {:on-change (r/partial f)})
          (rflush)
          (is (= @child-ran 6))
          (reset! child-props {:on-change (r/partial f1)})
          (rflush)
          (is (= @child-ran 7))

          (r/force-update-all)
          (is (= @child-ran 8)))))))

(deftest dirty-test
  (when isClient
    (let [ran (r/atom 0)
          state (r/atom 0)
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
  (server/render-to-static-markup comp))

(deftest to-string-test []
  (let [comp (fn [props]
               [:div (str "i am " (:foo props))])]
    (is (re-find #"i am foobar"
                 (as-string [comp {:foo "foobar"}])))))

(deftest data-aria-test []
  (is (re-find #"data-foo"
               (as-string [:div {:data-foo "x"}])))
  (is (re-find #"aria-labelledby"
               (as-string [:div {:aria-labelledby "x"}])))
  ;; Skip test: produces warning in new React
  ;; (is (not (re-find #"enctype"
  ;;                   (as-string [:div {"enc-type" "x"}])))
  ;;     "Strings are passed through to React.")
  ;; FIXME: For some reason UMD module returns everything in
  ;; lowercase, and CommonJS with upper T
  (is (re-find #"enc[tT]ype"
               (as-string [:div {"encType" "x"}]))
      "Strings are passed through to React, and have to be camelcase.")
  (is (re-find #"enc[tT]ype"
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

(defmulti my-div :type)
(defmethod my-div :fooish [child] [:div.foo (:content child)])
(defmethod my-div :barish [child] [:div.bar (:content child)])

(deftest ifn-component []
  (let [comp {:foo [:div "foodiv"]
              :bar [:div "bardiv"]}]
    (is (re-find #"foodiv"
                 (as-string [:div [comp :foo]])))
    (is (re-find #"bardiv"
                 (as-string [:div [comp :bar]])))
    (is (re-find #"class=.foo"
                 (as-string [my-div {:type :fooish :content "inner"}])))))

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
  (let [p1 (r/partial vector 1 2)]
    (is (= (p1 3) [1 2 3]))
    (is (= p1 (r/partial vector 1 2)))
    (is (ifn? p1))
    (is (= (r/partial vector 1 2) p1))
    (is (not= p1 (r/partial vector 1 3)))
    (is (= (hash p1) (hash (r/partial vector 1 2))))))

(deftest test-null-component
  (let [null-comp (fn [do-show]
                    (when do-show
                      [:div "div in test-null-component"]))]
    (is (not (re-find #"test-null-component"
                      (as-string [null-comp false]))))
    (is (re-find #"test-null-component"
                 (as-string [null-comp true])))))

(deftest test-static-markup
  (is (= "<div>foo</div>"
         (rstr [:div "foo"])))
  (is (= "<div class=\"bar\"><p>foo</p></div>"
         (rstr [:div.bar [:p "foo"]])))
  (is (= "<div class=\"bar\"><p>foobar</p></div>"
         (rstr [:div.bar {:dangerously-set-inner-HTML
                          {:__html "<p>foobar</p>"}} ]))))

(deftest test-return-class
  (when isClient
    (let [ran (r/atom 0)
          top-ran (r/atom 0)
          comp (fn []
                 (swap! top-ran inc)
                 (r/create-class
                  {:component-did-mount #(swap! ran inc)
                   :render
                   (fn [this]
                     (let [props (r/props this)]
                       (is (map? props))
                       (is (= props ((r/argv this) 1)))
                       (is (= 1 (first (r/children this))))
                       (is (= 1 (count (r/children this))))
                       (swap! ran inc)
                       [:div (str "hi " (:foo props) ".")]))}))
          prop (r/atom {:foo "you"})
          parent (fn [] [comp @prop 1])]
      (with-mounted-component [parent]
        (fn [C div]
          (swap! ran inc)
          (is (found-in #"hi you" div))
          (is (= 1 @top-ran))
          (is (= 3 @ran))

          (swap! prop assoc :foo "me")
          (r/flush)
          (is (found-in #"hi me" div))
          (is (= 1 @top-ran))
          (is (= 4 @ran)))))))

(deftest test-return-class-fn
  (when isClient
    (let [ran (r/atom 0)
          top-ran (r/atom 0)
          comp (fn []
                 (swap! top-ran inc)
                 (r/create-class
                  {:component-did-mount #(swap! ran inc)
                   :reagent-render
                   (fn [p a]
                     (is (= 1 a))
                     (swap! ran inc)
                     [:div (str "hi " (:foo p) ".")])}))
          prop (r/atom {:foo "you"})
          parent (fn [] [comp @prop 1])]
      (with-mounted-component [parent]
        (fn [C div]
          (swap! ran inc)
          (is (found-in #"hi you" div))
          (is (= 1 @top-ran))
          (is (= 3 @ran))

          (swap! prop assoc :foo "me")
          (r/flush)
          (is (found-in #"hi me" div))
          (is (= 1 @top-ran))
          (is (= 4 @ran)))))))

(deftest test-create-element
  (let [ae r/as-element
        ce r/create-element]
    (is (= (rstr (ae [:div]))
           (rstr (ce "div"))))
    (is (= (rstr (ae [:div]))
           (rstr (ce "div" nil))))
    (is (= (rstr (ae [:div "foo"]))
           (rstr (ce "div" nil "foo"))))
    (is (= (rstr (ae [:div "foo" "bar"]))
           (rstr (ce "div" nil "foo" "bar"))))
    (is (= (rstr (ae [:div "foo" "bar" "foobar"]))
           (rstr (ce "div" nil "foo" "bar" "foobar"))))

    (is (= (rstr (ae [:div.foo "bar"]))
           (rstr (ce "div" #js{:className "foo"} "bar"))))

    (is (= (rstr (ae [:div [:div "foo"]]))
           (rstr (ce "div" nil (ce "div" nil "foo")))))
    (is (= (rstr (ae [:div [:div "foo"]]))
           (rstr (ce "div" nil (ae [:div "foo"])))))
    (is (= (rstr (ae [:div [:div "foo"]]))
           (rstr (ae [:div (ce "div" nil "foo")]))))))

(def ndiv (let [cmp (fn [])]
            (gobj/extend
              (.-prototype cmp)
              (.-prototype react/Component)
              #js {:render (fn []
                             (this-as
                               this
                               (r/create-element
                                 "div" #js {:className (.. this -props -className)}
                                 (.. this -props -children))))})
            (gobj/extend cmp react/Component)
            cmp))

(deftest test-adapt-class
  (let [d1 (r/adapt-react-class ndiv)
        d2 (r/adapt-react-class "div")]
    (is (= (rstr [:div])
           (rstr [d1])))
    (is (= (rstr [:div "a"])
           (rstr [d1 "a"])))
    (is (= (rstr [:div "a" "b"])
           (rstr [d1 "a" "b"])))
    (is (= (rstr [:div.foo "a"])
           (rstr [d1 {:class "foo"} "a"])))
    (is (= (rstr [:div "a" "b" [:div "c"]])
           (rstr [d1 "a" "b" [:div "c"]])))

    (is (= (rstr [:div])
           (rstr [d2])))
    (is (= (rstr [:div "a"])
           (rstr [d2 "a"])))
    (is (= (rstr [:div "a" "b"])
           (rstr [d2 "a" "b"])))
    (is (= (rstr [:div.foo "a"])
           (rstr [d2 {:class "foo"} "a"])))
    (is (= (rstr [:div "a" "b" [:div "c"]])
           (rstr [d2 "a" "b" [:div "c"]])))))

(deftest test-adapt-class-2
  (let [d1 ndiv
        d2 "div"]
    (is (= (rstr [:div])
           (rstr [:> d1])))
    (is (= (rstr [:div "a"])
           (rstr [:> d1 "a"])))
    (is (= (rstr [:div "a" "b"])
           (rstr [:> d1 "a" "b"])))
    (is (= (rstr [:div.foo "a"])
           (rstr [:> d1 {:class "foo"} "a"])))
    (is (= (rstr [:div "a" "b" [:div "c"]])
           (rstr [:> d1 "a" "b" [:div "c"]])))

    (is (= (rstr [:div])
           (rstr [:> d2])))
    (is (= (rstr [:div "a"])
           (rstr [:> d2 "a"])))
    (is (= (rstr [:div "a" "b"])
           (rstr [:> d2 "a" "b"])))
    (is (= (rstr [:div.foo "a"])
           (rstr [:> d2 {:class "foo"} "a"])))
    (is (= (rstr [:div "a" "b" [:div "c"]])
           (rstr [:> d2 "a" "b" [:div "c"]])))))


(deftest test-reactize-component
  (let [ae r/as-element
        ce r/create-element
        a (atom nil)
        c1r (fn reactize [p & args]
              (reset! a args)
              [:p "p:" (:a p) (:children p)])
        c1 (r/reactify-component c1r)]
    (is (= (rstr [:p "p:a"])
           (rstr (ce c1 #js{:a "a"}))))
    (is (= @a nil))
    (is (= (rstr [:p "p:"])
           (rstr (ce c1 #js{:a nil}))))
    (is (= (rstr [:p "p:"])
           (rstr (ce c1 nil))))

    (is (= (rstr [:p "p:a" [:b "b"]])
           (rstr (ce c1 #js{:a "a"}
                     (ae [:b "b"])))))
    (is (= @a nil))
    (is (= (rstr [:p "p:a" [:b "b"] [:i "i"]])
           (rstr (ce c1 #js{:a "a"}
                     (ae [:b "b"])
                     (ae [:i "i"])))))
    (is (= @a nil))))

(deftest test-keys
  (let [a nil ;; (r/atom "a")
        c (fn key-tester []
            [:div
             (for [i (range 3)]
               ^{:key i} [:p i (some-> a deref)])
             (for [i (range 3)]
               [:p {:key i} i (some-> a deref)])])
        w (debug/track-warnings
            #(with-mounted-component [c]
               (fn [c div])))]
    (is (empty? (:warn w))))

  (when r/is-client
    (testing "Check warning text can be produced even if hiccup contains function literals"
      (let [c (fn key-tester []
                [:div
                 (for [i (range 3)]
                   ^{:key nil}
                   [:button {:on-click #(js/console.log %)}])])
            w (debug/track-warnings
                (wrap-capture-console-error
                  #(with-mounted-component [c]
                     (fn [c div]))))]
        (if (debug/dev?)
          (is (= "Warning: Every element in a seq should have a unique :key: ([:button {:on-click #object[Function]}] [:button {:on-click #object[Function]}] [:button {:on-click #object[Function]}])\n (in reagenttest.testreagent.key_tester)"
                 (first (:warn w)))))))))

(deftest test-extended-syntax
  (is (= (rstr [:p>b "foo"])
         "<p><b>foo</b></p>"))
  (is (= (rstr [:p.foo>b "x"])
         (rstr [:p.foo [:b "x"]])))
  (is (= (rstr [:div.foo>p.bar.foo>b.foobar "xy"])
         (rstr [:div.foo [:p.bar.foo [:b.foobar "xy"]]])))
  (is (= (rstr [:div.foo>p.bar.foo>b.foobar {} "xy"])
         (rstr [:div.foo [:p.bar.foo [:b.foobar "xy"]]])))
  (is (= (rstr [:div>p.bar.foo>a.foobar {:href "href"} "xy"])
         (rstr [:div [:p.bar.foo [:a.foobar {:href "href"} "xy"]]]))))

(deftest extended-syntax-metadata
  (when r/is-client
    (let [comp (fn []
                 [:div
                  (for [k [1 2]]
                    ^{:key k} [:div>div "a"])])]
      (with-mounted-component [comp]
        (fn [c div]
          ;; Just make sure this doesn't print a debug message
          )))))

(deftest test-class-from-collection
  (is (= (rstr [:p {:class ["a" "b" "c" "d"]}])
         (rstr [:p {:class "a b c d"}])))
  (is (= (rstr [:p {:class ["a" nil "b" false "c" nil]}])
         (rstr [:p {:class "a b c"}])))
  (is (= (rstr [:p {:class '("a" "b" "c")}])
         (rstr [:p {:class "a b c"}])))
  (is (= (rstr [:p {:class #{"a" "b" "c"}}])
         (rstr [:p {:class "a b c"}]))))

(deftest class-different-types
  (testing "named values are supported"
    (is (= (rstr [:p {:class :a}])
           (rstr [:p {:class "a"}])))
    (is (= (rstr [:p.a {:class :b}])
           (rstr [:p {:class "a b"}])))
    (is (= (rstr [:p.a {:class 'b}])
           (rstr [:p {:class "a b"}])))
    (is (= (rstr [:p {:class [:a :b]}])
           (rstr [:p {:class "a b"}])))
    (is (= (rstr [:p {:class ['a :b]}])
           (rstr [:p {:class "a b"}]))))

  (testing "non-named values like numbers"
    (is (= (rstr [:p {:class [1 :b]}])
           (rstr [:p {:class "1 b"}]))))

  (testing "falsey values are filtered from collections"
    (is (= (rstr [:p {:class [:a :b false nil]}])
           (rstr [:p {:class "a b"}])))) )

(deftest test-force-update
  (let [v (atom {:v1 0
                 :v2 0})
        comps (atom {})
        c1 (fn []
             (swap! comps assoc :c1 (r/current-component))
             [:p "" (swap! v update-in [:v1] inc)])
        c2 (fn []
             (swap! comps assoc :c2 (r/current-component))
             [:div "" (swap! v update-in [:v2] inc)
              [c1]])
        state (r/atom 0)
        spy (r/atom 0)
        t (fn [] @state)
        t1 (fn [] @(r/track t))
        c3 (fn []
             (swap! comps assoc :c3 (r/current-component))
             [:div "" (reset! spy @(r/track t1))])]
    (with-mounted-component [c2]
      (fn [c div]
        (is (= @v {:v1 1 :v2 1}))

        (r/force-update (:c2 @comps))
        (is (= @v {:v1 1 :v2 2}))

        (r/force-update (:c1 @comps))
        (is (= @v {:v1 2 :v2 2}))

        (r/force-update (:c2 @comps) true)
        (is (= @v {:v1 3 :v2 3}))))
    (with-mounted-component [c3]
      (fn [c]
        (is (= @spy 0))
        (swap! state inc)
        (is (= @spy 0))
        (r/force-update (:c3 @comps))
        (is (= @spy 1))))))

(deftest test-component-path
  (let [a (atom nil)
        tc (r/create-class {:display-name "atestcomponent"
                           :render (fn []
                                     (let [c (r/current-component)]
                                       (reset! a (r/component-path c))
                                       [:div]))})]
    (with-mounted-component [tc]
      (fn [c]
        (is (seq @a))
        (is (re-find #"atestcomponent" @a) "component-path should work")))))

(deftest test-sorted-map-key
  (let [c1 (fn [map]
             [:div (map 1)])
        c2 (fn []
             [c1 (sorted-map 1 "foo" 2 "bar")])]
    (is (= (rstr [c2]) "<div>foo</div>"))))

(deftest basic-with-let
  (when isClient
    (let [n1 (atom 0)
          n2 (atom 0)
          n3 (atom 0)
          val (r/atom 0)
          c (fn []
              (r/with-let [v (swap! n1 inc)]
                (swap! n2 inc)
                [:div @val]
                (finally
                  (swap! n3 inc))))]
      (with-mounted-component [c]
        (fn [_ div]
          (is (= [1 1 0] [@n1 @n2 @n3]))
          (swap! val inc)
          (is (= [1 1 0] [@n1 @n2 @n3]))
          (r/flush)
          (is (= [1 2 0] [@n1 @n2 @n3]))))
      (is (= [1 2 1] [@n1 @n2 @n3])))))

(deftest with-let-destroy-only
  (when isClient
    (let [n1 (atom 0)
          n2 (atom 0)
          c (fn []
              (r/with-let []
                (swap! n1 inc)
                [:div]
                (finally
                  (swap! n2 inc))))]
      (with-mounted-component [c]
        (fn [_ div]
          (is (= [1 0] [@n1 @n2]))))
      (is (= [1 1] [@n1 @n2])))))

(deftest with-let-arg
  (when isClient
    (let [a (atom 0)
          s (r/atom "foo")
          f (fn [x]
              (r/with-let []
                (reset! a x)
                [:div x]))
          c (fn []
              (r/with-let []
                [f @s]))]
      (with-mounted-component [c]
        (fn [_ div]
          (is (= @a "foo"))
          (reset! s "bar")
          (r/flush)
          (is (= @a "bar")))))))

(deftest with-let-non-reactive
  (let [n1 (atom 0)
        n2 (atom 0)
        n3 (atom 0)
        c (fn []
            (r/with-let [a (swap! n1 inc)]
              (swap! n2 inc)
              [:div a]
              (finally
                (swap! n3 inc))))]
    (is (= (rstr [c]) (rstr [:div 1])))
    (is (= [1 1 1] [@n1 @n2 @n3]))))

(deftest lifecycle
  (let [n1 (atom 0)
        t (atom 0)
        res (atom {})
        add-args (fn [key args]
                   (swap! res assoc key
                          {:at (swap! n1 inc)
                           :args (vec args)}))
        render (fn [& args]
                 (this-as c (is (= c (r/current-component))))
                 (add-args :render args)
                 [:div "" (first args)])
        render2 (fn [& args]
                 (add-args :render args)
                 [:div "" (first args)])
        ls {:get-initial-state
            (fn [& args]
              (reset! t (first args))
              (add-args :initial-state args)
              {:foo "bar"})
            :UNSAFE_component-will-mount
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :will-mount args))
            :component-did-mount
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :did-mount args))
            :should-component-update
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :should-update args) true)
            :UNSAFE_component-will-receive-props
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :will-receive args))
            :UNSAFE_component-will-update
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :will-update args))
            :component-did-update
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :did-update args))
            :component-will-unmount
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :will-unmount args))}
        c1 (r/create-class
            (assoc ls :reagent-render render))
        defarg ["a" "b"]
        arg (r/atom defarg)
        comp (atom c1)
        c2 (fn []
             (apply vector @comp @arg))
        cnative (fn []
                  (into [:> @comp] @arg))
        check (fn []
                (is (= (:initial-state @res)
                       {:at 1 :args [@t]}))
                (is (= (:will-mount @res)
                       {:at 2 :args [@t]}))
                (is (= (:render @res)
                       {:at 3 :args ["a" "b"]}))
                (is (= (:did-mount @res)
                       {:at 4 :args [@t]}))

                (reset! arg ["a" "c"])
                (r/flush)
                (is (= (:will-receive @res)
                       {:at 5 :args [@t [@comp "a" "c"]]}))
                (is (= (:should-update @res)
                       {:at 6 :args [@t [@comp "a" "b"] [@comp "a" "c"]]}))
                (is (= (:will-update @res)
                       {:at 7 :args [@t [@comp "a" "c"] {:foo "bar"}]}))
                (is (= (:render @res)
                       {:at 8 :args ["a" "c"]}))
                (is (= (:did-update @res)
                       {:at 9 :args [@t [@comp "a" "b"] {:foo "bar"} nil]})))]
    (when isClient
      (with-mounted-component [c2] check)
      (is (= (:will-unmount @res)
             {:at 10 :args [@t]}))

      (reset! comp (with-meta render2 ls))
      (reset! arg defarg)
      (reset! n1 0)
      (with-mounted-component [c2] check)
      (is (= (:will-unmount @res)
             {:at 10 :args [@t]})))))


(deftest lifecycle-native
  (let [n1 (atom 0)
        t (atom 0)
        res (atom {})
        oldprops (atom nil)
        newprops (atom nil)
        add-args (fn [key args]
                   (swap! res assoc key
                          {:at (swap! n1 inc)
                           :args (vec args)}))
        render (fn [& args]
                 (this-as
                  c
                  (when @newprops
                    (is (= @newprops) (first args))
                    (is (= @newprops) (r/props c)))
                  (is (= c (r/current-component)))
                  (is (= (first args) (r/props c)))
                  (add-args :render
                            {:children (r/children c)})
                  [:div "" (first args)]))
        ls {:get-initial-state
            (fn [& args]
              (reset! t (first args))
              (reset! oldprops (-> args first r/props))
              (add-args :initial-state args)
              {:foo "bar"})
            :UNSAFE_component-will-mount
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :will-mount args))
            :component-did-mount
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :did-mount args))
            :should-component-update
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :should-update args) true)
            :UNSAFE_component-will-receive-props
            (fn [& args]
              (reset! newprops (-> args second second))
              (this-as c
                       (is (= c (first args)))
                       (add-args :will-receive (into [(dissoc (r/props c) :children)]
                                                     (:children (r/props c))))))
            :UNSAFE_component-will-update
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :will-update args))
            :component-did-update
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :did-update args))
            :component-will-unmount
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :will-unmount args))}
        c1 (r/create-class
            (assoc ls :reagent-render render))
        defarg [{:foo "bar"} "a" "b"]
        arg (r/atom defarg)
        comp (atom c1)
        cnative (fn []
                  (into [:> @comp] @arg))
        check (fn []
                (is (= (:initial-state @res)
                       {:at 1 :args [@t]}))
                (is (= (:will-mount @res)
                       {:at 2 :args [@t]}))
                (is (= (:render @res)
                       {:at 3 :args [[:children ["a" "b"]]]}))
                (is (= (:did-mount @res)
                       {:at 4 :args [@t]}))

                (reset! arg [{:f "oo"} "a" "c"])
                (r/flush)

                (is (= (:will-receive @res)
                       {:at 5 :args [{:foo "bar"} "a" "b"]}))
                (let [a (:should-update @res)
                      {at :at
                       [this oldv newv] :args} a]
                  (is (= at 6))
                  (is (= (count (:args a)) 3))
                  (is (= (js->clj oldv) (js->clj [@comp @oldprops])))
                  (is (= newv [@comp @newprops])))
                (let [a (:will-update @res)
                      {at :at
                       [this newv] :args} a]
                  (is (= at 7))
                  (is (= newv [@comp @newprops])))
                (is (= (:render @res)
                       {:at 8 :args [[:children ["a" "c"]]]}))
                (let [a (:did-update @res)
                      {at :at
                       [this oldv] :args} a]
                  (is (= at 9))
                  (is (= oldv [@comp @oldprops]))))]
    (when isClient
      (with-mounted-component [cnative] check)
      (is (= (:will-unmount @res)
             {:at 10 :args [@t]})))))

(defn foo []
  [:div])

(defn wrap-capture-window-error [f]
  (if (exists? js/window)
    (fn []
      (let [org js/console.onerror]
        (set! js/window.onerror (fn [e]
                                  (log-error e)
                                  true))
        (try
          (f)
          (finally
            (set! js/window.onerror org)))))
    (fn []
      (let [process (js/require "process")
            l (fn [e]
                (log-error e))]
        (.on process "uncaughtException" l)
        (try
          (f)
          (finally
            (.removeListener process "uncaughtException" l)))))))

(deftest test-err-messages
  (when (dev?)
    (is (thrown-with-msg?
         :default #"Hiccup form should not be empty: \[]"
         (rstr [])))
    (is (thrown-with-msg?
         :default #"Invalid Hiccup tag: \[:>div \[reagenttest.testreagent.foo]]"
         (rstr [:>div [foo]])))
    (is (thrown-with-msg?
         :default #"Invalid Hiccup form: \[23]"
         (rstr [23])))
    ;; This used to be asserted by Reagent, but because it is hard to validate
    ;; components, now we just trust React will validate elements.
    ; (is (thrown-with-msg?
    ;      :default #"Expected React component in: \[:> \[:div]]"
    ;      (rstr [:> [:div]])))
    ;; This is from React.createElement
    ;; NOTE: browser-npm uses production cjs bundle for now which only shows
    ;; the minified error
    (debug/track-warnings
      (wrap-capture-console-error
        #(is (thrown-with-msg?
               :default #"(Element type is invalid:|Minified React error)"
               (rstr [:> [:div]])))))
    (is (thrown-with-msg?
         :default #"Invalid tag: 'p.'"
         (rstr [:p.])))
    (when r/is-client
      (let [comp1 (fn comp1 [x]
                    x)
            comp2 (fn comp2 [x]
                    [comp1 x])
            comp3 (fn comp3 []
                    (r/with-let [a (r/atom "foo")]
                      [:div (for [i (range 0 1)]
                              ^{:key i} [:p @a])]))
            comp4 (fn comp4 []
                    (for [i (range 0 1)]
                      [:p "foo"]))
            nat (let [cmp (fn [])]
                  (gobj/extend
                    (.-prototype cmp)
                    (.-prototype react/Component)
                    #js {:render (fn [])})
                  (gobj/extend cmp react/Component)
                  cmp)
            pkg "reagenttest.testreagent."
            stack1 (str "in " pkg "comp1")
            stack2 (str "in " pkg "comp2 > " pkg "comp1")
            re (fn [& s]
                 (re-pattern (apply str s)))
            rend (fn [x]
                   (with-mounted-component x identity))]
        (let [e (debug/track-warnings
                  (wrap-capture-window-error
                    (wrap-capture-console-error
                      #(is (thrown-with-msg?
                             :default (re "Invalid tag: 'div.' \\(" stack2 "\\)")
                             (rend [comp2 [:div. "foo"]]))))))]
          (is (= (last (:error e))
                 (str "Error rendering component (" stack2 ")"))))

        (let [e (debug/track-warnings
                  (wrap-capture-window-error
                    (wrap-capture-console-error
                      #(is (thrown-with-msg?
                             :default (re "Invalid tag: 'div.' \\(" stack1 "\\)")
                             (rend [comp1 [:div. "foo"]]))))))]
          (is (= (last (:error e))
                 (str "Error rendering component (" stack1 ")"))))

        (let [e (debug/track-warnings #(r/as-element [nat]))]
          (is (re-find #"Using native React classes directly"
                       (-> e :warn first))))

        (let [e (debug/track-warnings
                 #(rend [comp3]))]
          (is (re-find #"Reactive deref not supported"
                       (-> e :warn first))))

        (let [e (debug/track-warnings
                 #(r/as-element (comp4)))]
          (is (re-find #"Every element in a seq should have a unique :key"
                       (-> e :warn first))))))))

(deftest test-error-boundary
  (let [error (r/atom nil)
        error-boundary (fn error-boundary [comp]
                         (r/create-class
                           {:component-did-catch (fn [this e info])
                            :get-derived-state-from-error (fn [e]
                                                            (reset! error e)
                                                            #js {})
                            :reagent-render (fn [comp]
                                              (if @error
                                                [:div "Something went wrong."]
                                                comp))}))
        comp1 (fn comp1 []
                (throw (js/Error. "Test error")))]
    (debug/track-warnings
      (wrap-capture-window-error
        (wrap-capture-console-error
          #(with-mounted-component [error-boundary [comp1]]
             (fn [c div]
               (r/flush)
               (is (= "Test error" (.-message @error)))
               (is (re-find #"Something went wrong\." (.-innerHTML div))))))))))

(deftest test-dom-node
  (let [node (atom nil)
        ref (atom nil)
        comp (r/create-class
              {:reagent-render (fn test-dom []
                                 [:div {:ref #(reset! ref %)} "foobar"])
               :component-did-mount
               (fn [this]
                 (reset! node (r/dom-node this)))})]
    (with-mounted-component [comp]
      (fn [c div]
        (is (= (.-innerHTML @ref) "foobar"))
        (is (= (.-innerHTML @node) "foobar"))
        (is (identical? @ref @node))))))

(deftest test-empty-input
  (is (= "<div><input/></div>"
         (rstr [:div [:input]]))))

(deftest test-object-children
  (is (= "<p>foo bar1</p>"
         (rstr [:p 'foo " " :bar nil 1])))
  (is (= "<p>#&lt;Atom: 1&gt;</p>"
         (rstr [:p (r/atom 1)]))))

(deftest test-after-render
  (let [spy (atom 0)
        val (atom 0)
        exp (atom 0)
        node (atom nil)
        state (r/atom 0)
        comp (fn []
               (let [old @spy]
                 (is (nil? (r/after-render
                            (fn []
                              (is (= "DIV" (.-tagName @node)))
                              (swap! spy inc)))))
                 (is (= old @spy))
                 (is (= @exp @val))
                 [:div {:ref #(reset! node %)} @state]))]
    (with-mounted-component [comp]
      (fn [c div]
        (is (= @spy 1))
        (swap! state inc)
        (is (= @spy 1))
        (is (nil? (r/next-tick #(swap! val inc))))
        (reset! exp 1)
        (is (= @val 0))
        (is (nil? (r/flush)))
        (is (= @val 1))
        (is (= @spy 2))
        (is (nil? (r/force-update c)))
        (is (= @spy 3))
        (is (nil? (r/next-tick #(reset! spy 0))))
        (is (= @spy 3))
        (r/flush)
        (is (= @spy 0))))
    (is (= @node nil))))

(deftest style-property-names-are-camel-cased
  (is (re-find #"<div style=\"text-align:center(;?)\">foo</div>"
               (rstr [:div {:style {:text-align "center"}} "foo"]))))

(deftest custom-element-class-prop
  (is (re-find #"<custom-element class=\"foobar\">foo</custom-element>"
               (rstr [:custom-element {:class "foobar"} "foo"])))

  (is (re-find #"<custom-element class=\"foobar\">foo</custom-element>"
               (rstr [:custom-element.foobar "foo"]))))

(deftest html-entities
  (testing "entity numbers can be unescaped always"
    (is (= "<i> </i>"
           (rstr [:i (gstr/unescapeEntities "&#160;")]))))

  (when r/is-client
    (testing "When DOM is available, all named entities can be unescaped"
      (is (= "<i> </i>"
             (rstr [:i (gstr/unescapeEntities "&nbsp;")]))))))

(defn context-wrapper []
  (r/create-class
    {:get-child-context (fn []
                          (this-as this
                            #js {:foo "bar"}))
     :child-context-types #js {:foo prop-types/string.isRequired}
     :reagent-render (fn [child]
                       [:div
                        "parent,"
                        child])}))

(defn context-child []
  (r/create-class
    {:context-types #js {:foo prop-types/string.isRequired}
     :reagent-render (fn []
                       (let [this (r/current-component)]
                         ;; Context property name is not mangled, so need to  use gobj/get to access property by string name
                         ;; React extern handles context name.
                         [:div "child," (gobj/get (.-context this) "foo")]))}))

(deftest context-test
  (with-mounted-component [context-wrapper [context-child]]
    (fn [c div]
      (is (= "parent,child,bar"
             (.-innerText div))))))


(deftest test-fragments
  (testing "Fragment as array"
    (let [comp (fn comp1 []
                 #js [(r/as-element [:div "hello"])
                      (r/as-element [:div "world"])])]
      (is (= "<div>hello</div><div>world</div>"
             (as-string [comp])))))

  (testing "Fragment element, :<>"
    (let [comp (fn comp2 []
                 [:<>
                  [:div "hello"]
                  [:div "world"]
                  [:div "foo"] ])]
      (is (= "<div>hello</div><div>world</div><div>foo</div>"
             (as-string [comp])))))

  (testing "Fragment key"
    ;; This would cause React warning if both fragements didn't have key set
    ;; But wont fail the test
    (let [children (fn comp4 []
                     [:<>
                      [:div "foo"]])
          comp (fn comp3 []
                 [:div
                  (list
                    [:<>
                     {:key 1}
                     [:div "hello"]
                     [:div "world"]]
                    ^{:key 2}
                    [children]
                    ^{:key 3}
                    [:<>
                     [:div "1"]
                     [:div "2"]])])]
      (is (= "<div><div>hello</div><div>world</div><div>foo</div><div>1</div><div>2</div></div>"
             (as-string [comp]))))))

(defonce my-context (react/createContext "default"))

(def Provider (.-Provider my-context))
(def Consumer (.-Consumer my-context))

(deftest new-context-test
  (is (= "<div>Context: foo</div>"
         (rstr (r/create-element
                 Provider #js {:value "foo"}
                 (r/create-element
                   Consumer #js {}
                   (fn [v]
                     (r/as-element [:div "Context: " v])))))))

  (testing "context default value works"
    (is (= "<div>Context: default</div>"
           (rstr (r/create-element
                   Consumer #js {}
                   (fn [v]
                     (r/as-element [:div "Context: " v])))))))

  (testing "context works with adapt-react-class"
    (let [provider (r/adapt-react-class Provider)
          consumer (r/adapt-react-class Consumer)]
      (is (= "<div>Context: bar</div>"
             (rstr [provider {:value "bar"}
                    [consumer {}
                     (fn [v]
                       (r/as-element [:div "Context: " v]))]])))))

  (testing "context works with :>"
    (is (= "<div>Context: bar</div>"
           (rstr [:> Provider {:value "bar"}
                  [:> Consumer {}
                   (fn [v]
                     (r/as-element [:div "Context: " v]))]]))))

  (testing "static contextType"
    (let [comp (r/create-class
                 {:context-type my-context
                  :reagent-render (fn []
                                    (this-as this
                                      (r/as-element [:div "Context: " (.-context this)])))})]
      (is (= "<div>Context: default</div>"
             (rstr [comp]))))))

(deftest on-failed-prop-comparison-in-should-update-swallow-exception-and-do-not-update-component
  (let [prop (r/atom {:todos 1})
        component-was-updated (atom false)
        error-thrown-after-updating-props (atom false)
        component-class (r/create-class {:reagent-render (fn [& args]
                                                           [:div (str (first args))])
                                         :component-did-update (fn [& args]
                                                                 (reset! component-was-updated true))})
        component (fn []
                    [component-class @prop])]

    (when (and isClient (dev?))
      (let [e (debug/track-warnings
                #(with-mounted-component [component]
                   (fn [c div]
                     (reset! prop (sorted-map 1 2))
                     (try
                       (r/flush)
                       (catch :default e
                         (reset! error-thrown-after-updating-props true)))

                     (is (not @component-was-updated))
                     (is (not @error-thrown-after-updating-props)))))]
        (is (re-find #"Warning: Exception thrown while comparing argv's in shouldComponentUpdate:"
                     (first (:warn e))))))))

(deftest get-derived-state-from-props-test
  (when isClient
    (let [prop (r/atom 0)
          ;; Usually one can use Cljs object as React state. However,
          ;; getDerivedStateFromProps implementation in React uses
          ;; Object.assign to merge current state and partial state returned
          ;; from the method, so the state has to be plain old object.
          pure-component (r/create-class
                           {:constructor (fn [this]
                                           (set! (.-state this) #js {}))
                            :get-derived-state-from-props (fn [props state]
                                                            ;; "Expensive" calculation based on the props
                                                            #js {:v (string/join " " (repeat (inc (:value props)) "foo"))})
                            :render (fn [this]
                                      (r/as-element [:p "Value " (gobj/get (.-state this) "v")]))})
          component (fn []
                      [pure-component {:value @prop}])]
      (with-mounted-component [component]
        (fn [c div]
          (is (found-in #"Value foo" div))
          (swap! prop inc)
          (r/flush)
          (is (found-in #"Value foo foo" div)))))))

(deftest get-derived-state-from-error-test
  (when isClient
    (let [prop (r/atom 0)
          component (r/create-class
                      {:constructor (fn [this props]
                                      (set! (.-state this) #js {:hasError false}))
                       :get-derived-state-from-error (fn [error]
                                                       #js {:hasError true})
                       :component-did-catch (fn [this e info])
                       :render (fn [^js/React.Component this]
                                 (r/as-element (if (.-hasError (.-state this))
                                                 [:p "Error"]
                                                 (into [:<>] (r/children this)))))})
          bad-component (fn []
                          (if (= 0 @prop)
                            [:div "Ok"]
                            (throw (js/Error. "foo"))))]
      (wrap-capture-window-error
        (wrap-capture-console-error
          #(with-mounted-component [component [bad-component]]
             (fn [c div]
               (is (found-in #"Ok" div))
               (swap! prop inc)
               (r/flush)
               (is (found-in #"Error" div)))))))))

(deftest get-snapshot-before-update-test
  (when isClient
    (let [ref (react/createRef)
          prop (r/atom 0)
          did-update (atom nil)
          component (r/create-class
                      {:get-snapshot-before-update (fn [this [_ prev-props] prev-state]
                                                     {:height (.. ref -current -scrollHeight)})
                       :component-did-update (fn [this [_ prev-props] prev-state snapshot]
                                               (reset! did-update snapshot))
                       :render (fn [this]
                                 (r/as-element
                                   [:div
                                    {:ref ref
                                     :style {:height "20px"}}
                                    "foo"]))})
          component-2 (fn []
                        [component {:value @prop}])]
      (with-mounted-component [component-2]
        (fn [c div]
          ;; Attach to DOM to get real height value
          (.appendChild js/document.body div)
          (is (found-in #"foo" div))
          (swap! prop inc)
          (r/flush)
          (is (= {:height 20} @did-update))
          (.removeChild js/document.body div))))))

(deftest issue-462-test
  (when isClient
    (let [val (r/atom 0)
          render (atom 0)
          a (fn issue-462-a [nr]
              (swap! render inc)
              [:h1 "Value " nr])
          b (fn issue-462-b []
              [:div
               ^{:key @val}
               [a @val]])
          c (fn issue-462-c []
              ^{:key @val}
              [b])]
      (with-mounted-component [c]
        (fn [c div]
          (is (= 1 @render))
          (reset! val 1)
          (r/flush)
          (is (= 2 @render))
          (reset! val 0)
          (r/flush)
          (is (= 3 @render)))))))
