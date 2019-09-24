(ns figmacs.tours
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [reagent.core :as r]
            [cljs-http.client :as http]))

(defn post-sample-tours []
  "Load sample data from discover.swiss and post to ActivityPub public collection"
  (go
    (let
     [response (<! (http/get "ds-tours.json" {:with-credentials? false}))
      tours (get-in response [:body :data])
      as-activity (fn [object] {:type "Create"
                                :to ["https://www.w3.org/ns/activitystreams#Public"]
                                :object object})]
      (doseq [tour tours]
        (http/post "http://localhost:8080/actors/alice/outbox"
                   {:with-credentials? false :json-params (as-activity tour)})))))

(defn tour? [object]
  "Is the object a tour"
  (= "discover.swiss/Tour" (:type object)))

(defn tour-id [tour]
  (get tour (keyword "@id")))

(defn tour-image [tour]
  (when (get-in tour [:image :contentUrl])
    [:img.tour-image {:src (get-in tour [:image :contentUrl])}]))

(defn tour-status [activities tour]
  "Compute current status of tour from a list of activities (that may include Status targeting the tour)"
  (let
   [status-updates (filter (fn [activity] (and (= (:type activity) "Create")
                                               (= (get-in activity [:object :type]) "Status")
                                               (= (get-in activity [:object :target]) (tour-id tour))))
                           ;; sort by reverse published date
                           (reverse (sort-by :published activities)))]
    (first status-updates)))

(defn status-component [activity]
  (if activity
    [:span {:class ["tour-status" (get-in activity [:object :status])]}
     (str (get-in activity [:object :status])
          " (updated " (.fromNow (js/moment (:published activity)))
          " by " (:actor activity)
          ")")]
    "-"))

(defn status-update-component [tour submit-status]
  (let
   [status-update-content (r/atom {:status "open"
                                   :type "Status"
                                   :target (tour-id tour)})

    create-activity (fn [object] {:type "Create"
                                  :to "https://www.w3.org/ns/activitystreams#Public"
                                  :object object})

    submit #(do (submit-status (create-activity @status-update-content)))]

    (fn [submit-status]
      [:div.status-update
       [:h3 "Status update"]
       [:select
        {:selected (:status @status-update-content)
         :on-change (fn [event] (swap!
                                 status-update-content
                                 #(assoc % :status (-> event .-target .-value))))}
        [:option {:value "open"} "open"]
        [:option {:value "warning"} "warning"]
        [:option {:value "closed"} "closed"]]
       [:input {:type "button"
                :value "Update"
                :on-click submit}]])))

(defn tour-component [tour submit-status tour-status]
  (when (tour? tour)
    [:div.tour
     [:h2 "Tour"]

     [tour-image tour]

     [:dl

      [:dt "ID"]
      [:dd (tour-id tour)]

      [:dt "Description"]
      [:dd (or (get tour :description) "-")]

      (when tour-status
        [:div
         [:dt "Status"]
         [:dd
          [status-component (tour-status tour)]]])]

     (when submit-status
       [status-update-component tour submit-status])]))

(defn tours-component [tours submit-status tour-status]
  [:div
   (for [tour (filter tour? tours)]
     [:div
      [:div.object
       [tour-component tour submit-status tour-status]]
      [:hr]])])
