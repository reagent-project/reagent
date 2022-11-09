(ns reagenttest.utils)

(defmacro deftest [test-name & body]
  `(do
     (cljs.test/deftest
       ~(with-meta (symbol (str (name test-name) "--class"))
                   (meta test-name))
       (binding [*test-compiler* class-compiler
                 *test-compiler-name* "class"]
         ~@body))
     (cljs.test/deftest
       ~(with-meta (symbol (str (name test-name) "--fn"))
                   (meta test-name))
       (binding [*test-compiler* fn-compiler
                 *test-compiler-name* "fn"]
         ~@body))))

(defmacro act
  [& body]
  `(act* (fn [] ~@body)))

;; Inspired by
;; https://github.com/henryw374/Cljs-Async-Timeout-Tests/blob/master/src/widdindustries/timeout_test.cljc
(defmacro async
  "Similar to cljs.test/async, but presumes the body
  will return single promise and the test is considered done
  when the promise is resolved.

  If promise isn't resolved in given timeout (default 10 seconds),
  test is considered timed out.

  First argument can be a map with options:

  - :timeout - default 10000 ms"
  [& [maybe-opts :as args]]
  (let [[opts body] (if (map? maybe-opts)
                      [maybe-opts (rest args)]
                      [nil args])
        timeout-ms (:timeout opts 10000)]
    `(cljs.test/async done#
       (let [timeout# (js/setTimeout (fn []
                                       (cljs.test/is (= 1 0) "Test timed out")
                                       (done#))
                                     ~timeout-ms)]
         (try
           (-> (do ~@body)
               (.then (fn []
                        (js/clearTimeout timeout#)
                        (done#)))
               (.catch (fn [e#]
                         (js/clearTimeout timeout#)
                         (cljs.test/is (not e#))
                         (done#))))
           (catch js/Error e#
             (js/clearTimeout timeout#)
             (cljs.test/is (not e#))
             (done#)))))))
