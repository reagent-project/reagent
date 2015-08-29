(ns reagenttest.testreagent
  (:require [cljs.test :as t :refer-macros [is deftest testing]]
            [reagent.ratom :as rv :refer-macros [reaction]]
            [reagent.debug :refer-macros [dbg println log]]
            [reagent.interop :refer-macros [.' .!]]
            [reagent.core :as r]))

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
      (let [comp (r/render-component comp div #(f comp div))]
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
  (when isClient
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
          v1 (reaction @val)
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
          (r/flush)
          (is (found-in #"val 1" div))
          (is (= 2 @ran))

          ;; should not be rendered
          (reset! val 1)
          (r/flush)
          (is (found-in #"val 1" div))
          (is (= 2 @ran))))
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
  (when isClient
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

(def ndiv (.' js/React
              createClass
              #js{:render
                  (fn []
                    (this-as
                     this
                     (r/create-element
                      "div" #js{:className (.' this :props.className)}
                      (.' this :props.children))))}))

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

(deftest test-reactize-component
  (let [ae r/as-element
        ce r/create-element
        c1r (fn [p]
              [:p "p:" (:a p) (:children p)])
        c1 (r/reactify-component c1r)]
    (is (= (rstr [:p "p:a"])
           (rstr (ce c1 #js{:a "a"}))))
    (is (= (rstr [:p "p:"])
           (rstr (ce c1 #js{:a nil}))))
    (is (= (rstr [:p "p:"])
           (rstr (ce c1 nil))))

    (is (= (rstr [:p "p:a" [:b "b"]])
           (rstr (ce c1 #js{:a "a"}
                     (ae [:b "b"])))))
    (is (= (rstr [:p "p:a" [:b "b"] [:i "i"]])
           (rstr (ce c1 #js{:a "a"}
                     (ae [:b "b"])
                     (ae [:i "i"])))))))

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
             [:p (swap! v update-in [:v1] inc)])
        c2 (fn []
             (swap! comps assoc :c2 (r/current-component))
             [:p (swap! v update-in [:v2] inc)
              [c1]])]
    (with-mounted-component [c2]
      (fn [c div]
        (is (= @v {:v1 1 :v2 1}))

        (r/force-update (:c2 @comps))
        (is (= @v {:v1 1 :v2 2}))

        (r/force-update (:c1 @comps))
        (is (= @v {:v1 2 :v2 2}))

        (r/force-update (:c2 @comps) true)
        (is (= @v {:v1 3 :v2 3}))))))
