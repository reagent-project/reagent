(ns reagenttest.testreagent
  (:require [cljs.test :as t :refer-macros [is deftest testing]]
            [reagent.ratom :as rv :refer-macros [reaction]]
            [reagent.debug :as debug :refer-macros [dbg println log dev?]]
            [reagent.interop :refer-macros [$ $!]]
            [reagent.core :as r]
            [reagent.impl.util :as util]))

(def tests-done (atom {}))

(defn fixture [f]
  (set! rv/debug true)
  (f)
  (set! rv/debug false))

(t/use-fixtures :once fixture)

(defn running [] (rv/running))

(def isClient r/is-client)

(def rflush r/flush)

(defn add-test-div [name]
  (let [doc js/document
        body (.-body js/document)
        div (.createElement doc "div")]
    (.appendChild body div)
    div))

(defn with-mounted-component [comp f]
  (when isClient
    (let [div (add-test-div "_testreagent")]
      (let [c (r/render-component comp div)]
        (f c div)
        (r/unmount-component-at-node div)
        (r/flush)
        (.removeChild (.-body js/document) div)))))

(defn found-in [re div]
  (let [res (.-innerHTML div)]
    (if (re-find re res)
      true
      (do (println "Not found: " res)
          false))))

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
          (do (reset! child-props {:on-change (r/partial f)})
              (rflush))
          (is (= @child-ran 6))
          (do (reset! child-props {:on-change (r/partial f)})
              (rflush))
          (is (= @child-ran 6))
          (do (reset! child-props {:on-change (r/partial f1)})
              (rflush))
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
  (r/render-component-to-string comp))

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
  ;; Skip test: produces warning in new React
  ;; (is (not (re-find #"enctype"
  ;;                   (as-string [:div {"enc-type" "x"}])))
  ;;     "Strings are passed through to React.")
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
  (defmulti my-div :type)
  (defmethod my-div :fooish [child] [:div.foo (:content child)])
  (defmethod my-div :barish [child] [:div.bar (:content child)])

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
    (is (not= p1 (r/partial vector 1 3)))))

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
         (r/render-to-static-markup
          [:div "foo"])))
  (is (= "<div class=\"bar\"><p>foo</p></div>"
         (r/render-to-static-markup
          [:div.bar [:p "foo"]])))
  (is (= "<div class=\"bar\"><p>foobar</p></div>"
         (r/render-to-static-markup
          [:div.bar {:dangerously-set-inner-HTML
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
                   :component-function
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

(defn rstr [react-elem]
  (r/render-to-static-markup react-elem))

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

(def ndiv ($ util/react
              createClass
              #js{:displayName "ndiv"
                  :render
                  (fn []
                    (this-as
                     this
                     (r/create-element
                      "div" #js{:className ($ this :props.className)}
                      ($ this :props.children))))}))

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
               [:p {:key i} i (some-> a deref)])])]
    (with-mounted-component [c]
      (fn [c div]
        ;; Just make sure this doesn't print a debug message
        ))))

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
            :component-will-mount
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
            :component-will-receive-props
            (fn [& args]
              (this-as c (is (= c (first args))))
              (add-args :will-receive args))
            :component-will-update
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
                       {:at 7 :args [@t [@comp "a" "c"]]}))
                (is (= (:render @res)
                       {:at 8 :args ["a" "c"]}))
                (is (= (:did-update @res)
                       {:at 9 :args [@t [@comp "a" "b"]]})))]
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
            :component-will-mount
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
            :component-will-receive-props
            (fn [& args]
              (reset! newprops (-> args second second))
              (this-as c
                       (is (= c (first args)))
                       (add-args :will-receive (into [(dissoc (r/props c) :children)]
                                                     (:children (r/props c))))))
            :component-will-update
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
    (is (thrown-with-msg?
         :default #"Expected React component in: \[:> \[:div]]"
         (rstr [:> [:div]])))
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
            nat ($ util/react createClass #js{:render (fn [])})
            pkg "reagenttest.testreagent."
            stack1 (str "in " pkg "comp1")
            stack2 (str "in " pkg "comp2 > " pkg "comp1")
            lstr (fn [& s] (list (apply str s)))
            re (fn [& s]
                 (re-pattern (apply str s)))
            rend (fn [x]
                   (with-mounted-component x identity))]
        (let [e (debug/track-warnings
                 #(is (thrown-with-msg?
                       :default (re "Invalid tag: 'div.' \\(" stack2 "\\)")
                       (rend [comp2 [:div. "foo"]]))))]
          (is (= e
                 {:error (lstr "Error rendering component (" stack2 ")")})))

        (let [e (debug/track-warnings
                 #(is (thrown-with-msg?
                       :default (re "Invalid tag: 'div.' \\(" stack1 "\\)")
                       (rend [comp1 [:div. "foo"]]))))]
          (is (= e
                 {:error (lstr "Error rendering component (" stack1 ")")})))

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
                              (is (= "DIV" ($ @node :tagName)))
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
