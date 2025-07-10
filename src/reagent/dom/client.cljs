(ns reagent.dom.client
  (:require ["react" :as react]
            ["react-dom/client" :as react-dom-client]
            [reagent.impl.batching :as batch]
            [reagent.impl.protocols :as p]
            [reagent.impl.template :as tmpl]
            [reagent.impl.util :as util]
            [goog.object :as gobj]
            [reagent.core :as r]))

(defn create-root
  "Create a React Root connected to given container DOM element."
  ([container]
   (react-dom-client/createRoot container))
  ([container options]
   ;; TODO: Coerce options from Cljs to JS? Just cljs->js or only handle
   ;; known keys?
   ;; Also allow JS values without conversion.
   (react-dom-client/createRoot container options)))

(defn unmount
  "Unmount the given React Root"
  [^js root]
  (.unmount root))

;; Wrapper component notes:
;; reagent-root wrapper is used to flush after-render queue after the
;; initial mounting, only after React has really mounted nodes to DOM.
;; FIXME: later after-render calls don't wait for React to mount nodes to DOM.
;;
;; comp wrapper from render or hydrate-root is used to convert the Reagent
;; hiccup into React elements. This component is re-created on every render
;; call to ensure React will consider it a new component always?
;; This does mean Reagent will wrap the whole app in two levels of wrapper
;; components. Is this a problem? No idea.
;; Trying to use comp as a regular function in reagent-root, or calling
;; as-element from reagent-root directlly didn't seem to work with
;; live reloads. React didn't see new version of a component in a multi
;; ns projects after changes.

(defn- reagent-root [^js js-props]
  ;; This will flush initial r/after-render callbacks.
  ;; Later that queue will be flushed on Reagent render-loop.
  (react/useEffect (fn []
                     (binding [util/*always-update* false]
                       (batch/flush-after-render)
                       js/undefined)))
  (binding [util/*always-update* true]
    (react/createElement (.-comp js-props))))

(defn render
  "Render the given Reagent element (i.e. Hiccup data)
  into a given React root."
  ([^js root el]
   (render root el tmpl/*current-default-compiler*))
  ([^js root el compiler]
   (let [comp (fn [] (r/as-element el compiler))
         js-props #js {}]
     (set! (.-comp js-props) comp)
     (.render root (react/createElement reagent-root js-props)))))

(defn hydrate-root
  ([container el]
   (hydrate-root container el nil))
  ([container el {:keys [compiler on-recoverable-error identifier-prefix]
                  :or {compiler tmpl/*current-default-compiler*}}]
   (let [js-props #js {}
         comp (fn [] (r/as-element el compiler))]
     (set! (.-comp js-props) comp)
     (react-dom-client/hydrateRoot container (react/createElement reagent-root js-props)))))
