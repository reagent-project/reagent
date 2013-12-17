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

(def tests-run (clojure.core/atom 0))
(def tests-should-run (clojure.core/atom 0))

(defn really-simple []
  [:div "div in really-simple"])

(deftest really-simple-test
  (swap! tests-should-run inc)
  (with-mounted-component [really-simple nil nil]
    (fn [c div]
      (swap! tests-run inc)
      (is (found-in #"div in really-simple" div)))))

(deftest test-simple-callback
  (swap! tests-should-run + 6)
  (let [comp (r/create-class
              {:component-did-mount #(swap! tests-run inc)
               :render (fn [P C S]
                         (assert (map? P))
                         (swap! tests-run inc)
                         [:div (str "hi " (:foo P) ".")])})]
    (with-mounted-component (comp {:foo "you"})
      (fn [C div]
        (swap! tests-run inc)
        (is (found-in #"hi you" div))
        
        (r/set-props C {:foo "there"})
        (is (found-in #"hi there" div))

        (let [runs @tests-run]
          (r/set-props C {:foo "there"})
          (is (found-in #"hi there" div))
          (is (= runs @tests-run)))

        (r/replace-props C {:foobar "not used"})
        (is (found-in #"hi ." div))))))

(deftest test-state-change
  (swap! tests-should-run + 3)
  (let [comp (r/create-class
              {:get-initial-state (fn [])
               :render (fn [P C S]
                         (swap! tests-run inc)
                         [:div (str "hi " (:foo S))])})]
    (with-mounted-component (comp)
      (fn [C div]
        (swap! tests-run inc)
        (is (found-in #"hi " div))

        (swap! C assoc :foo "there")
        (is (found-in #"hi there" div))

        (swap! C assoc :foo "you")
        (is (found-in #"hi you" div))))))

(deftest test-ratom-change
  (swap! tests-should-run + 3)
  (let [runs (running)
        val (atom 0)
        v1 (reaction @val)
        ran @tests-run
        comp (fn []
              (swap! tests-run inc)
              [:div (str "val " @v1)])]
    (with-mounted-component [comp]
      (fn [C div]
        (swap! tests-run inc)
        (is (not= runs (running)))
        (is (found-in #"val 0" div))
        (is (= @tests-run (+ ran 2)))

        (reset! val 1)
        (is (found-in #"val 1" div))
        (is (= @tests-run (+ ran 3)))

        ;; should not be rendered
        (reset! val 1)
        (is (found-in #"val 1" div))
        (is (= @tests-run (+ ran 3)))))
    (is (= runs (running)))))

(deftest check-that-test-ran
  (if isClient
    (is (= @tests-run @tests-should-run))
    (is (= @tests-run 0))))
