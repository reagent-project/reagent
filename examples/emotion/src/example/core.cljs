(ns example.core
  (:require ["@emotion/react" :as emotion]
            [example.compiler :refer [emotion-compiler]]
            [example.syntax :as s]
            [goog.object :as obj]
            [reagent.impl.protocols :as p]
            [reagent.impl.template :as t]
            [reagent.dom :as rdom]))

(def global
  {"*, *::before, *::after"                              {:box-sizing "border-box"}
   "body, h1, h2, h3, h4, p, figure, blockquote, dl, dd" {:margin 0}
   "ul[role='list'], ol[role='list']"                    {:list-style "none"}
   "html:focus-within"                                   {:scroll-behaviour "smooth"}
   "body"                                                {:line-height    1.5
                                                          :min-height     "100vh"
                                                          :text-rendering "optimizeSpeed"}
   "a:not([class])"                                      {:text-decoration-skip-ink "auto"}
   "img, picture"                                        {:display   "block"
                                                          :max-width "100%"}
   "input, button, textarea, select"                     {:font "inherit"}})

(def theme
  {:palette {:primary   "hotpink"
             :secondary "darkmagenta"
             :bg        "lightgray"}})

(defn css [styles]
  (-> styles t/convert-prop-value emotion/css))

(defn container-css [{{:keys [bg]} :palette}]
  {:background-color bg
   :min-height       "100vh"
   "&>div"           {:max-width 614
                      :margin    "auto"
                      :padding   14}})

(defn container [& children]
  [:div {:css container-css}
   (into [:div] children)])

(defn p-css [theme]
  {:color          (get-in theme [:palette :secondary])
   :padding-bottom 14})

(defn object-style-example []
  [:span {:css {:color "hotpink"}}
   "This text should be hotpink"])

(defn theme-example []
  [:span {:css (fn [theme]
                 {:color (get-in theme
                           [:palette :secondary])})}
   "This text should be darkmagenta"])

(defn primary-bg
  [{{:keys [primary]} :palette}]
  (css {:background-color primary}))

(def default-text-color (css {:color "white"}))

(defn share-example []
  [:div {:css [primary-bg default-text-color]}
   [:span
    "You can use the " [:code "css"] " function "
    "to share your styles across components."]])

(defn multiple-css-example []
  (let [base   {:background-color "blue"
                :color            "white"
                :padding          "0 8px"}
        more-1 {:text-transform "uppercase"}
        more-2 (fn [{{:keys [primary]} :palette}]
                 {:background-color primary})]
    [:ul
     [:li {:css [base]} "[base]"]
     [:li {:css [base more-1]} "[base more-1]"]
     [:li {:css [base more-1 more-2]} "[base more-1 more-2]"]]))

(defn example [{:keys [code title comp]}]
  [:li {:css {:margin-bottom 14}}
   [:span title]
   [:code code]
   [:div comp]])

(defn content []
  [container
   [:h1 {:css {:text-align    "center"
               :margin-bottom 28}}
    [:a {:css    (fn [theme] {:color (get-in theme [:palette :primary])})
         :href   "https://emotion.sh/docs/introduction"
         :target "_blank"}
     "Emotion"]
    " Example 💅"]
   [:p {:css p-css}
    "Emotion is a css-in-js solution that you can use within your reagent project. "
    "This example uses the reagent compiler protocol to replace " [:code "react/createElement"]
    " with " [:code "emotion/jsx"] " to enable the " [:code ":css"] " prop provided by emotion."]
   [:p {:css p-css}
    "If you've ever used " [:a {:css    {:color "#007FFF"}
                                :href   "https://mui.com/"
                                :target "_blank"} "Material UI"] ", "
    "the " [:code ":sx"] " prop is almost the same as emotion's " [:code ":css"] ". "
    "In fact, mui uses emotion under the hood."]


   [:h2 [:code ":css"] " usage"]
   [:p {:css p-css}
    "This compiler handles emotion's css api including themeing and composition. Below are a few examples."]

   [:ol {:css {"&>li>code" {:display          "block"
                            :padding          14
                            :margin           14
                            :background-color "beige"
                            :border-radius    4}}}
    [example {:code  (s/src-of [:object-style-example])
              :comp  [object-style-example]
              :title "Object style"}]

    [example {:code  (s/src-of [:theme-example
                                :theme
                                :main])
              :comp  [theme-example]
              :title "Theme"}]

    [example {:code  (s/src-of [:multiple-css-example])
              :comp  [multiple-css-example]
              :title "Css composition"}]

    [example {:code  (s/src-of [:css
                                :primary-bg
                                :default-text-color
                                :share-example])
              :comp  [share-example]
              :title "Share styles"}]]])

(defn main []
  [:<>
   [:> emotion/Global {:styles global}]
    ;; Wrapped in fn to prevent cljs->js conversion
   [:> emotion/ThemeProvider {:theme (fn [] theme)}
    [content]]])



(def compiler (emotion-compiler {}))

(defn ^:dev/after-load start []
  (rdom/render [main] (js/document.getElementById "app") compiler))

(start)
