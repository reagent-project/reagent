(ns sitetools.markdown
  "Build-time Markdown rendering for the site (used from macros)."
  (:require [clojure.string :as string]
            [sitetools.highlight :as hl])
  (:import [org.commonmark.ext.autolink AutolinkExtension]
           [org.commonmark.ext.gfm.tables TablesExtension]
           [org.commonmark.node Code FencedCodeBlock HardLineBreak Heading IndentedCodeBlock Link Node Paragraph SoftLineBreak Text]
           [org.commonmark.parser Parser]
           [org.commonmark.renderer NodeRenderer]
           [org.commonmark.renderer.html AttributeProvider AttributeProviderFactory HtmlNodeRendererContext HtmlNodeRendererFactory HtmlRenderer HtmlWriter]))

(def github-blob "https://github.com/reagent-project/reagent/blob/master/")

(def extensions [(TablesExtension/create) (AutolinkExtension/create)])

(def ^Parser parser (-> (Parser/builder) (.extensions extensions) (.build)))

(defn children [^Node node]
  (take-while some? (iterate #(.getNext ^Node %) (.getFirstChild node))))

(defn node-seq [node]
  (tree-seq (constantly true) children node))

(defn node-text
  "Plain text of a node and its children."
  [node]
  (->> (node-seq node)
       (keep #(cond
                (instance? Text %) (.getLiteral ^Text %)
                (instance? Code %) (.getLiteral ^Code %)
                (instance? FencedCodeBlock %) (.getLiteral ^FencedCodeBlock %)
                (instance? IndentedCodeBlock %) (.getLiteral ^IndentedCodeBlock %)
                (or (instance? SoftLineBreak %) (instance? HardLineBreak %)
                    (instance? Paragraph %) (instance? Heading %)) " "))
       (apply str)))

(defn slug [s]
  (-> s
      string/lower-case
      (string/replace #"[^\p{L}\p{N}\s-]" "")
      string/trim
      (string/replace #"\s+" "-")))

(defn normalize-path
  "Resolve `..` and `.` segments in a relative path."
  [path]
  (->> (string/split path #"/")
       (reduce (fn [acc part]
                 (case part
                   ("" ".") acc
                   ".." (if (seq acc) (pop acc) (conj acc ".."))
                   (conj acc part)))
               [])
       (string/join "/")))

(defn rewrite-href
  "Links between docs point to the site pages, links to other repository files
  point to GitHub. `dir` is the directory of the current file, e.g. \"doc/FAQ\"."
  [dir href]
  (if (or (re-find #"^[a-z]+:" href)
          (string/starts-with? href "#")
          (string/starts-with? href "/"))
    href
    (let [[path anchor] (string/split href #"#" 2)
          full (normalize-path (str dir "/" path))
          anchor (when anchor (str "#" anchor))]
      (cond
        (and (string/starts-with? full "doc/")
             (string/ends-with? full ".md"))
        (str (if (= "doc/README.md" full)
               "doc/index.html"
               (string/replace full #"\.md$" ".html"))
             anchor)

        :else (str github-blob full anchor)))))

(def clojure-langs #{"clojure" "clj" "cljs" "cljc" "clojurescript" "edn"})

(defn clojure-code?
  "Fenced blocks tagged as Clojure, and untagged blocks that look like Clojure."
  [lang code]
  (if (string/blank? lang)
    (boolean (re-find #"^\s*[(\[{;:]" code))
    (contains? clojure-langs (string/lower-case (first (string/split (string/trim lang) #"\s+"))))))

(def code-block-renderer
  (reify HtmlNodeRendererFactory
    (create [_ ctx]
      (let [^HtmlWriter w (.getWriter ^HtmlNodeRendererContext ctx)]
        (reify NodeRenderer
          (getNodeTypes [_] #{FencedCodeBlock IndentedCodeBlock})
          (render [_ node]
            (let [[lang code] (if (instance? FencedCodeBlock node)
                                [(.getInfo ^FencedCodeBlock node) (.getLiteral ^FencedCodeBlock node)]
                                [nil (.getLiteral ^IndentedCodeBlock node)])
                  clj? (clojure-code? lang code)]
              (.line w)
              (.raw w (str "<pre" (when clj? " class=\"hl\"") "><code>"
                           (if clj? (hl/highlight-html code) (hl/escape-html code))
                           "</code></pre>"))
              (.line w))))))))

(defn renderer [dir]
  (-> (HtmlRenderer/builder)
      (.extensions extensions)
      (.nodeRendererFactory code-block-renderer)
      (.attributeProviderFactory
       (reify AttributeProviderFactory
         (create [_ _ctx]
           (reify AttributeProvider
             (setAttributes [_ node _tag attrs]
               (cond
                 (instance? Heading node)
                 (.put attrs "id" (slug (node-text node)))

                 (and dir (instance? Link node))
                 (.put attrs "href" (rewrite-href dir (.getDestination ^Link node)))))))))
      (.build)))

(defn headings
  "[{:level 2 :text \"...\" :id \"...\"}] of the document."
  [doc]
  (vec (for [n (node-seq doc)
             :when (instance? Heading n)
             :let [t (string/trim (node-text n))]]
         {:level (.getLevel ^Heading n) :text t :id (slug t)})))

(defn plain-text
  "Text content for search."
  [doc]
  (-> (node-text doc)
      (string/replace #"\s+" " ")
      string/trim))

(defn render
  "Render markdown string. `dir` is used to resolve relative links."
  ([md] (render md nil))
  ([md dir]
   (let [doc (.parse parser md)]
     {:html (.render ^HtmlRenderer (renderer dir) doc)
      :headings (headings doc)
      :text (plain-text doc)})))
