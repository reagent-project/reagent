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
