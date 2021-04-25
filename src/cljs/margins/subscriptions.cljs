(ns margins.subscriptions
  (:require [cljs-time.core :as t]
            [cljs-time.coerce :as tc]
            [re-frame.core :as rf]
            [re-posh.core :as rp]
            [margins.db :as db]))

(rf/reg-sub ::hovered-position
  (fn [_ _]
    (@db/globals :hovered-position)))

(rf/reg-sub ::route
  (fn [_ _]
    (@db/globals :route)))

(rf/reg-sub ::dragged-cell-id
  (fn [_ _]
    (@db/globals :dragged-cell-id)))

(rp/reg-query-sub ::notebook-ids
  '[:find [?e ...]
    :where [?e :item/type :type/notebook]])

(rp/reg-sub ::notebooks-unsorted
  :<- [::notebook-ids]
  (fn [ids _]
    {:type :pull-many
     :pattern '[*]
     :ids ids}))

(rf/reg-sub ::notebooks
  :<- [::notebooks-unsorted]
  (fn [notebooks _]
    (sort-by (comp tc/to-long #(or % (t/now)) :notebook/updated-at) > notebooks)))

(rp/reg-query-sub ::notebook-id
  '[:find ?id .
    :where
    [?e :item/type :type/notebook]
    [?e :item/id ?id]])

(rp/reg-query-sub ::cell-ids
  '[:find [?e ...]
    :where [?e :item/type :type/cell]])

(rp/reg-sub ::cells-unordered
  :<- [::cell-ids]
  (fn [ids _]
    {:type :pull-many
     :pattern '[* {:attachment/_cell [*]}]
     :ids ids}))

(rf/reg-sub ::cells
  :<- [::cells-unordered]
  (fn [cells _]
    (sort-by :cell/order cells)))
