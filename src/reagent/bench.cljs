(ns reagent.bench
  (:require [reagent.core :as r]
            ["react-dom/server" :as rds]))

(def hiccup
  [:html
   [:body
    [:div.css-1dbjc4n.r-1iusvr4.r-46vdb2.r-1777fci.r-5f2r5o.r-bcqeeo.r-1mi0q7o
     [:div.css-1dbjc4n.r-18u37iz.r-1wtj0ep.r-zl2h9q
      [:div.css-1dbjc4n.r-1d09ksm.r-18u37iz.r-1wbh5a2
       [:div.css-1dbjc4n.r-1wbh5a2.r-dnmrzs
        [:a.css-4rbku5.css-18t94o4.css-1dbjc4n.r-1loqt21.r-1wbh5a2.r-dnmrzs.r-1ny4l3l
         {:data-focusable "true",
          :role "link",
          :href "/Rainmaker1973",
          :aria-haspopup "false"}]
        [:div.css-1dbjc4n.r-18u37iz.r-1wbh5a2.r-dnmrzs.r-1ny4l3l
         [:div.css-1dbjc4n.r-18u37iz.r-dnmrzs
          [:div.css-901oao.css-bfa6kz.r-hkyrab.r-1qd0xha.r-a023e6.r-vw2c0b.r-ad9z0x.r-bcqeeo.r-3s2u2q.r-qvutc0
           {:dir "auto"}
           [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
            [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
             "Massimo"]]]
          [:div.css-901oao.r-hkyrab.r-18u37iz.r-1q142lx.r-1qd0xha.r-a023e6.r-16dba41.r-ad9z0x.r-bcqeeo.r-qvutc0
           {:dir "auto"}]]
         [:div.css-1dbjc4n.r-18u37iz.r-1wbh5a2.r-1f6r7vd
          [:div.css-901oao.css-bfa6kz.r-1re7ezh.r-18u37iz.r-1qd0xha.r-a023e6.r-16dba41.r-ad9z0x.r-bcqeeo.r-qvutc0
           {:dir "ltr"}
           [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
            "@Rainmaker1973"]]]]]
       [:div.css-901oao.r-1re7ezh.r-1q142lx.r-1qd0xha.r-a023e6.r-16dba41.r-ad9z0x.r-bcqeeo.r-ou255f.r-qvutc0
        {:aria-hidden "true", :dir "auto"}
        [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
         "·"]]
       [:a.css-4rbku5.css-18t94o4.css-901oao.r-1re7ezh.r-1loqt21.r-1q142lx.r-1qd0xha.r-a023e6.r-16dba41.r-ad9z0x.r-bcqeeo.r-3s2u2q.r-qvutc0
        {:data-focusable "true",
         :role "link",
         :aria-label "18 minutes ago",
         :dir "auto",
         :href "/Rainmaker1973/status/1171514719129092096",
         :title "11:04 PM · Sep 10, 2019"}
        [:time {:datetime "2019-09-10T20:04:09.000Z"} "18m"]]]
      [:div.css-1dbjc4n.r-18u37iz.r-1h0z5md.r-1joea0r
       [:div.css-18t94o4.css-1dbjc4n.r-1777fci.r-11cpok1.r-1ny4l3l.r-bztko3.r-lrvibr
        {:data-testid "caret",
         :tabindex "0",
         :data-focusable "true",
         :role "button",
         :aria-label "More",
         :aria-haspopup "true"}
        [:div.css-901oao.r-1awozwy.r-1re7ezh.r-6koalj.r-1qd0xha.r-a023e6.r-16dba41.r-1h0z5md.r-ad9z0x.r-bcqeeo.r-o7ynqc.r-clp7b1.r-3s2u2q.r-qvutc0
         {:dir "ltr"}
         [:div.css-1dbjc4n.r-xoduu5
          [:div.css-1dbjc4n.r-sdzlij.r-1p0dtai.r-xoduu5.r-1d2f490.r-podbf7.r-u8s1d.r-zchlnj.r-ipm5af.r-o7ynqc.r-6416eg]
          [:svg.r-4qtqp9.r-yyyyoo.r-ip8ujx.r-dnmrzs.r-bnwqim.r-1plcrui.r-lrvibr.r-27tl0q
           {:viewbox "0 0 24 24"}
           [:g
            [:path
             {:d
              "M20.207 8.147c-.39-.39-1.023-.39-1.414 0L12 14.94 5.207 8.147c-.39-.39-1.023-.39-1.414 0-.39.39-.39 1.023 0 1.414l7.5 7.5c.195.196.45.294.707.294s.512-.098.707-.293l7.5-7.5c.39-.39.39-1.022 0-1.413z"}]]]]]]]]
     [:div.css-901oao.r-hkyrab.r-1qd0xha.r-a023e6.r-16dba41.r-ad9z0x.r-bcqeeo.r-bnwqim.r-qvutc0
      {:dir "auto", :lang "en"}
      [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
       "Created by EUPHRATES and Masahiko Sato for Japan’s National Institute for Material Science, this video shows a ring with a strong magnet in the center and a light spongy material surrounding it, visualizing the electromagnetic induction and the Lenz's law "]
      [:a.css-4rbku5.css-18t94o4.css-901oao.css-16my406.r-1n1174f.r-1loqt21.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
       {:rel " noopener noreferrer",
        :data-focusable "true",
        :role "link",
        :dir "ltr",
        :target "_blank",
        :href "https://t.co/Y3SluPFt6U?amp=1",
        :title "https://buff.ly/2PY3DcT"}
       [:span.css-901oao.css-16my406.r-1qd0xha.r-hiw28u.r-ad9z0x.r-bcqeeo.r-qvutc0
        {:aria-hidden "true"}
        "https://"]
       "buff.ly/2PY3DcT"]]
     [:div.css-1dbjc4n.r-156q2ks
      [:div.css-1dbjc4n.r-1udh08x
       [:div.css-1dbjc4n.r-9x6qib.r-t23y2h.r-1phboty.r-rs99b7.r-1udh08x
        [:div.css-1dbjc4n
         [:div.css-1dbjc4n.r-1adg3ll.r-1udh08x
          [:div.r-1adg3ll.r-13qz1uu
           {:style {:padding-bottom "55.9375%"}}]
          [:div.r-1p0dtai.r-1pi2tsx.r-1d2f490.r-u8s1d.r-ipm5af.r-13qz1uu
           [:div.css-1dbjc4n.r-1awozwy.r-1p0dtai.r-1777fci.r-1d2f490.r-u8s1d.r-zchlnj.r-ipm5af
            {:data-testid "previewInterstitial",
             :aria-label "Play this video"}
            [:div.css-1dbjc4n.r-1ebgqk7.r-18u37iz.r-1t68eob.r-u8s1d.r-184en5c
             [:div.css-1dbjc4n.r-7o8qx1
              [:div.css-1dbjc4n.r-1awozwy.r-1810x6o.r-re1h2s.r-qpmf2f.r-1as3g83.r-dfv94e.r-7q8q6z.r-z80fyv.r-1777fci.r-ou255f
               [:div.css-901oao.r-jwli3a.r-1qd0xha.r-n6v787.r-16dba41.r-1sf4r6n.r-bcqeeo.r-q4m81j.r-qvutc0
                {:dir "auto"}
                [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
                 "0:10"]]]]
             [:div.css-1dbjc4n.r-7o8qx1
              [:div.css-1dbjc4n.r-1awozwy.r-1810x6o.r-re1h2s.r-qpmf2f.r-1as3g83.r-dfv94e.r-7q8q6z.r-z80fyv.r-1777fci.r-ou255f
               [:div.css-901oao.r-jwli3a.r-1qd0xha.r-n6v787.r-16dba41.r-1sf4r6n.r-bcqeeo.r-q4m81j.r-qvutc0
                {:dir "auto"}
                "1.7K views"]]]
             [:div.css-1dbjc4n.r-7o8qx1
              [:div.css-1dbjc4n.r-1awozwy.r-1810x6o.r-re1h2s.r-qpmf2f.r-1as3g83.r-dfv94e.r-7q8q6z.r-z80fyv.r-1777fci.r-ou255f
               [:div.css-901oao.r-jwli3a.r-1qd0xha.r-n6v787.r-16dba41.r-1sf4r6n.r-bcqeeo.r-q4m81j.r-qvutc0
                {:dir "auto"}
                [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
                 "150 KB"]]]]]
            [:div.css-1dbjc4n.r-1p0dtai.r-1d2f490.r-1udh08x.r-u8s1d.r-zchlnj.r-ipm5af
             [:div.css-1dbjc4n.r-1p0dtai.r-xigjrr.r-1d2f490.r-1udh08x.r-u8s1d.r-zchlnj.r-ipm5af.r-1c5lwsr
              [:div.css-1dbjc4n.r-1p0dtai.r-1mlwlqe.r-1d2f490.r-11wrixw.r-61z16t.r-1udh08x.r-u8s1d.r-zchlnj.r-ipm5af.r-417010
               {:style {:margin-bottom 0
                        :margin-top 0}
                :aria-label "Embedded video"}
               [:div.css-1dbjc4n.r-1niwhzg.r-vvn4in.r-u6sd8q.r-4gszlv.r-1p0dtai.r-1pi2tsx.r-1d2f490.r-u8s1d.r-zchlnj.r-ipm5af.r-13qz1uu.r-1wyyakw]
               [:img.css-9pa8cd
                {:src
                 "https://pbs.twimg.com/ext_tw_video_thumb/1171514697159282688/pu/img/ur3QePPGjeOOqrZ0?format=webp&name=tiny",
                 :draggable "true",
                 :alt "Embedded video"}]]]]]]]
         [:div.css-1dbjc4n.r-1awozwy.r-1p0dtai.r-1777fci.r-1d2f490.r-u8s1d.r-zchlnj.r-ipm5af
          [:div.css-18t94o4.css-1dbjc4n.r-loe9s5.r-42olwf.r-sdzlij.r-1phboty.r-rs99b7.r-1w2pmg.r-1vsu8ta.r-aj3cln.r-1fneopy.r-o7ynqc.r-6416eg.r-lrvibr
           {:tabindex "0", :data-focusable "true", :role "button"}
           [:div.css-901oao.r-1awozwy.r-jwli3a.r-6koalj.r-18u37iz.r-16y2uox.r-1qd0xha.r-a023e6.r-vw2c0b.r-1777fci.r-eljoum.r-dnmrzs.r-bcqeeo.r-q4m81j.r-qvutc0
            {:dir "auto"}
            [:span.css-901oao.css-16my406.css-bfa6kz.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
             [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
              "Load video"]]]]]]]]
      [:div.css-1dbjc4n.r-1g94qm0]]
     [:div.css-1dbjc4n.r-18u37iz.r-1wtj0ep.r-156q2ks.r-1mdbhws
      {:role "group", :aria-label "5 Retweets, 19 likes"}
      [:div.css-1dbjc4n.r-1iusvr4.r-18u37iz.r-16y2uox.r-1h0z5md
       [:div.css-18t94o4.css-1dbjc4n.r-1777fci.r-11cpok1.r-1ny4l3l.r-bztko3.r-lrvibr
        {:data-testid "reply",
         :tabindex "0",
         :data-focusable "true",
         :role "button",
         :aria-label "Reply"}
        [:div.css-901oao.r-1awozwy.r-1re7ezh.r-6koalj.r-1qd0xha.r-a023e6.r-16dba41.r-1h0z5md.r-ad9z0x.r-bcqeeo.r-o7ynqc.r-clp7b1.r-3s2u2q.r-qvutc0
         {:dir "ltr"}
         [:div.css-1dbjc4n.r-xoduu5
          [:div.css-1dbjc4n.r-sdzlij.r-1p0dtai.r-xoduu5.r-1d2f490.r-xf4iuw.r-u8s1d.r-zchlnj.r-ipm5af.r-o7ynqc.r-6416eg]
          [:svg.r-4qtqp9.r-yyyyoo.r-1xvli5t.r-dnmrzs.r-bnwqim.r-1plcrui.r-lrvibr.r-1hdv0qi
           {:viewbox "0 0 24 24"}
           [:g
            [:path
             {:d
              "M14.046 2.242l-4.148-.01h-.002c-4.374 0-7.8 3.427-7.8 7.802 0 4.098 3.186 7.206 7.465 7.37v3.828c0 .108.044.286.12.403.142.225.384.347.632.347.138 0 .277-.038.402-.118.264-.168 6.473-4.14 8.088-5.506 1.902-1.61 3.04-3.97 3.043-6.312v-.017c-.006-4.367-3.43-7.787-7.8-7.788zm3.787 12.972c-1.134.96-4.862 3.405-6.772 4.643V16.67c0-.414-.335-.75-.75-.75h-.396c-3.66 0-6.318-2.476-6.318-5.886 0-3.534 2.768-6.302 6.3-6.302l4.147.01h.002c3.532 0 6.3 2.766 6.302 6.296-.003 1.91-.942 3.844-2.514 5.176z"}]]]]]]]
      [:div.css-1dbjc4n.r-1iusvr4.r-18u37iz.r-16y2uox.r-1h0z5md
       [:div.css-18t94o4.css-1dbjc4n.r-1777fci.r-11cpok1.r-1ny4l3l.r-bztko3.r-lrvibr
        {:data-testid "retweet",
         :tabindex "0",
         :data-focusable "true",
         :role "button",
         :aria-label "5 Retweets. Retweet",
         :aria-haspopup "true"}
        [:div.css-901oao.r-1awozwy.r-1re7ezh.r-6koalj.r-1qd0xha.r-a023e6.r-16dba41.r-1h0z5md.r-ad9z0x.r-bcqeeo.r-o7ynqc.r-clp7b1.r-3s2u2q.r-qvutc0
         {:dir "ltr"}
         [:div.css-1dbjc4n.r-xoduu5
          [:div.css-1dbjc4n.r-sdzlij.r-1p0dtai.r-xoduu5.r-1d2f490.r-xf4iuw.r-u8s1d.r-zchlnj.r-ipm5af.r-o7ynqc.r-6416eg]
          [:svg.r-4qtqp9.r-yyyyoo.r-1xvli5t.r-dnmrzs.r-bnwqim.r-1plcrui.r-lrvibr.r-1hdv0qi
           {:viewbox "0 0 24 24"}
           [:g
            [:path
             {:d
              "M23.77 15.67c-.292-.293-.767-.293-1.06 0l-2.22 2.22V7.65c0-2.068-1.683-3.75-3.75-3.75h-5.85c-.414 0-.75.336-.75.75s.336.75.75.75h5.85c1.24 0 2.25 1.01 2.25 2.25v10.24l-2.22-2.22c-.293-.293-.768-.293-1.06 0s-.294.768 0 1.06l3.5 3.5c.145.147.337.22.53.22s.383-.072.53-.22l3.5-3.5c.294-.292.294-.767 0-1.06zm-10.66 3.28H7.26c-1.24 0-2.25-1.01-2.25-2.25V6.46l2.22 2.22c.148.147.34.22.532.22s.384-.073.53-.22c.293-.293.293-.768 0-1.06l-3.5-3.5c-.293-.294-.768-.294-1.06 0l-3.5 3.5c-.294.292-.294.767 0 1.06s.767.293 1.06 0l2.22-2.22V16.7c0 2.068 1.683 3.75 3.75 3.75h5.85c.414 0 .75-.336.75-.75s-.337-.75-.75-.75z"}]]]]
         [:div.css-1dbjc4n.r-xoduu5.r-1udh08x
          [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-1n0xq6e.r-bcqeeo.r-d3hbe1.r-1wgg2b2.r-axxi2z.r-qvutc0
           [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
            "5"]]]]]]
      [:div.css-1dbjc4n.r-1iusvr4.r-18u37iz.r-16y2uox.r-1h0z5md
       [:div.css-18t94o4.css-1dbjc4n.r-1777fci.r-11cpok1.r-1ny4l3l.r-bztko3.r-lrvibr
        {:data-testid "like",
         :tabindex "0",
         :data-focusable "true",
         :role "button",
         :aria-label "19 Likes. Like"}
        [:div.css-901oao.r-1awozwy.r-1re7ezh.r-6koalj.r-1qd0xha.r-a023e6.r-16dba41.r-1h0z5md.r-ad9z0x.r-bcqeeo.r-o7ynqc.r-clp7b1.r-3s2u2q.r-qvutc0
         {:dir "ltr"}
         [:div.css-1dbjc4n.r-xoduu5
          [:div.css-1dbjc4n.r-sdzlij.r-1p0dtai.r-xoduu5.r-1d2f490.r-xf4iuw.r-u8s1d.r-zchlnj.r-ipm5af.r-o7ynqc.r-6416eg]
          [:svg.r-4qtqp9.r-yyyyoo.r-1xvli5t.r-dnmrzs.r-bnwqim.r-1plcrui.r-lrvibr.r-1hdv0qi
           {:viewbox "0 0 24 24"}
           [:g
            [:path
             {:d
              "M12 21.638h-.014C9.403 21.59 1.95 14.856 1.95 8.478c0-3.064 2.525-5.754 5.403-5.754 2.29 0 3.83 1.58 4.646 2.73.814-1.148 2.354-2.73 4.645-2.73 2.88 0 5.404 2.69 5.404 5.755 0 6.376-7.454 13.11-10.037 13.157H12zM7.354 4.225c-2.08 0-3.903 1.988-3.903 4.255 0 5.74 7.034 11.596 8.55 11.658 1.518-.062 8.55-5.917 8.55-11.658 0-2.267-1.823-4.255-3.903-4.255-2.528 0-3.94 2.936-3.952 2.965-.23.562-1.156.562-1.387 0-.014-.03-1.425-2.965-3.954-2.965z"}]]]]
         [:div.css-1dbjc4n.r-xoduu5.r-1udh08x
          [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-1n0xq6e.r-bcqeeo.r-d3hbe1.r-1wgg2b2.r-axxi2z.r-qvutc0
           [:span.css-901oao.css-16my406.r-1qd0xha.r-ad9z0x.r-bcqeeo.r-qvutc0
            "19"]]]]]]
      [:div.css-1dbjc4n.r-1iusvr4.r-18u37iz.r-16y2uox.r-1h0z5md
       [:div.css-18t94o4.css-1dbjc4n.r-1777fci.r-11cpok1.r-1ny4l3l.r-bztko3.r-lrvibr
        {:tabindex "0",
         :data-focusable "true",
         :role "button",
         :aria-label "Share Tweet",
         :aria-haspopup "true"}
        [:div.css-901oao.r-1awozwy.r-1re7ezh.r-6koalj.r-1qd0xha.r-a023e6.r-16dba41.r-1h0z5md.r-ad9z0x.r-bcqeeo.r-o7ynqc.r-clp7b1.r-3s2u2q.r-qvutc0
         {:dir "ltr"}
         [:div.css-1dbjc4n.r-xoduu5
          [:div.css-1dbjc4n.r-sdzlij.r-1p0dtai.r-xoduu5.r-1d2f490.r-xf4iuw.r-u8s1d.r-zchlnj.r-ipm5af.r-o7ynqc.r-6416eg]
          [:svg.r-4qtqp9.r-yyyyoo.r-1xvli5t.r-dnmrzs.r-bnwqim.r-1plcrui.r-lrvibr.r-1hdv0qi
           {:viewbox "0 0 24 24"}
           [:g
            [:path
             {:d
              "M17.53 7.47l-5-5c-.293-.293-.768-.293-1.06 0l-5 5c-.294.293-.294.768 0 1.06s.767.294 1.06 0l3.72-3.72V15c0 .414.336.75.75.75s.75-.336.75-.75V4.81l3.72 3.72c.146.147.338.22.53.22s.384-.072.53-.22c.293-.293.293-.767 0-1.06z"}]
            [:path
             {:d
              "M19.708 21.944H4.292C3.028 21.944 2 20.916 2 19.652V14c0-.414.336-.75.75-.75s.75.336.75.75v5.652c0 .437.355.792.792.792h15.416c.437 0 .792-.355.792-.792V14c0-.414.336-.75.75-.75s.75.336.75.75v5.652c0 1.264-1.028 2.292-2.292 2.292z"}]]]]]]]
      [:div.css-1dbjc4n.r-1mlwlqe.r-18kxxzh.r-199wky7]]]]])

(simple-benchmark []
  (rds/renderToString (r/as-element hiccup)) 10000)
