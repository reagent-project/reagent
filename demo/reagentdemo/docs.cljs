;; The macros read doc/*.md and src/, which the shadow-cljs cache doesn't
;; track (shadow.resource only tracks classpath files): never cache this ns.
(ns ^:dev/always reagentdemo.docs
  "Guide pages from doc/*.md and API pages from docstrings."
  (:require-macros [reagentdemo.docs :refer [doc-site api-docs github-url]])
  (:require [clojure.string :as string]
            ["react" :as react]
            [reagent.core :as r]
            [sitetools.core :as tools :refer [link]]))

(def site (doc-site))
(def api (api-docs))

(def github (github-url))
(def github-blob (str github "/blob/master/"))

(defn api-path [ns-name]
  (str "/api/" ns-name ".html"))

(def api-index "/api/")
(def guide-index "/doc/")

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

(defn scroll-to-active!
  "Scroll the sidebar so its active item is visible (centered when it was
  out of view). Only the sidebar scrolls, not the page."
  [^js nav]
  (when-some [^js a (and nav (.querySelector nav "a.active"))]
    (let [n (.getBoundingClientRect nav)
          r (.getBoundingClientRect a)]
      (when (or (< (.-top r) (.-top n)) (> (.-bottom r) (.-bottom n)))
        (set! (.-scrollTop nav)
              (+ (.-scrollTop nav)
                 (- (.-top r) (.-top n))
                 (- (/ (.-height n) 2))))))))

(r/defc sidebar []
  (let [current (current-path)
        nav-ref (react/useRef nil)]
    (react/useEffect (fn [] (scroll-to-active! (.-current nav-ref)) js/undefined)
                     #js [current])
    [:nav.sidebar {:class (when @sidebar-open "open")
                   :ref nav-ref}
     [:button.sidebar-toggle {:on-click #(swap! sidebar-open not)}
      (if @sidebar-open "Close menu" "Menu")]
     [:ul {:on-click-capture #(reset! sidebar-open false)}
      (for [n (:nav site)]
        ^{:key (:title n)} [nav-item n])
      [:li
       [:span.sidebar-section "API"]
       [:ul
        [:li [link {:href api-index
                    :class (when (= current api-index) "active")}
              "API reference"]]
        (for [{ns-name :ns} api]
          ^{:key ns-name}
          [:li [link {:href (api-path ns-name)
                      :class (when (= current (api-path ns-name)) "active")}
                ns-name]])]]]]))

(defn current-heading
  "Id of the last heading scrolled past the header, or the last one when
  the page is scrolled to the bottom."
  [ids]
  (let [offset 120
        at-bottom? (>= (+ js/window.scrollY js/window.innerHeight)
                       (- (.. js/document -documentElement -scrollHeight) 2))]
    (if at-bottom?
      (last ids)
      (reduce (fn [found id]
                (if-some [el (js/document.getElementById id)]
                  (if (<= (.-top (.getBoundingClientRect el)) offset)
                    id
                    (reduced found))
                  found))
              (first ids)
              ids))))

(r/defc toc [headings]
  (let [hs (filter #(<= 2 (:level %) 3) headings)
        ids (mapv :id hs)
        [active set-active] (react/useState nil)
        [open? set-open] (react/useState false)
        nav-ref (react/useRef nil)]
    ;; Track the heading in view while scrolling.
    (react/useEffect
     (fn []
       (if (seq ids)
         (let [frame (atom nil)
               update! (fn []
                         (reset! frame nil)
                         (set-active (current-heading ids)))
               on-scroll (fn []
                           (when-not @frame
                             (reset! frame (js/requestAnimationFrame update!))))]
           (update!)
           (js/addEventListener "scroll" on-scroll #js {:passive true})
           (fn []
             (some-> @frame js/cancelAnimationFrame)
             (js/removeEventListener "scroll" on-scroll)))
         js/undefined))
     #js [(string/join " " ids)])
    (react/useEffect (fn [] (scroll-to-active! (.-current nav-ref)) js/undefined)
                     #js [active])
    (when (seq hs)
      [:nav.toc {:ref nav-ref
                 :class (when open? "open")}
       [:div.toc-title "On this page"]
       ;; Narrow screens: the TOC sits above the content, collapsed.
       [:button.toc-toggle {:on-click #(set-open (not open?))
                            :aria-expanded open?}
        "On this page"
        (when-some [t (some #(when (= active (:id %)) (:text %)) hs)]
          [:span.toc-current t])]
       [:ul {:on-click #(set-open false)}
        (for [{:keys [level text id]} hs]
          ^{:key id}
          [:li {:class (str "toc-h" level)}
           [:a {:href (str "#" id)
                :class (when (= id active) "active")}
            text]])]])))

(defn docs-page? [path]
  (or (some-> path (string/starts-with? "/doc/"))
      (some-> path (string/starts-with? "/api/"))))

(r/defc site-footer []
  [:footer.site-footer
   [:a {:href github} "GitHub"] " · "
   [:a {:href "https://cljdoc.org/d/reagent/reagent/"} "cljdoc"] " · "
   [:a {:href "https://clojurians.slack.com/messages/reagent/"} "#reagent on Clojurians Slack"] " · "
   [link {:href "/news/"} "Archive"]])

(r/defc docs-layout [{:keys [headings]} & content]
  ;; The footer is inside the content column so the grid reaches the page
  ;; bottom and the sticky sidebars are never pushed up by it.
  [:div.docs-layout {:on-click tools/internal-link-handler}
   [sidebar]
   [:div.docs-main
    (into [:article.docs-content] content)
    [site-footer]]
   [toc headings]])

(r/defc raw-html [tag props s]
  [tag (assoc props
              :dangerouslySetInnerHTML (r/unsafe-html s))])

;;; Guide pages

;; Shown on pages without `reviewed: YYYY-MM-DD` in their front matter.
(r/defc review-notice []
  [:p.review-notice
   [:strong "Not reviewed yet: "]
   "this page may be outdated or inconsistent with the other guides. The "
   [link {:href api-index} "API reference"]
   " is generated from the current source."])

(r/defc doc-page [{:keys [html file title] {:keys [reviewed]} :meta :as page}]
  [docs-layout page
   (when-not reviewed
     [review-notice])
   (when-not (string/starts-with? html "<h1")
     [:h1 title])
   [raw-html :div.markdown {} html]
   [:p.edit-link
    (when reviewed
      [:span.reviewed "Reviewed " reviewed " · "])
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
   [:p "Generated from the docstrings in the current Reagent source. "
    "API docs for each released version are on "
    ;; Codox output deployed by build-docs.sh; not a site page, so the
    ;; site's link handler leaves it to the browser.
    [:a {:href "docs/"} "the versioned API docs"] " and on "
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

(defn- with-lower-case
  "Lowercased title and text, computed once instead of per keystroke."
  [{:keys [title text] :as e}]
  (assoc e
         :title-lc (string/lower-case title)
         :text-lc (some-> text string/lower-case)))

(def search-index
  (delay
    (mapv
     with-lower-case
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

(defn snippet [text text-lc q]
  (when-some [i (some-> text-lc (string/index-of q))]
    ;; The index is into text-lc; use text only when lowercasing kept the
    ;; length (it doesn't for e.g. "İ"), so the offsets line up.
    (let [text (if (= (count text) (count text-lc)) text text-lc)
          start (max 0 (- i 40))]
      (str (when (pos? start) "…")
           (subs text start (min (count text) (+ i (count q) 80)))
           "…"))))

(defn search [query]
  (let [q (string/lower-case (string/trim query))]
    (when (<= 2 (count q))
      (->> @search-index
           (keep (fn [{:keys [title-lc text text-lc api] :as e}]
                   (let [score (cond
                                 (= title-lc q) 100
                                 (string/starts-with? title-lc q) 50
                                 (string/includes? title-lc q) 30
                                 (some-> text-lc (string/includes? q)) 10)]
                     (when score
                       (assoc e
                              :score (+ score (if api 1 0))
                              :snippet (when (< score 30) (snippet text text-lc q)))))))
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
