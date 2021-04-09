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
  (let [deps (dependencies db nm)
        name->id (into {}
                       (comp
                         (filter :cell/name)
                         (map (juxt :cell/name :item/id)))
                       deps)
        graph (into {}
                    (map (fn [{:keys [item/id cell/dependencies]}]
                           [id (into #{} (map name->id) dependencies)]))
                    deps)
        sorted (topo/sort graph)]
    (sort-by (comp #(.indexOf sorted %) :cell/id) > deps)))
