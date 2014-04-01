(ns reagentdemo.page
  (:require [reagent.core :as reagent :refer [atom partial]]
            [reagent.interop :refer-macros [.' .! fvar fvar?]]
            [reagent.debug :refer-macros [dbg]]
            [clojure.string :as string]
            [goog.events :as events]
            [goog.history.EventType :as hevt])
  (:import [goog History]
           [goog.history Html5History]))

(def page (atom ""))
(def base-path (atom nil))
(def html5-history false)

(defn create-history []
  (when reagent/is-client
    (let [proto (-> js/window .-location .-protocol)]
      (if (and (.isSupported Html5History)
               (case proto "http:" true "https:" true false))
        (do (set! html5-history true)
            (doto (Html5History.)
              (.setUseFragment false)))
        (History.)))))

(def history (create-history))

(defn setup-history []
  (when-let [h history]
    (events/listen h hevt/NAVIGATE
                   (fn [e]
                     (reset! page (subs (.-token e)
                                        (count @base-path)))
                     (reagent/flush)))
    (add-watch page ::history (fn [_ _ oldp newp]
                                (when-not (= oldp newp)
                                  (.setToken h (str @base-path newp)))))
    (.setEnabled h true)))

(js/setTimeout setup-history 100)

(defn set-start-page [p]
  (when html5-history
    ;; Find base-path for html5 history
    (let [loc (-> js/window .-location .-pathname)
          split #".[^/]*"
          loc-parts (re-seq split loc)
          page-parts (re-seq split (case p "" "." p))
          base (str (apply str
                           (drop-last (count page-parts) loc-parts))
                    "/")]
      (reset! base-path (string/replace base #"^/" ""))))
  (reset! page p))

(def title-atom (atom ""))

(def page-map (atom nil))

(def reverse-page-map (atom nil))

(add-watch page-map ::page-map-watch
           (fn [_ _ _ new-map]
             (reset! reverse-page-map
                     (into {} (for [[k v] new-map]
                                [v k])))))

(defn prefix [href]
  (let [depth (-> #"/" (re-seq @page) count)]
    (str (->> "../" (repeat depth) (apply str)) href)))

(defn link [props child]
  (let [rpm @reverse-page-map
        href (-> props :href rpm)]
    (assert (string? href))
    [:a (assoc props
          :href (prefix href)
          :on-click (if history
                      (fn [e]
                        (.preventDefault e)
                        (reset! page href)
                        (reagent/next-tick
                         #(set! (.-scrollTop (.-body js/document))
                                0)))
                      identity))
     child]))

(add-watch page ::title-watch
           (fn [_ _ _ p]
             ;; First title on a page wins
             (reset! title-atom "")))

(defn title [name]
  (when (= @title-atom "")
    (if reagent/is-client
      (set! (.-title js/document) name))
    (reset! title-atom name))
  [:div])
