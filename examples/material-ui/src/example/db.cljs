(ns example.db)

(def default-db
  {
   :active-demo "splash"
   :user nil ; current logged in user
   :logs nil
   :loading? {:table  false}
   :example {"title" "Reagent & Material UI"
             "subtile" "featuring Re-Frame"
   }})
