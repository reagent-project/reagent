(ns testcloact
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing)]
                   [cloact.ratom :refer [reaction]]
                   [cloact.debug :refer [dbg println]])
  (:require [cemerick.cljs.test :as t]
            [cloact.core :as r :refer [atom]]
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
      (let [comp (r/render-component comp div #(f comp div))]
        (r/unmount-component-at-node div)))))

(defn found-in [re div]
  (re-find re (.-innerHTML div)))


(deftest really-simple-test
  (let [ran (atom 0)
        really-simple (fn []
                        (swap! ran inc)
                        [:div "div in really-simple"])]
    (with-mounted-component [really-simple nil nil]
      (fn [c div]
        (swap! ran inc)
        (is (found-in #"div in really-simple" div))))
    (is (= 2 @ran))))

(deftest test-simple-callback
  (let [ran (atom 0)
        comp (r/create-class
              {:component-did-mount #(swap! ran inc)
               :render (fn [P C]
                         (assert (map? P))
                         (swap! ran inc)
                         [:div (str "hi " (:foo P) ".")])})]
    (with-mounted-component (comp {:foo "you"})
      (fn [C div]
        (swap! ran inc)
        (is (found-in #"hi you" div))
        
        (r/set-props C {:foo "there"})
        (is (found-in #"hi there" div))

        (let [runs @ran]
          (r/set-props C {:foo "there"})
          (is (found-in #"hi there" div))
          (is (= runs @ran)))

        (r/replace-props C {:foobar "not used"})
        (is (found-in #"hi ." div))))
    (is (= 5 @ran))))

(deftest test-state-change
  (let [ran (atom 0)
        comp (r/create-class
              {:get-initial-state (fn [])
               :render (fn [P C]
                         (swap! ran inc)
                         [:div (str "hi " (:foo @C))])})]
    (with-mounted-component (comp)
      (fn [C div]
        (swap! ran inc)
        (is (found-in #"hi " div))

        (swap! C assoc :foo "there")
        (is (found-in #"hi there" div))

        (swap! C assoc :foo "you")
        (is (found-in #"hi you" div))))
    (is (= 4 @ran))))

(deftest test-ratom-change
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
    (is (= 3 @ran))))

