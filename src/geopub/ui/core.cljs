(ns geopub.ui.core
  (:require
   [geopub.ui.map]))

(defn ui [state]
  [:div#container
   [:div#sidebar

    [:header
     [:h1 "GeoPub"]
     ]

    [:nav
     [:ul
      [:li [:a {:href "#something"} "Timeline"]]
      [:li [:a {:href "#something"} "Map"]]
      ]

     [:hr]

     [:ul
      [:li [:a {:href "#something"} "Settings"]]
      [:li [:a {:href "#something"} "Account"]]
      ]]

    [:div#debug
     [:code
      (str @state)]]

    ]

   [:main
    [:header]

    [geopub.ui.map/map-component state]
    ]]
  )
