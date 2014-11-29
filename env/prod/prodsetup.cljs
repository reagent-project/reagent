(ns envsetup
  (:require [mysite]))

(mysite/start!)

(when
  (exists? js/runtests)
  (js/runtests.main))
