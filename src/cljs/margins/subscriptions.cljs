(ns margins.subscriptions
  (:require [re-frame.core :as rf]
            [re-posh.core :as rp]
            [margins.db :as db]))

(rp/reg-query-sub ::cell-ids
  '[:find [?id ...]
    :where [?id :item/type :type/cell]])

(rp/reg-sub ::cells-unordered
  :<- [::cell-ids]
  (fn [ids _]
    {:type :pull-many
     :pattern '[*]
     :ids ids}))

(rf/reg-sub ::cells
  :<- [::cells-unordered]
  (fn [cells _]
    (sort-by :cell/order cells)))

(rp/reg-query-sub ::hovered-position
  '[:find ?o .
    :where [_ :globals/hovered-position ?o]])
