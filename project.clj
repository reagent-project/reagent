(defproject reagent "0.7.0-SNAPSHOT"
  :url "http://github.com/reagent-project/reagent"
  :license {:name "MIT"}
  :description "A simple ClojureScript interface to React"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.562"]
                 ; [cljsjs/react-dom "15.5.4-0"]
                 ; [cljsjs/react-dom-server "15.5.4-0"]
                 ; [cljsjs/create-react-class "15.5.3-0"]
                 ]

  :plugins [[lein-cljsbuild "1.1.6"]
            [lein-codox "0.10.3"]]

  :source-paths ["src"]

  :codox {:language :clojurescript
          :exclude clojure.string
          :source-paths ["src"]}

  :profiles {:test {:cljsbuild
                    {:builds {:client {:source-paths ["test"]
                                       :notify-command ["node" "bin/gen-site.js"]
                                       :compiler
                                       {:main "reagenttest.runtests"}}}}}

             :fig [{:dependencies [[figwheel "0.5.10"]]
                    :plugins [[lein-figwheel "0.5.10"]]
                    :source-paths ["demo"] ;; for lighttable
                    :resource-paths ["site" "outsite"]
                    :figwheel {:css-dirs ["site/public/css"]}
                    :cljsbuild
                    {:builds
                     {:client
                      {:figwheel true
                       :compiler {:source-map true
                                  :optimizations :none
                                  ;; :recompile-dependents false
                                  :output-dir "outsite/public/js/out"
                                  :asset-path "js/out"
                                  :language-in :ecmascript6
                                  :closure-warnings {:non-standard-jsdoc :off}
                                  ;; From node-inputs, with file requiring react, react-dom, react-dom/server, create-react-class
                                  :foreign-libs [{:file "node_modules/create-react-class/index.js"
                                                  :module-type :commonjs
                                                  :provides ["create-react-class"]}
                                                 {:file "node_modules/react/react.js"
                                                  :module-type :commonjs
                                                  :provides ["react"]}
                                                 {:file "node_modules/react-dom/index.js"
                                                  :module-type :commonjs
                                                  :provides ["react-dom"]}
                                                 {:file "node_modules/react-dom/server.js"
                                                  :module-type :commonjs
                                                  ;; Added manually
                                                  :provides ["react-dom.server"]}
                                                 {:file "node_modules/react/lib/React.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/create-react-class/factory.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOM.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMServer.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactChildren.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactPureComponent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactComponent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactClass.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactDOMFactories.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactElement.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactPropTypes.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactVersion.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/onlyChild.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/canDefineProperty.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactElementValidator.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMComponentTree.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDefaultInjection.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactMount.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactReconciler.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactUpdates.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactVersion.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/findDOMNode.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/getHostComponentFromComposite.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/renderSubtreeIntoContainer.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactInstrumentation.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMUnknownPropertyHook.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMNullInputValuePropHook.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMInvalidARIAHook.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactServerRendering.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/create-react-class/node_modules/fbjs/lib/emptyObject.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/create-react-class/node_modules/fbjs/lib/invariant.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/create-react-class/node_modules/fbjs/lib/warning.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/create-react-class/node_modules/object-assign/index.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/PooledClass.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/traverseAllChildren.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactNoopUpdateQueue.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/reactProdInvariant.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactPropTypeLocationNames.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactCurrentOwner.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactElementSymbol.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactComponentTreeHook.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/checkReactTypeSpec.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/getIteratorFn.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/reactProdInvariant.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/DOMProperty.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMComponentFlags.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/DOMLazyTree.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactBrowserEventEmitter.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMContainerInfo.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMFeatureFlags.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactFeatureFlags.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactInstanceMap.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactMarkupChecksum.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactUpdateQueue.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/instantiateReactComponent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/setInnerHTML.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/shouldUpdateReactComponent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactRef.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/CallbackQueue.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/PooledClass.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/Transaction.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ARIADOMPropertyConfig.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/BeforeInputEventPlugin.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ChangeEventPlugin.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/DefaultEventPluginOrder.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/EnterLeaveEventPlugin.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/HTMLDOMPropertyConfig.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactComponentBrowserEnvironment.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMComponent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMTreeTraversal.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMTextComponent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMEmptyComponent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactEventListener.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDefaultBatchingStrategy.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactInjection.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactReconcileTransaction.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SVGDOMPropertyConfig.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SelectEventPlugin.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SimpleEventPlugin.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDebugTool.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/EventPluginRegistry.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactNodeTypes.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactServerBatchingStrategy.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactServerRenderingTransaction.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/object-assign/index.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/create-react-class/node_modules/fbjs/lib/emptyFunction.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/KeyEscapeUtils.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/ReactPropTypesSecret.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/DOMNamespaces.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/createMicrosoftUnsafeLocalFunction.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/setTextContent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactEventEmitterMixin.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ViewportMetrics.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/getVendorPrefixedEventName.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/isEventSupported.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/validateDOMNesting.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/adler32.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactCompositeComponent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactEmptyComponent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactHostComponent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactOwner.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/EventPropagators.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/FallbackCompositionState.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticInputEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/EventPluginHub.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticCompositionEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/getEventTarget.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/isTextInputElement.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticMouseEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/DOMChildrenOperations.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMIDOperations.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/AutoFocusUtils.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/CSSPropertyOperations.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/DOMPropertyOperations.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMInput.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMOption.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMSelect.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMTextarea.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactMultiChild.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/escapeTextContentForBrowser.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/EventPluginUtils.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactComponentEnvironment.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactInputSelection.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticAnimationEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticClipboardEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticFocusEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticKeyboardEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticDragEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticTouchEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticTransitionEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticUIEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/SyntheticWheelEvent.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/getEventCharCode.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactInvalidSetStateWarningHook.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactHostOperationHistoryHook.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactServerUpdateQueue.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/fbjs/lib/warning.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/ExecutionEnvironment.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/warning.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/object-assign/index.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/prop-types/factory.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactErrorUtils.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/checkReactTypeSpec.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/accumulateInto.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/forEachAccumulated.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/getTextContentAccessor.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/getEventModifierState.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/Danger.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/CSSProperty.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/dangerousStyleValue.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/quoteAttributeValueForBrowser.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/LinkedValueUtils.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactChildReconciler.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/flattenChildren.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactDOMSelection.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/getEventKey.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/fbjs/lib/emptyFunction.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/fbjs/lib/emptyObject.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/fbjs/lib/invariant.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/invariant.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/emptyObject.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/emptyFunction.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/prop-types/factoryWithTypeCheckers.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactPropTypeLocationNames.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactPropTypesSecret.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/KeyEscapeUtils.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/traverseAllChildren.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/getNodeForCharacterOffset.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/shallowEqual.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/EventListener.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/getUnboundedScrollPosition.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/getActiveElement.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/performanceNow.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/prop-types/lib/ReactPropTypesSecret.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/prop-types/checkPropTypes.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/ReactElementSymbol.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/lib/getIteratorFn.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/focusNode.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/camelizeStyleName.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/hyphenateStyleName.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/memoizeStringOnly.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/containsNode.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/performance.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/lib/getNextDebugID.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/prop-types/factory.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/createNodesFromMarkup.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/camelize.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/hyphenate.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/isTextNode.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/prop-types/factoryWithTypeCheckers.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/createArrayFromMixed.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/getMarkupWrap.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/lib/isNode.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/prop-types/lib/ReactPropTypesSecret.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/prop-types/checkPropTypes.js"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/create-react-class/package.json"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/package.json"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/package.json"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/create-react-class/node_modules/fbjs/package.json"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/create-react-class/node_modules/object-assign/package.json"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/object-assign/package.json"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/fbjs/package.json"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/fbjs/package.json"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/object-assign/package.json"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react/node_modules/prop-types/package.json"
                                                  :module-type :commonjs}
                                                 {:file "node_modules/react-dom/node_modules/prop-types/package.json"
                                                  :module-type :commonjs}]
                                  ; :npm-deps {:react "15.5.3"
                                  ;            :react-dom "15.5.3"
                                  ;            :create-react-class "15.5.3"}
                                  }}}}}]

             :site {:resource-paths ^:replace ["outsite"]
                    :figwheel {:css-dirs ^:replace ["outsite/public/css"]}}

             :prod [:site
                    {:cljsbuild
                     {:builds {:client
                               {:compiler {:optimizations :advanced
                                           :elide-asserts true
                                           :pretty-print false
                                           ;; :pseudo-names true
                                           :output-dir "target/client"}}}}}]

             :prerender [:prod
                         {:cljsbuild
                          {:builds {:client
                                    {:compiler {:main "reagentdemo.server"
                                                :output-to "pre-render/main.js"
                                                :output-dir "pre-render/out"}
                                     :notify-command ["node" "bin/gen-site.js"] }}}}]

             :webpack {:cljsbuild
                       {:builds {:client
                                 {:compiler
                                  {:foreign-libs
                                   [{:file "target/webpack/bundle.js"
                                     :file-min "target/webpack/bundle.min.js"
                                     :provides ["cljsjs.react.dom"
                                                "cljsjs.react.dom.server"
                                                "cljsjs.react"]
                                     :requires []}]}}}}}

             :prod-test [:prod :test]

             :dev [:fig :test]

             :dev-notest [:fig]}

  :clean-targets ^{:protect false} [:target-path :compile-path
                                    "outsite/public/js"
                                    "outsite/public/site"
                                    "outsite/public/news"
                                    "outsite/public/css"
                                    "outsite/public/index.html"
                                    "out"
                                    "pre-render"]

  :cljsbuild {:builds {:client
                       {:source-paths ["src"
                                       "demo"
                                       "examples/todomvc/src"
                                       "examples/simple/src"
                                       "examples/geometry/src"]
                        :compiler {:parallel-build true
                                   :main "reagentdemo.core"
                                   :output-to "outsite/public/js/main.js"}}}}

  :figwheel {:http-server-root "public" ;; assumes "resources"
             :repl false})
