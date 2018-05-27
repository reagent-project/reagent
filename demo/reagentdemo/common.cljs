(ns reagentdemo.common
  (:require [reagent.core :as r]
            [reagent.debug :refer-macros [dbg println]]))

(defn demo-component [{:keys [expected comp src complete no-heading]}]
  (r/with-let [showing (r/atom false)]
    [:div
     (when expected
       [:div.demo-example.clearfix
        (when-not no-heading
          [:h3.demo-heading "Expected output "])
        (if-not complete
          [:div.simple-demo [expected]]
          [expected])
        (when comp
          [:div
           (when-not no-heading
             [:h3.demo-heading "Actual output "])
           (if-not complete
             [:div.simple-demo [comp]]
             [comp])])])

     (if src
       [:div.demo-source.clearfix
        [:a.demo-example-hide {:on-click (fn [e]
                                           (.preventDefault e)
                                           (swap! showing not)
                                           nil)}
         (if @showing "hide" "show")]
        (when-not no-heading
          [:h3.demo-heading "Solution"])
        (when @showing src)]
       [:div.clearfix])]))
