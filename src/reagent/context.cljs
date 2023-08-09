(ns reagent.context
  (:require-macros [reagent.core]
                   [reagent.context])
  (:require [react :as react]
            [reagent.core :as r]))

(defn make-context
  "Creates a context with the given name and optional default value.
   The default value will be used when trying to retrieve the context value with no provider present.
   Attempting to retrieve a context value that has no provider, and no default value will result in a crash."
  ([name]
   (let [context (react/createContext)]
     (set! (.-displayName context) name)
     context))
  ([name default-value]
   (let [context (react/createContext default-value)]
     (set! (.-displayName context) name)
     context)))

(defn provider
  "Provides the value for the given context to descendant components."
  [{:keys [context value]} & contents]
  (into [:r> (.-Provider context) #js{:value value}]
        contents))

(defn consumer
  "Retrieves the value for the given context.
   render-f must be a reagent render function that will be called with the value.
   If there's no provider, will return the default value if it is set, or throw otherwise."
  [{:keys [context]} render-f]
  ;; Use with-let to maintain a stable render function, otherwise the child will
  ;; remount every time a new prop comes into the parent.
  (r/with-let [wrapper-comp
               ;; Passes through context to component using meta data. See:
               ;; https://github.com/reagent-project/reagent/blob/ce80585e9aebe0a6df09bda1530773aa512f6103/doc/ReactFeatures.md#context
               ^{:context-type context}
               (fn [render-f]
                 (let [value (.-context (r/current-component))]
                   (when (undefined? value)
                     (throw (js/Error. (str "Missing provider for " (.-displayName context)))))
                   (render-f value)))]
    [wrapper-comp render-f]))
