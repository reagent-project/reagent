(ns reagentdemo.common
  (:require [reagent.core :as r]
            [reagent.debug :refer-macros [dbg println]]))

(defn demo-component []
  (let [showing (r/atom true)]
    (fn [{:keys [comp src complete no-heading]}]
      [:div
       (when comp
         [:div.demo-example.clearfix
          [:a.demo-example-hide {:on-click (fn [e]
                                             (.preventDefault e)
                                             (swap! showing not)
                                             false)}
           (if @showing "hide" "show")]
          (when-not no-heading
            [:h3.demo-heading "Example "])
          (when @showing
            (if-not complete
              [:div.simple-demo [comp]]
              [comp]))])
       (if @showing
         (if src
           [:div.demo-source.clearfix
            (when-not no-heading
              [:h3.demo-heading "Source"])
            src]
           [:div.clearfix]))])))
