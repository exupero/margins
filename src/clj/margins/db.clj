(ns margins.db
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [datahike.api :as d]
            [margins.queries :as queries]
            [margins.parse :refer [parse-name-and-form]]
            [margins.slug :refer [slugify]]
            [margins.title-generator :refer [new-title]]
            [margins.topo-sort :as topo])
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

(defn insert-cell! [notebook-id order]
  (let [new-cell {:item/type :type/cell
                  :item/id (UUID/randomUUID)
                  :cell/notebook [:item/id notebook-id]
                  :cell/order order
                  :cell/show-code? true}
        bumped-cells (d/q '[:find ?id ?o
                            :in $ ?notebook-id ?order
                            :where
                            [?n :item/id ?notebook-id]
                            [?n :item/type :type/notebook]
                            [?e :item/id ?id]
                            [?e :cell/notebook ?n]
                            [?e :cell/order ?o]
                            [(<= ?order ?o)]]
                          @conn notebook-id order)]
    (-> (d/transact conn
                    (into [[:db/add [:item/id notebook-id] :notebook/updated-at (tc/to-date (t/now))]
                           new-cell]
                          (for [[id order] bumped-cells]
                            [:db/add [:item/id id] :cell/order (inc order)])))
      (assoc :new-cell new-cell))))

(defn move-cell! [id order]
  (let [cells (d/q '[:find [(pull ?e [:item/id :cell/order :cell/notebook]) ...]
                     :in $ ?cell-id
                     :where
                     [?e1 :item/id ?cell-id]
                     [?e1 :cell/notebook ?n]
                     [?e :cell/notebook ?n]]
                   @conn id)
        sorted (->> cells
                 (map (fn [{id' :item/id :as cell}]
                        (if (= id' id)
                          (assoc cell :cell/order (- order 0.5))
                          cell)))
                 (sort-by :cell/order))
        _ (prn sorted)
        updates (map (fn [{:keys [item/id]} order]
                       [:db/add [:item/id id] :cell/order order])
                     sorted (range))
        notebook-id (-> cells first :cell/notebook :db/id)]
    (d/transact conn
                (into [[:db/add notebook-id :notebook/updated-at (tc/to-date (t/now))]
                       [:db/add [:item/id id] :cell/order order]]
                      updates))))

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

(defn delete-cell! [id]
  (let [notebook-id (d/q '[:find ?n .
                           :in $ ?cell-id
                           :where
                           [?e :item/id ?cell-id]
                           [?e :cell/notebook ?n]]
                         @conn id)
        bumped-cells (d/q '[:find (pull ?e [:item/id :cell/order :cell/notebook])
                            :in $ ?cell-id
                            :where
                            [?e1 :item/id ?cell-id]
                            [?e1 :cell/notebook ?n]
                            [?e1 :cell/order ?o1]
                            [?e :cell/notebook ?n]
                            [?e :item/id ?id]
                            [?e :cell/order ?o]
                            [(< ?o1 ?o)]]
                          @conn id)]
    (d/transact conn
                (into [[:db/add notebook-id :notebook/updated-at (tc/to-date (t/now))]
                       [:db.fn/retractEntity [:item/id id]]]
                      (for [[{:keys [item/id cell/order]}] bumped-cells]
                        [:db/add [:item/id id] :cell/order (dec order)])))))

(defn transclude [notebook-slug nm remap-names]
  (let [deps (topo/topo-sort-cells
               (d/q
                 '[:find [(pull ?e [:item/id :cell/name :cell/code :cell/dependencies]) ...]
                   :in $ ?slug ?name %
                   :where
                   [?n :notebook/slug ?slug]
                   [?e1 :cell/notebook ?n]
                   [?e1 :cell/name ?name]
                   (dependency ?e1 ?e)]
                 @conn (name 'test-utils) nm queries/dependency))
        {final :cell/code} (d/q '[:find (pull ?e [:cell/code]) .
                                  :in $ ?slug ?name
                                  :where
                                  [?n :notebook/slug ?slug]
                                  [?e :cell/notebook ?n]
                                  [?e :cell/name ?name]]
                                @conn notebook-slug nm)
        remapped (into {}
                       (map (fn [[k v]]
                              [k [v (gensym (name v))]]))
                       remap-names)
        lets (concat
               (mapcat (fn [[k [v g]]]
                         [g v])
                       remapped)
               (mapcat (fn [{nm :cell/name :keys [cell/code]}]
                         (let [[_ form] (parse-name-and-form code)
                               [_ g] (remapped nm)]
                           [nm (or g form)]))
                      deps))
        [_ final-form] (parse-name-and-form final)
        m (meta final-form)]
    `(~'let [~@lets] (~'with-meta ~final-form ~m))))

#_
(transclude 'test-utils 'square '{height x})

(comment
  (init-db! default-config)
  (d/delete-database default-config))
