(ns margins.renderable
  (:require datascript.db))

(defprotocol IRender
  (render [_]))

(extend-protocol IRender
  datascript.db.TxReport
  (render [this]
    [:table
     [:thead
      [:tr
       [:th]
       [:th "Entity ID"]
       [:th "Attribute"]
       [:th "Value"]]]
     [:tbody
      (for [[e a v _ added?] (:tx-data this)]
        [:tr {:key [e a v added?]}
         [:td (if added? "+" "-")]
         [:td e]
         [:td a]
         [:td v]])]]))
