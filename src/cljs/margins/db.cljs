(ns margins.db
  (:require [datascript.core :as d]
            [reagent.core :as r]
            [margins.topo-sort :as topo]))

(defonce globals (r/atom {}))

(def schema {:item/id {:db/unique :db.unique/identity}})

(defn empty-db []
  (d/create-conn schema))

(defn transact! [conn datoms]
  (d/transact! conn datoms))

(defn cell-names [db]
  (d/q '[:find [?nm ...]
         :where [_ :cell/name ?nm]]
       db))

(defn dependencies [db nm]
  (d/q '[:find [(pull ?e [:item/id :cell/name :cell/code :cell/dependencies]) ...]
         :in $ ?nm %
         :where (dependent ?e ?nm)]
       db nm
       '[[(dependent ?e ?nm)
          [?e :cell/dependencies ?deps]
          [(contains? ?deps ?nm)]]
         [(dependent ?e ?nm)
          [?e :cell/dependencies ?deps]
          [?e2 :cell/name ?nm2]
          [(contains? ?deps ?nm2)]
          (dependent ?e2 ?nm)]]))

(defn dependent-cells [db nm]
  (topo/topo-sort-cells (dependencies db nm)))

(defn current-notebook [db slug]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?slug
         :where [?e :notebook/slug ?slug]]
       db slug))
