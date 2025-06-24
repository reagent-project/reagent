(ns example.core
  (:require ["react" :as react]
            ["react-transition-group" :refer [CSSTransition TransitionGroup]]
            [reagent.core :as r]
            [reagent.dom.client :as rdomc]))

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
        (for [e (:elements @state)
              ;; Is this correct? no idea
              :let [node-ref (react/createRef)]]
          ;; Can't move this to separate function, or reagent will add component in between and transitions break
          [:> CSSTransition
           {:key e
            :node-ref node-ref
            :classNames "fade"
            :timeout 500
            :on-enter #(js/console.log "enter" e)
            :on-entering #(js/console.log "entering" e)
            :on-entered #(js/console.log "entered" e)
            :on-exit #(js/console.log "enter" e)
            :on-exiting #(js/console.log "exiting" e)
            :on-exited #(js/console.log "exited" e)}
           [:li {:ref node-ref}
            "item " e]])]])))

(defn css-transition-example []
  (let [state (r/atom true)]
    (fn []
      (let [node-ref (react/useRef nil)]
        [:div
         [:button
          {:on-click (fn [_] (swap! state not))}
          "Toggle"]
         [:> CSSTransition
          ;; On React 19 findDOMNode is not available and all transitions need to provide
          ;; nodeRef
          {:node-ref node-ref
           :classNames "fade"
           :timeout 500
           :in @state
           :on-enter #(js/console.log "enter")
           :on-entering #(js/console.log "entering")
           :on-entered #(js/console.log "entered")
           :on-exit #(js/console.log "enter")
           :on-exiting #(js/console.log "exiting")
           :on-exited #(js/console.log "exited")}
          [:div {:ref node-ref
                 :class (when-not @state "hide")} "foobar"]]]))))

(defn main []
  [:div
   [:h1 "Transition group example"]
   [transition-group-example]

   [:h1 "CSS transition example"]
   [:f> css-transition-example]])

(defonce react-root (delay (rdomc/create-root (.getElementById js/document "app"))))

(defn ^:export ^:dev/after-load run []
  (rdomc/render @react-root [main]))
