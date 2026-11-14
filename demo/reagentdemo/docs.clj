(ns reagentdemo.docs
  "Compile-time loading of doc/*.md and API docstrings for the site."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as rt]
            [sitetools.markdown :as md]))

;;; Guides from doc/, navigation from doc/cljdoc.edn

(defn doc-path [file]
  (if (= "doc/README.md" file)
    "doc/index.html"
    (string/replace file #"\.md$" ".html")))

(defn nav-tree
  "cljdoc.edn tree -> [{:title :file :path :children}]"
  [entries]
  (vec (for [[title {:keys [file]} & children] entries]
         (cond-> {:title title}
           file (assoc :file file :path (doc-path file))
           (seq children) (assoc :children (nav-tree children))))))

(defn flatten-nav [tree]
  (mapcat (fn [n] (cons n (flatten-nav (:children n)))) tree))

(defn front-matter
  "Splits a YAML front matter block (`---` lines around `key: value` lines)
  from a doc file. Only flat string values are supported. Doc files use
  `reviewed: YYYY-MM-DD` for the date the page was last reviewed against the
  current Reagent. Returns [{:reviewed \"...\"} body]."
  [md]
  (if-some [[block yaml] (re-find #"^---\r?\n([\s\S]*?)\r?\n---[ \t]*(?:\r?\n|$)" md)]
    [(into {}
           (for [line (string/split-lines yaml)
                 :let [[_ k v] (re-find #"^([\w-]+):\s*(.*?)\s*$" line)]
                 :when k]
             [(keyword k) (string/replace v #"^(['\"])(.*)\1$" "$2")]))
     (subs md (count block))]
    [{} md]))

(defmacro doc-site
  "Returns {:nav [...] :pages [{:path :title :file :html :headings :meta}]}.
  `:meta` is the file's front matter."
  []
  (let [tree (-> "doc/cljdoc.edn" slurp edn/read-string :cljdoc.doc/tree nav-tree)
        pages (for [{:keys [file path title]} (flatten-nav tree)
                    :when file
                    :let [[meta md] (front-matter (slurp file))]]
                (-> (md/render md (.getParent (io/file file)))
                    ;; Search text is derived from the html at runtime
                    (dissoc :text)
                    (assoc :path path :title title :file file :meta meta)))]
    {:nav tree
     :pages (vec pages)}))

;;; API docs from source files

(def api-namespaces
  "[ns files summary]. Summary is used when the ns has no docstring."
  [["reagent.core" ["src/reagent/core.cljs" "src/reagent/core.clj"]
    "Components, Reagent atoms, cursors, reactions and React interop."]
   ["reagent.dom.client" ["src/reagent/dom/client.cljs"]
    "Render into React roots (React 18 and 19)."]
   ["reagent.dom" ["src/reagent/dom.cljs"]
    "Legacy ReactDOM render API. Not available with React 19."]
   ["reagent.dom.server" ["src/reagent/dom/server.cljs"]
    "Render components to HTML strings for server-side rendering."]
   ["reagent.ratom" ["src/reagent/ratom.cljs" "src/reagent/ratom.clj"]
    "Reactive atoms, reactions and tracking."]
   ["reagent.hooks" ["src/reagent/hooks.cljs"]
    "React hooks that use Clojure value equality for deps and state."]])

(defn read-forms [file]
  (let [rdr (rt/indexing-push-back-reader (slurp file) 1 file)
        eof (Object.)]
    (binding [reader/*data-readers* {}
              reader/*default-data-reader-fn* (fn [_tag v] v)
              reader/*alias-map* (fn [sym] sym)
              *ns* (create-ns 'reagentdemo.docs.reading)]
      (loop [acc []]
        (let [form (reader/read {:eof eof :read-cond :allow :features #{:cljs}} rdr)]
          (if (identical? eof form)
            acc
            (recur (conj acc form))))))))

(defn unindent
  "Docstrings indent continuation lines; remove the common indentation so
  Markdown doesn't treat them as code blocks."
  [s]
  (let [[first-line & more] (string/split-lines s)
        indent (->> more
                    (remove string/blank?)
                    (map #(count (re-find #"^ *" %)))
                    (reduce min 0x7fffffff))
        indent (if (= 0x7fffffff indent) 0 indent)]
    (string/join "\n" (cons first-line (map #(if (string/blank? %) "" (subs % indent)) more)))))

(def def-types
  '{defn :function, defmacro :macro, def :var, defonce :var, defprotocol :protocol
    defc :macro})

(defn var-info [form]
  (when (and (seq? form) (contains? '#{defn defmacro def defonce defprotocol} (first form)))
    (let [[op sym & more] form
          m (meta sym)
          [doc more] (if (string? (first more)) [(first more) (rest more)] [nil more])
          [attr more] (if (map? (first more)) [(first more) (rest more)] [nil more])
          m (merge m attr)
          arglists (or (some-> (:arglists m) (as-> a (if (and (seq? a) (= 'quote (first a))) (second a) a)))
                       (case op
                         (defn defmacro) (if (vector? (first more))
                                           (list (first more))
                                           (map first (filter seq? more)))
                         nil))
          doc (or doc (:doc m))]
      (when-not (or (:private m) (:no-doc m) (= op 'defn-))
        (cond-> {:name (str sym)
                 :type (def-types op)
                 :arglists (mapv pr-str arglists)
                 :line (:line (meta form))}
          doc (assoc :doc-html (:html (md/render (unindent doc))))
          (:deprecated m) (assoc :deprecated (str (:deprecated m))))))))

(defn ns-doc [forms]
  (let [[op _ & more] (first forms)]
    (when (and (= 'ns op) (string? (first more)))
      (first more))))

(defmacro api-docs
  "Returns [{:ns :doc-html :vars [...]}] for the public namespaces."
  []
  (vec (for [[ns-name files summary] api-namespaces]
         (let [forms (map read-forms files)
               doc (some ns-doc forms)
               vars (->> (map (fn [file forms]
                                (keep #(some-> (var-info %) (assoc :file file)) forms))
                              files forms)
                         (apply concat)
                         (group-by :name)
                         ;; cljs fn and clj macro can share a name, prefer the macro
                         (map (fn [[_ vs]] (or (first (filter #(= :macro (:type %)) vs)) (first vs))))
                         (sort-by :name)
                         vec)]
           {:ns ns-name
            :vars vars
            :doc-html (:html (md/render (unindent (or doc summary))))}))))

(defmacro inline-file
  "File contents as a string, at compile time."
  [file]
  (slurp file))

(defmacro project-version
  "Version from project.clj."
  []
  (-> "project.clj" slurp read-string (nth 2)))
