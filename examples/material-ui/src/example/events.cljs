(ns example.events
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [example.utils.http-fx :refer  [GET POST PUT <sub >evt set-location]]
            [goog.object :as gobj]
            [example.db :as db]
            [reagent.impl.template :as rtpl]))

;; events

(re-frame/reg-event-db              ;; sets up initial application state
  :initialize                 ;; usage:  (dispatch [:initialize])
  (fn [_ _]
    db/default-db))

(re-frame/reg-event-db
 ::set-active-demo
 (fn [db [_ active-demo]]
   (assoc db :active-demo active-demo)))

(re-frame/reg-event-db :active-demo
 (fn [db [_ active-demo]]
   (assoc db :active-demo active-demo)))

(def github-origin "https://api.github.com")

 (re-frame/reg-event-fx :get-github-events
  (fn [{db :db} [_ vals]]
    {:http-xhrio (GET (str github-origin "/orgs/reagent-project/events")
                       :get-github-events-success
                       :get-github-events-fail)}
   ))

 (re-frame/reg-event-db :get-github-events-success
  (fn [db [_ response]]
    (set-location "#/accounts")
    (assoc db :user response)))

 (re-frame/reg-event-db :get-github-events-fail
  (fn [db [_ response]]
    (set-location "#/accounts") ;; will be a failure screen later
    (assoc db :user {:username "adam" :email "whatever" :id 1})))
