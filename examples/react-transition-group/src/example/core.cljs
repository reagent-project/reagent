(ns example.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            ["react-transition-group" :refer [TransitionGroup CSSTransition]]
            [goog.object :as gobj]))

(defn transition-group-example []
  (let [state (r/atom {:i 1
                       :elements []})]
    (fn []
      [:div
       [:button
        {:on-click (fn [_]
                     (swap! state (fn [s]
                                    (-> s
                                        (update :i inc)
                                        (update :elements conj (inc (:i s)))))))}
        "Append element"]
       [:button
        {:on-click (fn [_]
                     (swap! state (fn [s]
                                    (-> s
                                        (update :elements #(vec (drop-last %)))))))}
        "Remove element"]
       [:> TransitionGroup
        {:component "ul"}
        (for [e (:elements @state)]
          ;; Can't move this to separate function, or reagent will add component in between and transitions break
          [:> CSSTransition
           {:key e
            :classNames "fade"
            :timeout 500
            :on-enter #(js/console.log "enter" e)
            :on-entering #(js/console.log "entering" e)
            :on-entered #(js/console.log "entered" e)
            :on-exit #(js/console.log "enter" e)
            :on-exiting #(js/console.log "exiting" e)
            :on-exited #(js/console.log "exited" e)}
           [:li "item " e]])] ])))

(defn css-transition-example []
  (let [state (r/atom true)]
    (fn []
      [:div
       [:button
        {:on-click (fn [_] (swap! state not))}
        "Toggle"]
       [:> CSSTransition
        {:classNames "fade"
         :timeout 500
         :in @state
         :on-enter #(js/console.log "enter")
         :on-entering #(js/console.log "entering")
         :on-entered #(js/console.log "entered")
         :on-exit #(js/console.log "enter")
         :on-exiting #(js/console.log "exiting")
         :on-exited #(js/console.log "exited")}
        [:div {:class (if-not @state "hide")} "foobar"]]])))

(defn main []
  [:div
   [:h1 "Transition group example"]
   [transition-group-example]

   [:h1 "CSS transition example"]
   [css-transition-example]])

(defn start []
  (rdom/render [main] (js/document.getElementById "app")))

(start)
