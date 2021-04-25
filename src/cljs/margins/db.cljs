(ns margins.db
  (:require [datascript.core :as d]
            [reagent.core :as r]
            [margins.topo-sort :as topo]
            [margins.queries :as queries]))

(defonce globals (r/atom {}))

(def schema {:item/id {:db/unique :db.unique/identity}
             :attachment/cell {:db/valueType :db.type/ref
                               :db/index true}})

(defn empty-db []
  (d/create-conn schema))

(defn reset-db! [conn]
  (d/reset-conn! conn (d/empty-db schema)))

(defn transact! [conn datoms]
  (d/transact! conn datoms))

(defn cell-names [db]
  (d/q '[:find [?nm ...]
         :where [_ :cell/name ?nm]]
       db))

(defn dependents [db nm]
  (d/q '[:find [(pull ?e [:item/id :cell/name :cell/code :cell/dependencies]) ...]
         :in $ ?nm %
         :where (dependent ?e ?nm)]
       db nm queries/dependent))

(defn dependent-cells [db nm]
  (topo/topo-sort-cells (dependents db nm)))

(defn current-notebook [db slug]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?slug
         :where [?e :notebook/slug ?slug]]
       db slug))

(defn attachment [nm]
  (d/q '[:find ?t .
         :in $ ?name
         :where
         [?e :attachment/name ?name]
         [?e :attachment/text ?t]]
       @(@globals :db) nm))
