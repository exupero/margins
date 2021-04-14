(ns margins.db
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [datahike.api :as d]
            [margins.slug :refer [slugify]]
            [margins.title-generator :refer [new-title]])
  (:import [java.util UUID]))

(def default-config {:store {:backend :file, :path "./db"}
                     :schema-flexibility :read})

(defonce conn (d/connect default-config))

(defn init-db! [config]
  (d/create-database config)
  (doto (d/connect config)
    (d/transact [{:db/ident :item/id
                  :db/valueType :db.type/uuid
                  :db/cardinality :db.cardinality/one
                  :db/unique :db.unique/identity}
                 {:db/ident :notebook/title
                  :db/valueType :db.type/string
                  :db/cardinality :db.cardinality/one
                  :db/unique :db.unique/value}
                 {:db/ident :cell/notebook
                  :db/valueType :db.type/ref
                  :db/cardinality :db.cardinality/one}])))

(defn query [query args]
  (d/q {:query query
        :args (cons @conn args)}))

(defn transact! [items]
  (d/transact conn items))

(defn create-notebook! []
  (let [title (new-title)
        new-notebook {:db/id -1
                      :item/type :type/notebook
                      :item/id (UUID/randomUUID)
                      :notebook/title title
                      :notebook/slug (slugify title)
                      :notebook/created-at (tc/to-date (t/now))
                      :notebook/updated-at (tc/to-date (t/now))}
        new-cell {:item/type :type/cell
                  :item/id (UUID/randomUUID)
                  :cell/notebook -1
                  :cell/order 0
                  :cell/show-code? true}
        {:keys [tempids] :as tx} (d/transact conn [new-notebook new-cell])]
    (-> tx
      (assoc :new-notebook (update new-notebook :db/id tempids)
             :new-cell (update new-cell :cell/notebook tempids)))))

(defn update-notebook! [id title]
  (let [slug (slugify title)]
    (-> (d/transact conn [[:db/add [:item/id id] :notebook/title title]
                          [:db/add [:item/id id] :notebook/slug slug]
                          [:db/add [:item/id id] :notebook/updated-at (tc/to-date (t/now))]])
      (assoc :updated-notebook {:notebook/title title :notebook/slug slug}))))

(defn cell-ids-after [notebook-id order]
  (d/q '[:find ?id ?o
         :in $ ?notebook-id ?order
         :where
         [?n :item/id ?notebook-id]
         [?n :item/type :type/notebook]
         [?e :item/id ?id]
         [?e :cell/notebook ?n]
         [?e :cell/order ?o]
         [(<= ?order ?o)]]
       @conn notebook-id order))

(defn insert-cell! [notebook-id order]
  (let [new-cell {:item/type :type/cell
                           :item/id (UUID/randomUUID)
                           :cell/notebook [:item/id notebook-id]
                           :cell/order order
                           :cell/show-code? true}]
    (-> (d/transact conn
                    (into [[:db/add [:item/id notebook-id] :notebook/updated-at (tc/to-date (t/now))] new-cell]
                          (for [[id order] (cell-ids-after notebook-id order)]
                            [:db/add [:item/id id] :cell/order (inc order)])))
      (assoc :new-cell new-cell))))

(defn update-cell! [cell-id {nm :cell/name :keys [cell/code cell/show-code? cell/dependencies]}]
  (let [notebook-id (d/q '[:find ?n .
                           :in $ ?cell-id
                           :where
                           [?e :item/id ?cell-id]
                           [?e :cell/notebook ?n]]
                         @conn)
        transacts (cond-> [[:db/add notebook-id :notebook/updated-at (tc/to-date (t/now))]]
                          code (conj [:db/add [:item/id cell-id] :cell/code code])
                          (not (nil? show-code?)) (conj [:db/add [:item/id cell-id] :cell/show-code? show-code?])
                          nm (conj [:db/add [:item/id cell-id] :cell/name nm])
                          dependencies (conj [:db/add [:item/id cell-id] :cell/dependencies dependencies]))]
    (d/transact conn transacts)))

(comment
  (init-db! default-config)
  (d/delete-database default-config))
