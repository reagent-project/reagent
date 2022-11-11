(ns reagent.dom.client
  (:require ["react" :as react]
            ["react-dom/client" :as react-dom-client]
            [reagent.impl.batching :as batch]
            [reagent.impl.protocols :as p]
            [reagent.impl.template :as tmpl]
            [reagent.impl.util :as util]
            [goog.object :as gobj]))

(defn create-root
  "Create a React Root connected to given container DOM element."
  [container]
  (react-dom-client/createRoot container))

(defn unmount
  "Unmount the given React Root"
  [root]
  (.unmount root))

(defn- reagent-root [^js js-props]
  ;; This will flush initial r/after-render callbacks.
  ;; Later that queue will be flushed on Reagent render-loop.
  (let [el (gobj/get js-props "comp")]
    (react/useEffect (fn []
                       (binding [util/*always-update* false]
                         (batch/flush-after-render)
                         js/undefined)))
    (binding [util/*always-update* true]
      (el))))

(defn render
  "Render the given Reagent element (i.e. Hiccup data)
  into a given React root."
  ([root el]
   (render root el tmpl/*current-default-compiler*))
  ([root el compiler]
   (let [;; Not sure if this should be fn here?
         ;; At least this moves the as-element call to the reagent-root
         ;; render, and handles the *always-update* binding correctly?
         comp (fn [] (p/as-element compiler el))]
     (.render root (react/createElement reagent-root #js {:comp comp})))))

(defn hydrate-root
  ([container el]
   (hydrate-root container el nil))
  ([container el {:keys [compiler on-recoverable-error identifier-prefix]
                  :or {compiler tmpl/*current-default-compiler*}}]
   (let [comp (fn [] (p/as-element compiler el))]
     (react-dom-client/hydrateRoot container (react/createElement reagent-root #js {:comp comp})))))
