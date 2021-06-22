(ns margins.db
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [hitchhiker.tree.key-compare :as kc]
            [datahike.api :as d]
            [margins.queries :as queries]
            [margins.parse :refer [parse-name-and-form]]
            [margins.slug :refer [slugify]]
            [margins.title-generator :refer [new-title]]
            [margins.topo-sort :as topo])
  (:import [java.util UUID]))

(def default-config {:store {:backend :file, :path "./db"}
                     :schema-flexibility :read})

(extend-protocol kc/IKeyCompare
  clojure.lang.PersistentList
  (-compare [a b]
    (compare (vec a) (vec b))))

(def conn (d/connect default-config))

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
                 {:db/ident :notebook/slug
                  :db/valueType :db.type/string
                  :db/cardinality :db.cardinality/one
                  :db/unique :db.unique/value}
                 {:db/ident :cell/notebook
                  :db/valueType :db.type/ref
                  :db/cardinality :db.cardinality/one}
                 {:db/ident :attachment/cell
                  :db/valueType :db.type/ref
                  :db/cardinality :db.cardinality/one}])))

(defn query [q & args]
  (d/q {:query q
        :args (cons @conn args)}))

(defn transact! [items]
  (d/transact conn items))

(defn create-notebook! []
  (let [title (str "Untitled (" (new-title) ")")
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
        {:keys [tempids] :as tx} (transact! [new-notebook new-cell])]
    (-> tx
      (assoc :new-notebook (update new-notebook :db/id tempids)
             :new-cell (update new-cell :cell/notebook tempids)))))

(defn notebook-update [id]
  [:db/add id :notebook/updated-at (tc/to-date (t/now))])

(defn update-notebook! [id title]
  (let [slug (slugify title)]
    (-> (transact! [(notebook-update [:item/id id])
                    [:db/add [:item/id id] :notebook/title title]
                    [:db/add [:item/id id] :notebook/slug slug]])
      (assoc :updated-notebook {:notebook/title title :notebook/slug slug}))))

(defn delete-notebook! [slug]
  (let [id (query '[:find ?e .
                    :in $ ?slug
                    :where [?e :notebook/slug ?slug]]
                  slug)]
    (transact! [[:db.fn/retractEntity id]])))

(defn insert-cell! [notebook-id order]
  (let [new-cell {:item/type :type/cell
                  :item/id (UUID/randomUUID)
                  :cell/notebook [:item/id notebook-id]
                  :cell/order order
                  :cell/show-code? true}
        bumped-cells (query '[:find ?id ?o
                              :in $ ?notebook-id ?order
                              :where
                              [?n :item/id ?notebook-id]
                              [?n :item/type :type/notebook]
                              [?e :item/id ?id]
                              [?e :cell/notebook ?n]
                              [?e :cell/order ?o]
                              [(<= ?order ?o)]]
                            notebook-id order)]
    (-> (transact! (into [(notebook-update [:item/id notebook-id])
                          new-cell]
                         (for [[id order] bumped-cells]
                           [:db/add [:item/id id] :cell/order (inc order)])))
      (assoc :new-cell new-cell))))

(defn notebook-id-for-cell [id]
  (query '[:find ?n .
           :in $ ?cell-id
           :where
           [?e :item/id ?cell-id]
           [?e :cell/notebook ?n]]
         id))

(defn move-cell! [id order]
  (let [cells (query '[:find [(pull ?e [:item/id :cell/order]) ...]
                       :in $ ?cell-id
                       :where
                       [?e1 :item/id ?cell-id]
                       [?e1 :cell/notebook ?n]
                       [?e :cell/notebook ?n]]
                     id)
        sorted (->> cells
                 (map (fn [{id' :item/id :as cell}]
                        (if (= id' id)
                          (assoc cell :cell/order (- order 0.5))
                          cell)))
                 (sort-by :cell/order))
        updates (map (fn [{:keys [item/id]} order]
                       [:db/add [:item/id id] :cell/order order])
                     sorted (range))]
    (transact! (into [(notebook-update (notebook-id-for-cell id))
                      [:db/add [:item/id id] :cell/order order]]
                     updates))))

(defn update-cell! [cell-id {nm :cell/name :keys [cell/code cell/show-code? cell/dependencies]}]
  (let [transacts (cond-> [(notebook-update (notebook-id-for-cell cell-id))]
                          code (conj [:db/add [:item/id cell-id] :cell/code code])
                          (not (nil? show-code?)) (conj [:db/add [:item/id cell-id] :cell/show-code? show-code?])
                          nm (conj [:db/add [:item/id cell-id] :cell/name nm])
                          dependencies (conj [:db/add [:item/id cell-id] :cell/dependencies dependencies]))]
    (transact! transacts)))

(defn delete-cell! [id]
  (let [bumped-cells (query '[:find (pull ?e [:item/id :cell/order :cell/notebook])
                              :in $ ?cell-id
                              :where
                              [?e1 :item/id ?cell-id]
                              [?e1 :cell/notebook ?n]
                              [?e1 :cell/order ?o1]
                              [?e :cell/notebook ?n]
                              [?e :item/id ?id]
                              [?e :cell/order ?o]
                              [(< ?o1 ?o)]]
                            id)]
    (transact! (into [(notebook-update (notebook-id-for-cell id))
                      [:db.fn/retractEntity [:item/id id]]]
                     (for [[{:keys [item/id cell/order]}] bumped-cells]
                       [:db/add [:item/id id] :cell/order (dec order)])))))

(defn attach! [attachment]
  (transact! [(notebook-update (notebook-id-for-cell (second (attachment :attachment/cell))))
              attachment]))

(defn transclude [notebook-slug nm remap-names]
  (let [deps (topo/topo-sort-cells
               (query '[:find [(pull ?e [:item/id :cell/name :cell/code :cell/dependencies]) ...]
                        :in $ ?slug ?name %
                        :where
                        [?n :notebook/slug ?slug]
                        [?e1 :cell/notebook ?n]
                        [?e1 :cell/name ?name]
                        (dependency ?e1 ?e)]
                      (name 'test-utils) nm queries/dependency))
        {final :cell/code} (query '[:find (pull ?e [:cell/code]) .
                                    :in $ ?slug ?name
                                    :where
                                    [?n :notebook/slug ?slug]
                                    [?e :cell/notebook ?n]
                                    [?e :cell/name ?name]]
                                  notebook-slug nm)
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

(defn load-from-dump [conn dumpfile]
  (let [source-datoms (mapv #(into [(if (last %) :db/add :db/retract)]
                                   (subvec (into [] %) 0 3))
                            (read-string (slurp dumpfile)))]
    (transact! source-datoms)))

(comment
  (init-db! default-config)
  (d/delete-database default-config))
