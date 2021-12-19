(ns reagenttest.testreagent
  (:require [clojure.test :as t :refer-macros [is deftest testing]]
            [react :as react]
            [reagent.ratom :as rv :refer [reaction]]
            [reagent.debug :as debug :refer [dev?]]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [reagent.dom.server :as server]
            [reagent.impl.component :as comp]
            [reagent.impl.template :as tmpl]
            [reagenttest.utils :as u :refer [with-mounted-component]]
            [clojure.string :as string]
            [goog.string :as gstr]
            [goog.object :as gobj]
            [prop-types :as prop-types]))

(t/use-fixtures :once
                {:before (fn []
                           (set! rv/debug true))
                 :after  (fn []
                           (set! rv/debug false))})

(defn rstr [react-elem compiler]
  (server/render-to-static-markup react-elem compiler))

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

;; Different set of options to try for most test cases

(def functional-compiler (r/create-compiler {:function-components true}))

(def test-compilers
  [tmpl/default-compiler*
   functional-compiler])

(deftest really-simple-test
  (when r/is-client
    (doseq [compiler test-compilers]
      (let [ran (r/atom 0)
            really-simple (fn []
                            (swap! ran inc)
                            [:div "div in really-simple"])]
        (with-mounted-component [really-simple nil nil]
          compiler
          (fn [c div]
            (swap! ran inc)
            (is (= "div in really-simple" (.-innerText div)))
            (r/flush)
            (is (= 2 @ran))
            (rdom/force-update-all)
            (is (= 3 @ran))))
        (is (= 3 @ran))))))

(deftest test-simple-callback
  (when r/is-client
    (doseq [compiler test-compilers]
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
          compiler
          (fn [C div]
            (swap! ran inc)
            (is (= "hi you." (.-innerText div)))))
        (is (= 3 @ran))))))

(deftest test-state-change
  (when r/is-client
    (doseq [compiler test-compilers]
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
          compiler
          (fn [C div]
            (swap! ran inc)
            (is (= "hi initial" (.-innerText div)))

            (r/replace-state @self {:foo "there"})
            (r/state @self)

            (r/flush)
            (is (= "hi there" (.-innerText div)))

            (r/set-state @self {:foo "you"})
            (r/flush)
            (is (= "hi you" (.-innerText div)))))
        (is (= 4 @ran))))))

(deftest test-ratom-change
  (when r/is-client
    (t/async done
      ((reduce (fn [next-done compiler]
                 (fn []
                   (let [ran (r/atom 0)
                         runs (rv/running)
                         val (r/atom 0)
                         secval (r/atom 0)
                         v1-ran (atom 0)
                         v1 (reaction (swap! v1-ran inc) @val)
                         comp (fn []
                                (swap! ran inc)
                                [:div (str "val " @v1 " " @val " " @secval)])]
                     (u/with-mounted-component-async [comp]
                       (fn []
                         (r/next-tick
                           (fn []
                             (r/next-tick
                               (fn []
                                 (is (= runs (rv/running)))
                                 (is (= 2 @ran))
                                 (next-done))))))
                       compiler
                       (fn [C div done]
                         (r/flush)
                         (is (not= runs (rv/running)))
                         (is (= "val 0 0 0" (.-innerText div)))
                         (is (= 1 @ran))

                         (reset! secval 1)
                         (reset! secval 0)
                         (reset! val 1)
                         (reset! val 2)
                         (reset! val 1)
                         (is (= 1 @ran))
                         (is (= 1 @v1-ran))
                         (r/flush)
                         (is (= "val 1 1 0" (.-innerText div)))
                         (is (= 2 @ran) "ran once more")
                         (is (= 2 @v1-ran))

                         ;; should not be rendered
                         (reset! val 1)
                         (is (= 2 @v1-ran))
                         (r/flush)
                         (is (= 2 @v1-ran))
                         (is (= "val 1 1 0" (.-innerText div)))
                         (is (= 2 @ran) "did not run")
                         (done))))))
              done
              test-compilers)))))

(deftest batched-update-test []
  (when r/is-client
    (doseq [compiler test-compilers]
      (let [ran (r/atom 0)
            v1 (r/atom 0)
            v2 (r/atom 0)
            c2 (fn [{val :val}]
                 (swap! ran inc)
                 (is (= val @v1))
                 [:div @v2])
            c1 (fn []
                 (swap! ran inc)
                 [:div @v1
                  [c2 {:val @v1}]])]
        (with-mounted-component [c1]
          compiler
          (fn [c div]
            (r/flush)
            (is (= 2 @ran))
            (swap! v2 inc)
            (is (= 2 @ran))
            (r/flush)
            (is (= 3 @ran))
            (swap! v1 inc)
            (r/flush)
            (is (= 5 @ran))
            ;; TODO: Failing on optimized build
            ; (swap! v2 inc)
            ; (swap! v1 inc)
            ; (r/flush)
            ; (is (= 7 @ran))
            ; (swap! v1 inc)
            ; (swap! v1 inc)
            ; (swap! v2 inc)
            ; (r/flush)
            ; (is (= 9 @ran))
            ))))))

(deftest init-state-test
  (when r/is-client
    (doseq [compiler test-compilers]
      (let [ran (r/atom 0)
            really-simple (fn []
                            (let [this (r/current-component)]
                              (swap! ran inc)
                              (r/set-state this {:foo "foobar"})
                              (fn []
                                [:div (str "this is "
                                           (:foo (r/state this)))])))]
        (with-mounted-component [really-simple nil nil]
          compiler
          (fn [c div]
            (swap! ran inc)
            (is (= "this is foobar" (.-innerText div)))))
        (is (= 2 @ran))))))

(deftest should-update-test
  (when r/is-client
    (doseq [compiler test-compilers]
      (let [parent-ran (r/atom 0)
            child-ran (r/atom 0)
            child-props (r/atom nil)
            f (fn [])
            f1 (fn [])
            child (fn [p]
                    (swap! child-ran inc)
                    [:div (:val p)])
            parent (fn []
                     (swap! parent-ran inc)
                     [:div "child-foo" [child @child-props]])]
        (with-mounted-component [parent nil nil]
          compiler
          (fn [c div]
            (r/flush)
            (is (= 1 @child-ran))
            (is (= "child-foo" (.-innerText div)))

            (reset! child-props {:style {:display :none}})
            (r/flush)
            (is (= 2 @child-ran))

            (reset! child-props {:style {:display :none}})
            (r/flush)
            (is (= 2 @child-ran) "keyw is equal")

            (reset! child-props {:class :foo}) (r/flush)
            (r/flush)
            (is (= 3 @child-ran))

            (reset! child-props {:class :foo}) (r/flush)
            (r/flush)
            (is (= 3 @child-ran))

            (reset! child-props {:class 'foo})
            (r/flush)
            (is (= 4 @child-ran) "symbols are different from keyw")

            (reset! child-props {:class 'foo})
            (r/flush)
            (is (= 4 @child-ran) "symbols are equal")

            (reset! child-props {:style {:color 'red}})
            (r/flush)
            (is (= 5 @child-ran))

            (reset! child-props {:on-change (r/partial f)})
            (r/flush)
            (is (= 6 @child-ran))

            (reset! child-props {:on-change (r/partial f)})
            (r/flush)
            (is (= 6 @child-ran))

            (reset! child-props {:on-change (r/partial f1)})
            (r/flush)
            (is (= 7 @child-ran))

            (rdom/force-update-all)
            (is (= 8 @child-ran))))))))

(deftest dirty-test
  (when r/is-client
    (doseq [compiler test-compilers]
      (let [ran (r/atom 0)
            state (r/atom 0)
            really-simple (fn []
                            (swap! ran inc)
                            (if (= 1 @state)
                              (reset! state 3))
                            [:div (str "state=" @state)])]
        (with-mounted-component [really-simple nil nil]
          compiler
          (fn [c div]
            (is (= 1 @ran))
            (is (= "state=0" (.-innerText div)))
            (reset! state 1)
            (r/flush)
            (is (= 2 @ran))
            (is (= "state=3" (.-innerText div)))))
        (is (= 2 @ran))))))

(defn as-string [comp compiler]
  (server/render-to-static-markup comp compiler))

(deftest to-string-test []
  (doseq [compiler test-compilers]
    (let [comp (fn [props]
                 [:div (str "i am " (:foo props))])]
      (is (= "<div>i am foobar</div>" (as-string [comp {:foo "foobar"}] compiler))))))

(deftest data-aria-test []
  (doseq [compiler test-compilers]
    (is (= "<div data-foo=\"x\"></div>"
           (as-string [:div {:data-foo "x"}] compiler)))
    (is (= "<div aria-labelledby=\"x\"></div>"
           (as-string [:div {:aria-labelledby "x"}] compiler)))
    ;; Skip test: produces warning in new React
    ;; (is (not (re-find #"enctype"
    ;;                   (as-string [:div {"enc-type" "x"}])))
    ;;     "Strings are passed through to React.")
    ;; FIXME: For some reason UMD module returns everything in
    ;; lowercase, and CommonJS with upper T
    (is (re-find #"enc[tT]ype"
                 (as-string [:div {"encType" "x"}] compiler))
        "Strings are passed through to React, and have to be camelcase.")
    (is (re-find #"enc[tT]ype"
                 (as-string [:div {:enc-type "x"}] compiler))
        "Strings are passed through to React, and have to be camelcase.")))

(deftest dynamic-id-class []
  (doseq [compiler test-compilers]
    (is (re-find #"id=.foo"
                 (as-string [:div#foo {:class "bar"}] compiler)))
    (is (= "<div class=\"foo bar\"></div>"
           (as-string [:div.foo {:class "bar"}] compiler)))
    (is (= "<div class=\"foo bar\"></div>"
           (as-string [:div.foo.bar] compiler)))
    (is (= "<div class=\"foo bar\"></div>"
           (as-string [:div.foo {:className "bar"}] compiler)))
    (is (= "<div class=\"foo bar\"></div>"
           (as-string [:div {:className "foo bar"}] compiler)))
    (is (re-find #"id=.foo"
                 (as-string [:div#foo.foo.bar] compiler)))
    (is (re-find #"class=.xxx bar"
                 (as-string [:div#foo.xxx.bar] compiler)))
    (is (re-find #"id=.foo"
                 (as-string [:div.bar {:id "foo"}] compiler)))
    (is (re-find #"id=.foo"
                 (as-string [:div.bar.xxx {:id "foo"}] compiler)))
    (is (= "<div id=\"foo\"></div>"
           (as-string [:div#bar {:id "foo"}] compiler))
        "Dynamic id overwrites static")))

(defmulti my-div :type)
(defmethod my-div :fooish [child] [:div.foo (:content child)])
(defmethod my-div :barish [child] [:div.bar (:content child)])

(deftest ifn-component []
  (doseq [compiler test-compilers]
    (let [comp {:foo [:div "foodiv"]
                :bar [:div "bardiv"]}]
      (is (= "<div><div>foodiv</div></div>"
             (as-string [:div [comp :foo]] compiler)))
      (is (= "<div><div>bardiv</div></div>"
             (as-string [:div [comp :bar]] compiler)))
      (is (= "<div class=\"foo\">inner</div>"
             (as-string [my-div {:type :fooish :content "inner"}] compiler))))))

(deftest symbol-string-tag []
  (doseq [compiler test-compilers]
    (is (= "<div>foobar</div>" (as-string ['div "foobar"] compiler)))
    (is (= "<div>foobar</div>" (as-string ["div" "foobar"] compiler)))
    (is (= "<div id=\"foo\">x</div>" (as-string ['div#foo "x"] compiler)))
    (is (= "<div id=\"foo\">x</div>" (as-string ["div#foo" "x"] compiler)))
    (is (= "<div class=\"foo bar\"></div>" (as-string ['div.foo {:class "bar"}] compiler)))
    (is (= "<div class=\"foo bar\"></div>" (as-string ["div.foo.bar"] compiler)))
    (is (re-find #"id=.foo"
                 (as-string ['div#foo.foo.bar] compiler)))))

(deftest partial-test []
  (let [p1 (r/partial vector 1 2)]
    (is (= [1 2 3] (p1 3)))
    (is (= p1 (r/partial vector 1 2)))
    (is (ifn? p1))
    (is (= (r/partial vector 1 2) p1))
    (is (not= p1 (r/partial vector 1 3)))
    (is (= (hash p1) (hash (r/partial vector 1 2))))))

(deftest test-null-component
  (doseq [compiler test-compilers]
    (let [null-comp (fn [do-show]
                      (when do-show
                        [:div "div in test-null-component"]))]
      (is (= ""
             (as-string [null-comp false] compiler)))
      (is (= "<div>div in test-null-component</div>"
             (as-string [null-comp true] compiler))))))

(deftest test-string
  (doseq [compiler test-compilers]
    (is (= "<div data-reactroot=\"\">foo</div>"
           (server/render-to-string [:div "foo"] compiler)))

    (is (= "<div data-reactroot=\"\"><p>foo</p></div>"
           (server/render-to-string [:div [:p "foo"]] compiler)))))

(deftest test-static-markup
  (doseq [compiler test-compilers]
    (is (= "<div>foo</div>"
           (rstr [:div "foo"] compiler)))
    (is (= "<div class=\"bar\"><p>foo</p></div>"
           (rstr [:div.bar [:p "foo"]] compiler)))
    (is (= "<div class=\"bar\"><p>foobar</p></div>"
           (rstr [:div.bar {:dangerously-set-inner-HTML
                            {:__html "<p>foobar</p>"}}] compiler)))))

(deftest test-return-class
  (when r/is-client
    (doseq [compiler test-compilers]
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
          compiler
          (fn [C div]
            (swap! ran inc)
            (is (= "hi you." (.-innerText div)))
            (is (= 1 @top-ran))
            (is (= 3 @ran))

            (swap! prop assoc :foo "me")
            (r/flush)
            (is (= "hi me." (.-innerText div)))
            (is (= 1 @top-ran))
            (is (= 4 @ran))))))))

(deftest test-return-class-fn
  (when r/is-client
    (doseq [compiler test-compilers]
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
        compiler
        (fn [C div]
          (swap! ran inc)
          (is (= "hi you." (.-innerText div)))
          (is (= 1 @top-ran))
          (is (= 3 @ran))

          (swap! prop assoc :foo "me")
          (r/flush)
          (is (= "hi me." (.-innerText div)))
          (is (= 1 @top-ran))
          (is (= 4 @ran))))))))

(deftest test-create-element
  (doseq [compiler test-compilers]
    (let [ae r/as-element
          ce r/create-element
          rstr #(rstr % compiler)]
      (is (= (rstr (ce "div"))
             (rstr (ae [:div]))))
      (is (= (rstr (ce "div" nil))
             (rstr (ae [:div]))))
      (is (= (rstr (ce "div" nil "foo"))
             (rstr (ae [:div "foo"]))))
      (is (= (rstr (ce "div" nil "foo" "bar"))
             (rstr (ae [:div "foo" "bar"]))))
      (is (= (rstr (ce "div" nil "foo" "bar" "foobar"))
             (rstr (ae [:div "foo" "bar" "foobar"]))))

      (is (= (rstr (ce "div" #js{:className "foo"} "bar"))
             (rstr (ae [:div.foo "bar"]))))

      (is (= (rstr (ce "div" nil (ce "div" nil "foo")))
             (rstr (ae [:div [:div "foo"]]))))
      (is (= (rstr (ce "div" nil (ae [:div "foo"])))
             (rstr (ae [:div [:div "foo"]]))))
      (is (= (rstr (ae [:div (ce "div" nil "foo")]))
             (rstr (ae [:div [:div "foo"]])))))))

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
  (doseq [compiler test-compilers]
    (let [d1 (r/adapt-react-class ndiv)
          d2 (r/adapt-react-class "div")
          rstr #(rstr % compiler)]
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
             (rstr [d2 "a" "b" [:div "c"]]))))))

(deftest test-adapt-class-2
  (doseq [compiler test-compilers]
    (let [d1 ndiv
          d2 "div"
          rstr #(rstr % compiler)]
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
             (rstr [:> d2 "a" "b" [:div "c"]]))))))

(deftest create-element-shortcut-test
  (when r/is-client
    (let [p (atom nil)
          comp (fn [props]
                 (reset! p props)
                 (r/as-element [:div "a" (.-children props)]))]
      (with-mounted-component [:r> comp #js {:foo {:bar "x"}}
                               [:p "bar"]]
        (fn [c div]
          (is (= {:bar "x"} (gobj/get @p "foo")))
          (is (= "<div>a<p>bar</p></div>" (.-innerHTML div))))))))

(deftest shortcut-key-warning
  ;; TODO: Test create-element with key prop

  (let [w (debug/track-warnings
           #(with-mounted-component [:div
                                     (list
                                      [:> "div" {:key 1} "a"]
                                      [:> "div" {:key 2} "b"])]
              (fn [c div])))]
      (is (empty? (:warn w))))

  (let [w (debug/track-warnings
           #(with-mounted-component [:div
                                     (list
                                      [:r> "div" #js {:key 1} "a"]
                                      [:r> "div" #js {:key 2} "b"])]
              (fn [c div])))]
      (is (empty? (:warn w))))

  (let [f (fn [props c]
            [:div props c])
        w (debug/track-warnings
           #(with-mounted-component [:div
                                     (list
                                      [:f> f {:key 1} "a"]
                                      [:f> f {:key 2} "b"])]
              (fn [c div])))]
      (is (empty? (:warn w)))))

(deftest test-reactize-component
  (doseq [compiler test-compilers]
    (let [ae r/as-element
          ce r/create-element
          rstr #(rstr % compiler)
          a (atom nil)
          c1r (fn reactize [p & args]
                (reset! a args)
                [:p "p:" (:a p) (:children p)])
          c1 (r/reactify-component c1r compiler)]
      (is (= (rstr (ce c1 #js{:a "a"}))
             (rstr [:p "p:a"])))
      (is (= nil @a))
      (is (= (rstr (ce c1 #js{:a nil}))
             (rstr [:p "p:"])))
      (is (= (rstr (ce c1 nil))
             (rstr [:p "p:"])))

      (is (= (rstr (ce c1 #js{:a "a"}
                       (ae [:b "b"])))
             (rstr [:p "p:a" [:b "b"]])))
      (is (= nil @a))
      (is (= (rstr (ce c1 #js{:a "a"}
                       (ae [:b "b"])
                       (ae [:i "i"])))
             (rstr [:p "p:a" [:b "b"] [:i "i"]])))
      (is (= nil @a)))))

(deftest test-keys
  (doseq [compiler test-compilers]
    (let [a nil ;; (r/atom "a")
          c (fn key-tester []
              [:div
               (for [i (range 3)]
                 ^{:key i} [:p i (some-> a deref)])
               (for [i (range 3)]
                 [:p {:key i} i (some-> a deref)])])
          w (debug/track-warnings
              #(with-mounted-component [c]
                 compiler
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
                       compiler
                       (fn [c div]))))]
          (if (dev?)
            (is (re-find #"Warning: Every element in a seq should have a unique :key: \(\[:button \{:on-click #object\[Function\]\}\] \[:button \{:on-click #object\[Function\]\}\] \[:button \{:on-click #object\[Function\]\}\]\)\n \(in reagenttest.testreagent.key_tester\)"
                         (first (:warn w))))))))))

(deftest test-extended-syntax
  (doseq [compiler test-compilers
          :let [rstr #(rstr % compiler)]]
    (is (= "<p><b>foo</b></p>"
           (rstr [:p>b "foo"])))
    (is (= (rstr [:p.foo [:b "x"]])
           (rstr [:p.foo>b "x"])))
    (is (= (rstr [:div.foo [:p.bar.foo [:b.foobar "xy"]]])
           (rstr [:div.foo>p.bar.foo>b.foobar "xy"])))
    (is (= (rstr [:div.foo [:p.bar.foo [:b.foobar "xy"]]])
           (rstr [:div.foo>p.bar.foo>b.foobar {} "xy"])))
    (is (= (rstr [:div [:p.bar.foo [:a.foobar {:href "href"} "xy"]]])
           (rstr [:div>p.bar.foo>a.foobar {:href "href"} "xy"])))))

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
  (doseq [compiler test-compilers
          :let [rstr #(rstr % compiler)]]
    (is (= (rstr [:p {:class "a b c d"}])
           (rstr [:p {:class ["a" "b" "c" "d"]}])))
    (is (= (rstr [:p {:class "a b c"}])
           (rstr [:p {:class ["a" nil "b" false "c" nil]}])))
    (is (= (rstr [:p {:class "a b c"}])
           (rstr [:p {:class '("a" "b" "c")}])))
    (is (= (rstr [:p {:class "a b c"}])
           (rstr [:p {:class #{"a" "b" "c"}}])))))

(deftest class-different-types
  (doseq [compiler test-compilers
          :let [rstr #(rstr % compiler)]]
    (testing "named values are supported"
      (is (= (rstr [:p {:class "a"}])
             (rstr [:p {:class :a}])))
      (is (= (rstr [:p {:class "a b"}])
             (rstr [:p.a {:class :b}])))
      (is (= (rstr [:p {:class "a b"}])
             (rstr [:p.a {:class 'b}])))
      (is (= (rstr [:p {:class "a b"}])
             (rstr [:p {:class [:a :b]}])))
      (is (= (rstr [:p {:class "a b"}])
             (rstr [:p {:class ['a :b]}]))))

    (testing "non-named values like numbers"
      (is (= (rstr [:p {:class "1 b"}])
             (rstr [:p {:class [1 :b]}]))))

    (testing "falsey values are filtered from collections"
      (is (= (rstr [:p {:class "a b"}])
             (rstr [:p {:class [:a :b false nil]}]))))))

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
        (is (= {:v1 1 :v2 1} @v))

        (r/force-update (:c2 @comps))
        (is (= {:v1 1 :v2 2} @v))

        (r/force-update (:c1 @comps))
        (is (= {:v1 2 :v2 2} @v))

        (r/force-update (:c2 @comps) true)
        (is (= {:v1 3 :v2 3} @v))))
    (with-mounted-component [c3]
      (fn [c]
        (is (= 0 @spy))
        (swap! state inc)
        (is (= 0 @spy))
        (r/force-update (:c3 @comps))
        (is (= 1 @spy))))))

(deftest test-component-path
  (let [a (atom nil)
        tc (r/create-class {:display-name "atestcomponent"
                            :render (fn []
                                      (let [c (r/current-component)]
                                        (reset! a (comp/component-name c))
                                        [:div]))})]
    (with-mounted-component [tc]
      (fn [c]
        (is (seq @a))
        (is (re-find #"atestcomponent" @a) "component-path should work")))))

(deftest test-sorted-map-key
  (doseq [compiler test-compilers
          :let [rstr #(rstr % compiler)]]
    (let [c1 (fn [map]
               [:div (map 1)])
          c2 (fn []
               [c1 (sorted-map 1 "foo" 2 "bar")])]
      (is (= "<div>foo</div>" (rstr [c2]))))))

(deftest basic-with-let
  (when r/is-client
    (t/async done
      ((reduce
        (fn [next-done compiler]
          (fn []
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
              ;; With functional components,
              ;; effect cleanup (which calls ratom dispose) happens
              ;; async after unmount.
              (u/with-mounted-component-async [c]
                (fn []
                  (r/next-tick
                    (fn []
                      (r/next-tick
                        (fn []
                          (is (= [1 2 1] [@n1 @n2 @n3]))
                          (next-done))))))
                compiler
                (fn [_ div done]
                  (is (= [1 1 0] [@n1 @n2 @n3]))
                  (swap! val inc)
                  (is (= [1 1 0] [@n1 @n2 @n3]))
                  (r/flush)
                  (is (= [1 2 0] [@n1 @n2 @n3]))
                  (done))))))
        done
        test-compilers)))))

(deftest with-let-destroy-only
  (when r/is-client
    (t/async done
      ((reduce
         (fn [next-done compiler]
           (fn []
             (let [n1 (atom 0)
                   n2 (atom 0)
                   c (fn []
                       (r/with-let []
                         (swap! n1 inc)
                         [:div]
                         (finally
                           (swap! n2 inc))))]
               (u/with-mounted-component-async [c]
                 ;; Wait 2 animation frames for
                 ;; useEffect cleanup to be called.
                 (fn []
                   (r/next-tick
                     (fn []
                       (r/next-tick
                         (fn []
                           (is (= [1 1] [@n1 @n2]))
                           (next-done))))))
                 compiler
                 (fn [_ div done]
                   (is (= [1 0] [@n1 @n2]))
                   (done))))))
         done
         test-compilers)))))

(deftest with-let-arg
  (when r/is-client
    (doseq [compiler test-compilers]
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
          compiler
          (fn [_ div]
            (is (= "foo" @a))
            (reset! s "bar")
            (r/flush)
            (is (= "bar" @a))))))))

(deftest with-let-non-reactive
  (doseq [compiler test-compilers]
    (let [n1 (atom 0)
          n2 (atom 0)
          n3 (atom 0)
          rstr #(rstr % compiler)
          c (fn []
              (r/with-let [a (swap! n1 inc)]
                (swap! n2 inc)
                [:div a]
                (finally
                  (swap! n3 inc))))]
      (is (= (rstr [c]) (rstr [:div 1])))
      (is (= [1 1 1] [@n1 @n2 @n3])))))

(deftest lifecycle
  (doseq [compiler test-compilers]
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
                  (is (= {:at 1 :args [@t]}
                         (:initial-state @res)))
                  (is (= {:at 2 :args [@t]}
                         (:will-mount @res)))
                  (is (= {:at 3 :args ["a" "b"]}
                         (:render @res)))
                  (is (= {:at 4 :args [@t]}
                         (:did-mount @res)))

                  (reset! arg ["a" "c"])
                  (r/flush)
                  (is (= {:at 5 :args [@t [@comp "a" "c"]]}
                         (:will-receive @res)))
                  (is (= {:at 6 :args [@t [@comp "a" "b"] [@comp "a" "c"]]}
                         (:should-update @res)))
                  (is (= {:at 7 :args [@t [@comp "a" "c"] {:foo "bar"}]}
                         (:will-update @res)))
                  (is (= {:at 8 :args ["a" "c"]}
                         (:render @res)))
                  (is (= {:at 9 :args [@t [@comp "a" "b"] {:foo "bar"} nil]}
                         (:did-update @res))))]
      (when r/is-client
        (with-mounted-component [c2] compiler check)
        (is (= {:at 10 :args [@t]}
               (:will-unmount @res)))

        (reset! comp (with-meta render2 ls))
        (reset! arg defarg)
        (reset! n1 0)
        (with-mounted-component [c2] check)
        (is (= {:at 10 :args [@t]}
               (:will-unmount @res)))))))


(deftest lifecycle-native
  (doseq [compiler test-compilers]
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
                       (is (= (first args) @newprops))
                       (is (= (r/props c) @newprops)))
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
                  (is (= {:at 1 :args [@t]}
                         (:initial-state @res)))
                  (is (= {:at 2 :args [@t]}
                         (:will-mount @res)))
                  (is (= {:at 3 :args [[:children ["a" "b"]]]}
                         (:render @res)))
                  (is (= {:at 4 :args [@t]}
                         (:did-mount @res)))

                  (reset! arg [{:f "oo"} "a" "c"])
                  (r/flush)

                  (is (= {:at 5 :args [{:foo "bar"} "a" "b"]}
                         (:will-receive @res)))
                  (let [a (:should-update @res)
                        {at :at
                         [this oldv newv] :args} a]
                    (is (= 6 at))
                    (is (= 3 (count (:args a))))
                    (is (= (js->clj [@comp @oldprops]) (js->clj oldv)))
                    (is (= [@comp @newprops] newv)))
                  (let [a (:will-update @res)
                        {at :at
                         [this newv] :args} a]
                    (is (= 7 at))
                    (is (= [@comp @newprops] newv)))
                  (is (= {:at 8 :args [[:children ["a" "c"]]]}
                         (:render @res)))
                  (let [a (:did-update @res)
                        {at :at
                         [this oldv] :args} a]
                    (is (= 9 at))
                    (is (= [@comp @oldprops] oldv))))]
      (when r/is-client
        (with-mounted-component [cnative] compiler check)
        (is (= {:at 10 :args [@t]}
               (:will-unmount @res)))))))

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
    (doseq [compiler test-compilers
            :let [rstr #(rstr % compiler)]]
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
              rend (fn [x]
                     (with-mounted-component x compiler identity))]

          ;; Error is orginally caused by comp1, so only that is shown in the error
          (let [e (debug/track-warnings
                    (wrap-capture-window-error
                      (wrap-capture-console-error
                        #(is (thrown-with-msg?
                               :default #"Invalid tag: 'div.' \(in reagenttest.testreagent.comp1\)"
                               (rend [comp2 [:div. "foo"]]))))))]
            (is (re-find #"The above error occurred in the <reagenttest\.testreagent\.comp1> component:"
                         (first (:error e)))))

          (let [e (debug/track-warnings
                    (wrap-capture-window-error
                      (wrap-capture-console-error
                        #(is (thrown-with-msg?
                               :default #"Invalid tag: 'div.' \(in reagenttest.testreagent.comp1\)"
                               (rend [comp1 [:div. "foo"]]))))))]
            (is (re-find #"The above error occurred in the <reagenttest\.testreagent\.comp1> component:"
                         (first (:error e)))))

          (let [e (debug/track-warnings #(r/as-element [nat] compiler))]
            (is (re-find #"Using native React classes directly"
                         (-> e :warn first))))

          (let [e (debug/track-warnings
                    #(rend [comp3]))]
            (is (re-find #"Reactive deref not supported"
                         (-> e :warn first))))

          (let [e (debug/track-warnings
                    #(r/as-element (comp4) compiler))]
            (is (re-find #"Every element in a seq should have a unique :key"
                         (-> e :warn first)))))))))

(deftest test-error-boundary
  (doseq [compiler test-compilers]
    (let [error (r/atom nil)
          info (r/atom nil)
          error-boundary (fn error-boundary [comp]
                           (r/create-class
                             {:component-did-catch (fn [this e i]
                                                     (reset! info i))
                              :get-derived-state-from-error (fn [e]
                                                              (reset! error e)
                                                              #js {})
                              :reagent-render (fn [comp]
                                                (if @error
                                                  [:div "Something went wrong."]
                                                  comp))}))
          comp1 (fn comp1 []
                  (throw (js/Error. "Test error")))
          comp2 (fn comp2 []
                  [comp1])]
      (debug/track-warnings
        (wrap-capture-window-error
          (wrap-capture-console-error
            #(with-mounted-component [error-boundary [comp2]]
               compiler
               (fn [c div]
                 (r/flush)
                 (is (= "Test error" (.-message @error)))
                 (is (re-find #"Something went wrong\." (.-innerHTML div)))
                 (if (dev?)
                   (is (re-find #"^\n    at reagenttest.testreagent.comp1 \([^)]*\)\n    at reagenttest.testreagent.comp2 \([^)]*\)\n    at reagent[0-9]+ \([^)]*\)\n    at reagenttest.testreagent.error_boundary \([^)]*\)$"
                                (.-componentStack ^js @info)))
                   (is (re-find #"^\n    at reagent[0-9]+. \([^)]*\)\n    at reagent[0-9]+ \([^)]*\)\n    at reagent[0-9]+ \([^)]*\)\n    at .+ \([^)]*\)$"
                                (.-componentStack ^js @info))))))))))))

(deftest test-dom-node
  (doseq [compiler test-compilers]
    (let [node (atom nil)
          ref (atom nil)
          comp (r/create-class
                 {:reagent-render (fn test-dom []
                                    [:div {:ref #(reset! ref %)} "foobar"])
                  :component-did-mount
                  (fn [this]
                    (reset! node (rdom/dom-node this)))})]
      (with-mounted-component [comp]
        compiler
        (fn [c div]
          (is (= "foobar" (.-innerHTML @ref)))
          (is (= "foobar" (.-innerHTML @node)))
          (is (identical? @ref @node)))))))

(deftest test-empty-input
  (doseq [compiler test-compilers
          :let [rstr #(rstr % compiler)]]
    (is (= "<div><input/></div>"
           (rstr [:div [:input]])))))

(deftest test-object-children
  (doseq [compiler test-compilers
          :let [rstr #(rstr % compiler)]]
    (is (= "<p>foo bar1</p>"
           (rstr [:p 'foo " " :bar nil 1])))
    (is (= "<p>#object[reagent.ratom.RAtom {:val 1}]</p>"
           (rstr [:p (r/atom 1)])))))

(deftest test-after-render
  (doseq [compiler test-compilers]
    (let [spy (atom 0)
          val (atom 0)
          exp (atom 0)
          node (atom nil)
          state (r/atom 0)
          comp (fn []
                 (let [old @spy]
                   (r/after-render
                     (fn []
                       (is (= "DIV" (.-tagName @node)))
                       (swap! spy inc)))
                   (is (= @spy old))
                   (is (= @exp @val))
                   [:div {:ref #(reset! node %)} @state]))]
      (with-mounted-component [comp]
        compiler
        (fn [c div]
          (is (= 1 @spy))
          (swap! state inc)
          (is (= 1 @spy))
          (r/next-tick #(swap! val inc))
          (reset! exp 1)
          (is (= 0 @val))
          (r/flush)
          (is (= 1 @val))
          (is (= 2 @spy))
          ;; FIXME: c is nil because render call doesn't return anything
          ; (r/force-update c)
          ; (is (= 3 @spy))
          ; (r/next-tick #(reset! spy 0))
          ; (is (= 3 @spy))
          ; (r/flush)
          ; (is (= 0 @spy))
          ))
      (is (= nil @node)))))

(deftest style-property-names-are-camel-cased
  (doseq [compiler test-compilers
          :let [rstr #(rstr % compiler)]]
    (is (= "<div style=\"text-align:center\">foo</div>"
           (rstr [:div {:style {:text-align "center"}} "foo"])))))

(deftest custom-element-class-prop
  (doseq [compiler test-compilers
          :let [rstr #(rstr % compiler)]]
    (is (= "<custom-element class=\"foobar\">foo</custom-element>"
           (rstr [:custom-element {:class "foobar"} "foo"])))

    (is (= "<custom-element class=\"foobar\">foo</custom-element>"
           (rstr [:custom-element.foobar "foo"])))))

(deftest html-entities
  (doseq [compiler test-compilers
          :let [rstr #(rstr % compiler)]]
    (testing "entity numbers can be unescaped always"
      (is (= "<i> </i>"
             (rstr [:i (gstr/unescapeEntities "&#160;")]))))

    (when r/is-client
      (testing "When DOM is available, all named entities can be unescaped"
        (is (= "<i> </i>"
               (rstr [:i (gstr/unescapeEntities "&nbsp;")])))))))

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
  (doseq [compiler test-compilers]
    (testing "Fragment as array"
      (let [comp (fn comp1 []
                   #js [(r/as-element [:div "hello"] compiler)
                        (r/as-element [:div "world"] compiler)])]
        (is (= "<div>hello</div><div>world</div>"
               (as-string [comp] compiler)))))

    (testing "Fragment element, :<>"
      (let [comp (fn comp2 []
                   [:<>
                    [:div "hello"]
                    [:div "world"]
                    [:div "foo"] ])]
        (is (= "<div>hello</div><div>world</div><div>foo</div>"
               (as-string [comp] compiler)))))

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
               (as-string [comp] compiler)))))))

;; In bundle version, the names aren't optimized.
;; In node module processed versions, names probably are optimized.
(defonce my-context ^js/MyContext (react/createContext "default"))

(def Provider (.-Provider my-context))
(def Consumer (.-Consumer my-context))

(deftest new-context-test
  (doseq [compiler test-compilers
          :let [rstr #(rstr % compiler)]]
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
               (rstr [comp])))))

    (testing "useContext hook"
      (let [comp (fn [v]
                   (let [v (react/useContext my-context)]
                     [:div "Context: " v]))]
        (is (= "<div>Context: foo</div>"
               (rstr [:r> Provider
                      #js {:value "foo"}
                      [:f> comp]])))))))

(deftest on-failed-prop-comparison-in-should-update-swallow-exception-and-do-not-update-component
  (doseq [compiler test-compilers]
    (let [prop (r/atom {:todos 1})
          component-was-updated (atom false)
          error-thrown-after-updating-props (atom false)
          component-class (r/create-class {:reagent-render (fn [& args]
                                                             [:div (str (first args))])
                                           :component-did-update (fn [& args]
                                                                   (reset! component-was-updated true))})
          component (fn []
                      [component-class @prop])]

      (when (and r/is-client (dev?))
        (let [e (debug/track-warnings
                  #(with-mounted-component [component]
                     compiler
                     (fn [c div]
                       (reset! prop (sorted-map 1 2))
                       (try
                         (r/flush)
                         (catch :default e
                           (reset! error-thrown-after-updating-props true)))

                       (is (not @component-was-updated))
                       (is (not @error-thrown-after-updating-props)))))]
          (is (re-find #"Warning: Exception thrown while comparing argv's in shouldComponentUpdate:"
                       (first (:warn e)))))))))

(deftest get-derived-state-from-props-test
  (when r/is-client
    (doseq [compiler test-compilers]
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
          compiler
          (fn [c div]
            (is (= "Value foo" (.-innerText div)))
            (swap! prop inc)
            (r/flush)
            (is (= "Value foo foo" (.-innerText div)))))))))

(deftest get-derived-state-from-error-test
  (when r/is-client
    (doseq [compiler test-compilers]
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
             compiler
             (fn [c div]
               (is (= "Ok" (.-innerText div)))
               (swap! prop inc)
               (r/flush)
               (is (= "Error" (.-innerText div)))))))))))

(deftest get-snapshot-before-update-test
  (when r/is-client
    (doseq [compiler test-compilers]
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
          compiler
          (fn [c div]
            ;; Attach to DOM to get real height value
            (.appendChild js/document.body div)
            (is (= "foo" (.-innerText div)))
            (swap! prop inc)
            (r/flush)
            (is (= {:height 20} @did-update))
            (.removeChild js/document.body div)))))))

(deftest issue-462-test
  (when r/is-client
    (doseq [compiler test-compilers]
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
          compiler
          (fn [c div]
            (is (= 1 @render))
            (reset! val 1)
            (r/flush)
            (is (= 2 @render))
            (reset! val 0)
            (r/flush)
            (is (= 3 @render))))))))

(deftest functional-component-poc-simple
  (when r/is-client
    (let [c (fn [x]
              [:span "Hello " x])]
        (testing ":f>"
          (with-mounted-component [:f> c "foo"]
            (fn [c div]
              (is (nil? c) "Render returns nil for stateless components")
              (is (= "Hello foo" (.-innerText div))))))

        (testing "compiler options"
          (with-mounted-component [c "foo"]
            functional-compiler
            (fn [c div]
              (is (nil? c) "Render returns nil for stateless components")
              (is (= "Hello foo" (.-innerText div))))))

        (testing "setting default compiler"
          (try
           (r/set-default-compiler! functional-compiler)
           (with-mounted-component [c "foo"] nil
             (fn [c div]
               (is (nil? c) "Render returns nil for stateless components")
               (is (= "Hello foo" (.-innerText div)))))
           (finally
            (r/set-default-compiler! nil)))))))

(deftest functional-component-poc-state-hook
  (when r/is-client
    (let [;; Probably not the best idea to keep
          ;; refernce to state hook update fn, but
          ;; works for testing.
          set-count! (atom nil)
          c (fn [x]
              (let [[c set-count] (react/useState x)]
                (reset! set-count! set-count)
                [:span "Count " c]))]
      (with-mounted-component [c 5]
        functional-compiler
        (fn [c div]
          (is (nil? c) "Render returns nil for stateless components")
          (is (= "Count 5" (.-innerText div)))
          (@set-count! 6)
          (is (= "Count 6" (.-innerText div))))))))

(deftest functional-component-poc-ratom
  (when r/is-client
    (let [count (r/atom 5)
          c (fn [x]
              [:span "Count " @count])]
      (with-mounted-component [c 5]
        functional-compiler
        (fn [c div]
          (is (nil? c) "Render returns nil for stateless components")
          (is (= "Count 5" (.-innerText div)))
          (reset! count 6)
          (r/flush)
          (is (= "Count 6" (.-innerText div)))
          ;; TODO: Test that component RAtom is disposed
          )))))


(deftest functional-component-poc-ratom-state-hook
  (when r/is-client
    (let [r-count (r/atom 3)
          set-count! (atom nil)
          c (fn [x]
              (let [[c set-count] (react/useState x)]
                (reset! set-count! set-count)
                [:span "Counts " @r-count " " c]))]
      (with-mounted-component [c 15]
        functional-compiler
        (fn [c div]
          (is (nil? c) "Render returns nil for stateless components")
          (is (= "Counts 3 15" (.-innerText div)))
          (reset! r-count 6)
          (r/flush)
          (is (= "Counts 6 15" (.-innerText div)))
          (@set-count! 17)
          (is (= "Counts 6 17" (.-innerText div)))
          )))))

(deftest test-input-el-ref
  (when r/is-client
    (doseq [compiler test-compilers]
      (let [ref-1 (atom nil)
            ref-1-fn #(reset! ref-1 %)

            ref-2 (react/createRef)

            c (fn [x]
                [:div
                 [:input
                  {:ref ref-1-fn
                   :value nil
                   :on-change (fn [_])}]

                 [:input
                  {:ref ref-2
                   :value nil
                   :on-change (fn [_])}]])]
        (with-mounted-component [c]
          compiler
          (fn [c div]
            (is (some? @ref-1))
            (is (some? (.-current ref-2)))
            ))))))

(deftest test-element-key
  (is (= "0" (.-key (r/as-element           [:div {:key 0}]))))
  (is (= "0" (.-key (r/as-element ^{:key 0} [:div]))))
  (is (= "0" (.-key (r/as-element           [:input {:key 0}]))))
  (is (= "0" (.-key (r/as-element ^{:key 0} [:input]))))
  )

;; Note: you still pretty much need to access the impl.template namespace to
;; implement your own parse-tag
(defn parse-tag [hiccup-tag]
  (let [[tag id className] (->> hiccup-tag name (re-matches tmpl/re-tag) next)
        ;; For testing, prefix namy class names with foo_
        className (when-not (nil? className)
                    (->> (string/split className #"\.")
                         (map (fn [s] (str "foo_" s)))
                         (string/join " ")))]
    (assert tag (str "Invalid tag: '" hiccup-tag "'" (comp/comp-name)))
    (tmpl/->HiccupTag tag
                      id
                      className
                      ;; Custom element names must contain hyphen
                      ;; https://www.w3.org/TR/custom-elements/#custom-elements-core-concepts
                      (not= -1 (.indexOf tag "-")))))

(def tag-name-cache #js {})

(defn cached-parse [this x _]
  (if-some [s (tmpl/cache-get tag-name-cache x)]
    s
    (let [v (parse-tag x)]
      (gobj/set tag-name-cache x v)
      v)))

(deftest parse-tag-test
  (let [compiler (r/create-compiler {:parse-tag cached-parse})]
    (gobj/clear tag-name-cache)
    (is (= "<div class=\"foo_asd foo_xyz bar\"></div>"
           (as-string [:div.asd.xyz {:class "bar"}] compiler)))))
