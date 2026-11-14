(ns reagentdemo.docs
  "Guide pages from doc/*.md and API pages from docstrings."
  (:require-macros [reagentdemo.docs :refer [doc-site api-docs]])
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [sitetools.core :as tools :refer [link]]))

(def site (doc-site))
(def api (api-docs))

(def github-blob "https://github.com/reagent-project/reagent/blob/master/")

(defn api-path [ns-name]
  (str "/api/" ns-name ".html"))

(def api-index "/api/index.html")
(def guide-index "/doc/index.html")

;;; Layout

(defn current-path []
  (get-in @tools/config [:current-page :path]))

(r/defc nav-item [{:keys [title path children]}]
  (let [current (current-path)]
    [:li
     (if path
       [link {:href (str "/" path)
              :class (when (= current (str "/" path)) "active")}
        title]
       [:span.sidebar-section title])
     (when children
       [:ul (for [c children]
              ^{:key (:title c)} [nav-item c])])]))

(defonce sidebar-open (r/atom false))

(r/defc sidebar []
  (let [current (current-path)]
    [:nav.sidebar {:class (when @sidebar-open "open")}
     [:button.sidebar-toggle {:on-click #(swap! sidebar-open not)}
      (if @sidebar-open "Close menu" "Menu")]
     [:ul {:on-click-capture #(reset! sidebar-open false)}
      (for [n (:nav site)]
        ^{:key (:title n)} [nav-item n])
      [:li
       [:span.sidebar-section "API"]
       [:ul
        (for [{ns-name :ns} api]
          ^{:key ns-name}
          [:li [link {:href (api-path ns-name)
                      :class (when (= current (api-path ns-name)) "active")}
                ns-name]])]]]]))

(r/defc toc [headings]
  (let [hs (filter #(<= 2 (:level %) 3) headings)]
    (when (seq hs)
      [:nav.toc
       [:div.toc-title "On this page"]
       [:ul
        (for [{:keys [level text id]} hs]
          ^{:key id}
          [:li {:class (str "toc-h" level)}
           [:a {:href (str "#" id)} text]])]])))

(defn docs-page? [path]
  (or (some-> path (string/starts-with? "/doc/"))
      (some-> path (string/starts-with? "/api/"))))

(r/defc site-footer []
  [:footer.site-footer
   [:a {:href "https://github.com/reagent-project/reagent"} "GitHub"] " · "
   [:a {:href "https://cljdoc.org/d/reagent/reagent/"} "cljdoc"] " · "
   [:a {:href "https://clojurians.slack.com/messages/reagent/"} "#reagent on Clojurians Slack"] " · "
   [link {:href "/news/index.html"} "Old release notes"]])

(r/defc docs-layout [{:keys [headings]} & content]
  ;; The footer is inside the content column so the grid reaches the page
  ;; bottom and the sticky sidebars are never pushed up by it.
  [:div.docs-layout
   [sidebar]
   [:div.docs-main
    (into [:article.docs-content] content)
    [site-footer]]
   [toc headings]])

(r/defc raw-html [tag props s]
  [tag (assoc props
              :on-click tools/internal-link-handler
              :dangerouslySetInnerHTML (r/unsafe-html s))])

;;; Guide pages

(r/defc doc-page [{:keys [html file title] :as page}]
  [docs-layout page
   (when-not (string/starts-with? html "<h1")
     [:h1 title])
   [raw-html :div.markdown {} html]
   [:p.edit-link
    [:a {:href (str github-blob file)} "Edit this page on GitHub"]]])

;;; API pages

(declare html->text)

(defn first-sentence [s]
  (let [s (string/trim s)]
    (or (re-find #"^.*?\.(?=\s|$)" s) s)))

(defn arglist->usage [var-name arglist]
  ;; "[x & rest]" -> "(atom x & rest)"
  (let [args (subs arglist 1 (dec (count arglist)))]
    (str "(" var-name (when-not (string/blank? args) (str " " args)) ")")))

(r/defc api-var [{:keys [name type arglists doc-html deprecated file line]}]
  [:section.api-var
   [:h3 {:id name}
    [:a.anchor {:href (str "#" name)} name]
    [:span.api-type (cljs.core/name type)]
    (when deprecated [:span.api-deprecated "deprecated"])]
   (when (seq arglists)
     [:pre.api-usage
      (string/join "\n" (map #(arglist->usage name %) arglists))])
   (if doc-html
     [raw-html :div.markdown {} doc-html]
     [:p.api-nodoc "No docstring."])
   [:a.api-source {:href (str github-blob file "#L" line)} "Source"]])

(r/defc api-ns-page [{ns-name :ns :keys [doc-html vars]}]
  [docs-layout {:headings (for [v vars] {:level 2 :text (:name v) :id (:name v)})}
   [:h1 ns-name]
   (when doc-html [raw-html :div.markdown {} doc-html])
   (for [v vars]
     ^{:key (:name v)} [api-var v])])

(r/defc api-index-page []
  [docs-layout {}
   [:h1 "API reference"]
   [:p "Generated from the docstrings in the Reagent source. "
    "Docs for released versions are also on "
    [:a {:href "https://cljdoc.org/d/reagent/reagent/"} "cljdoc"] "."]
   (for [{ns-name :ns :keys [vars doc-html]} api]
     ^{:key ns-name}
     [:section.api-ns-summary
      [:h2 [link {:href (api-path ns-name)} ns-name]]
      (when doc-html
        [:p.api-ns-doc (first-sentence (html->text doc-html))])
      [:div.api-var-list
       (for [{:keys [name]} vars]
         ^{:key name}
         [link {:href (str (api-path ns-name) "#" name)} name])]])])

;;; Search

(defn html->text [s]
  (-> s
      (string/replace #"<[^>]+>" " ")
      (string/replace "&quot;" "\"")
      (string/replace "&lt;" "<")
      (string/replace "&gt;" ">")
      (string/replace "&amp;" "&")
      (string/replace #"\s+" " ")))

(def search-index
  (delay
    (vec
     (concat
      (for [{:keys [path title html headings]} (:pages site)
            entry (cons {:title title :path (str "/" path) :text (html->text html)}
                        (for [{:keys [text id]} headings
                              :when (not= text title)]
                          {:title text :context title
                           :path (str "/" path "#" id)}))]
        entry)
      (for [{ns-name :ns :keys [vars]} api
            {:keys [name doc-html]} vars]
        {:title name
         :context ns-name
         :path (str (api-path ns-name) "#" name)
         :text (some-> doc-html html->text)
         :api true})))))

(defn snippet [text q]
  (when-some [i (some-> text string/lower-case (string/index-of q))]
    (let [start (max 0 (- i 40))]
      (str (when (pos? start) "…")
           (subs text start (min (count text) (+ i (count q) 80)))
           "…"))))

(defn search [query]
  (let [q (string/lower-case (string/trim query))]
    (when (<= 2 (count q))
      (->> @search-index
           (keep (fn [{:keys [title text api] :as e}]
                   (let [t (string/lower-case title)
                         score (cond
                                 (= t q) 100
                                 (string/starts-with? t q) 50
                                 (string/includes? t q) 30
                                 (some-> text string/lower-case (string/includes? q)) 10)]
                     (when score
                       (assoc e
                              :score (+ score (if api 1 0))
                              :snippet (when (< score 30) (snippet text q)))))))
           (sort-by (comp - :score))
           (take 12)))))

(r/defc search-box []
  (r/with-let [query (r/atom "")
               go! (fn [path]
                     (reset! query "")
                     (tools/goto-page path))]
    (let [results (search @query)]
      [:div.search
       [:input {:type "search"
                :placeholder "Search docs & API"
                :aria-label "Search"
                :value @query
                :on-change #(reset! query (.. % -target -value))
                :on-key-down (fn [e]
                               (case (.-key e)
                                 "Enter" (when-some [r (first results)] (go! (:path r)))
                                 "Escape" (reset! query "")
                                 nil))}]
       (when (seq @query)
         [:ul.search-results
          (if (empty? results)
            [:li.search-empty "No results"]
            (for [{:keys [path title context snippet]} results]
              ^{:key path}
              [:li
               [:a {:href (subs path 1)
                    :on-click (fn [e] (.preventDefault e) (go! path))}
                [:span.search-title title]
                (when context [:span.search-context context])
                (when snippet [:span.search-snippet snippet])]]))])])))

;;; Pages

(doseq [{:keys [path title] :as page} (:pages site)]
  (tools/register-page (str "/" path) [doc-page page] title))

(tools/register-page api-index [api-index-page] "API")

(doseq [{ns-name :ns :as n} api]
  (tools/register-page (api-path ns-name) [api-ns-page n] ns-name))
