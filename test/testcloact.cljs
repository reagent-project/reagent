(ns testcloact
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing)]
                   [cloact.ratom :refer [reaction]]
                   [cloact.debug :refer [dbg println log]])
  (:require [cemerick.cljs.test :as t]
            [cloact.core :as cloact :refer [atom]]
            [cloact.ratom :as rv]))

(defn running [] (rv/running))

(def isClient (not (nil? (try (.-document js/window)
                              (catch js/Object e nil)))))

(defn add-test-div [name]
  (let [doc js/document
        body (.-body js/document)
        div (.createElement doc "div")]
    (.appendChild body div)
    div))

(defn with-mounted-component [comp f]
  (when isClient
    (let [div (add-test-div "_testcloact")]
      (let [comp (cloact/render-component comp div #(f comp div))]
        (cloact/unmount-component-at-node div)))))

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
          comp (cloact/create-class
                       {:component-did-mount #(swap! ran inc)
                        :render (fn [P C]
                                  (assert (map? P))
                                  (swap! ran inc)
                                  [:div (str "hi " (:foo P) ".")])})]
      (with-mounted-component (comp {:foo "you"})
        (fn [C div]
          (swap! ran inc)
          (is (found-in #"hi you" div))
          
          (cloact/set-props C {:foo "there"})
          (is (found-in #"hi there" div))

          (let [runs @ran]
            (cloact/set-props C {:foo "there"})
            (is (found-in #"hi there" div))
            (is (= runs @ran)))

          (cloact/replace-props C {:foobar "not used"})
          (is (found-in #"hi ." div))))
      (is (= 5 @ran)))))

(deftest test-state-change
  (when isClient
    (let [ran (atom 0)
          comp (cloact/create-class
                       {:get-initial-state (fn [])
                        :render (fn [P C]
                                  (swap! ran inc)
                                  [:div (str "hi " (:foo (cloact/state C)))])})]
      (with-mounted-component (comp)
        (fn [C div]
          (swap! ran inc)
          (is (found-in #"hi " div))

          (cloact/set-state C {:foo "there"})
          (is (found-in #"hi there" div))

          (cloact/set-state C {:foo "you"})
          (is (found-in #"hi you" div))))
      (is (= 4 @ran)))))

(deftest test-ratom-change
  (when isClient
    (let [ran (atom 0)
          runs (running)
          val (atom 0)
          v1 (reaction @val)
          comp (fn []
                 (swap! ran inc)
                 [:div (str "val " @v1)])]
      (with-mounted-component [comp]
        (fn [C div]
          (swap! ran inc)
          (is (not= runs (running)))
          (is (found-in #"val 0" div))
          (is (= 2 @ran))

          (reset! val 1)
          (is (found-in #"val 1" div))
          (is (= 3 @ran))

          ;; should not be rendered
          (reset! val 1)
          (is (found-in #"val 1" div))
          (is (= 3 @ran))))
      (is (= runs (running)))
      (is (= 3 @ran)))))

(deftest init-state-test
  (when isClient
    (let [ran (atom 0)
          really-simple (fn [props this]
                          (swap! ran inc)
                          (cloact/set-state this {:foo "foobar"})
                          (fn []
                            [:div (str "this is " (:foo (cloact/state this)))]))]
      (with-mounted-component [really-simple nil nil]
        (fn [c div]
          (swap! ran inc)
          (is (found-in #"this is foobar" div))))
      (is (= 2 @ran)))))

(deftest to-string-test []
  (let [comp (fn [props]
               [:div (str "i am " (:foo props))])]
    (is (re-find #"i am foobar"
                 (cloact/render-component-to-string
                         [comp {:foo "foobar"}])))))
