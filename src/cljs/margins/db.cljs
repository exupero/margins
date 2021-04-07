(ns margins.db
  (:require [datascript.core :as d]
            [reagent.core :as r]))

(def schema {:item/id {:db/unique :db.unique/identity}})

(defn empty-db []
  (d/create-conn schema))

(defn transact! [conn datoms]
  (d/transact! conn datoms))

(defn new-cell []
  {:item/type :type/cell
   :item/id (random-uuid)
   :cell/code ""
   :cell/show-code? true
   :cell/order 0})

(defn cell-ids-after [db order]
  (d/q '[:find [?id ...]
         :in $ ?order
         :where
         [?e :item/id ?id]
         [?e :cell/order ?o]
         [(<= ?order ?o)]]
       db order))
