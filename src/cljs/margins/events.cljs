(ns margins.events
  (:require [re-frame.core :as rf]
            [re-posh.core :as rp]
            [margins.db :as db]
            [margins.effects :as effects]))

(rf/reg-event-db :initialize
  (fn [_ _]
    (doto (db/empty-db)
      rp/connect!
      (db/transact! [{:item/id :item/globals}
                     (db/new-cell)]))))

(rp/reg-event-ds ::edit
  (fn [_ [_ id code]]
    [[:db/add [:item/id id] :cell/code code]]))

(rf/reg-event-fx ::eval
  (fn [{:keys [db]} [_ id code]]
    {::effects/eval {:db db :id id :code code}}))

(rp/reg-event-ds ::evaled
  (fn [_ [_ id value]]
    (let [str-value (cond
                      (nil? value) "nil"
                      (fn? value) (.-name value)
                      :else value)]
      [[:db/add [:item/id id] :cell/value str-value]])))

(rp/reg-event-ds ::set-code-visibility
  (fn [_ [_ id show-code?]]
    [[:db/add [:item/id id] :cell/show-code? show-code?]]))

(rp/reg-event-ds ::insert-cell
  (fn [db [_ order]]
    (cons (assoc (db/new-cell) :cell/order order)
          (map #(do [:db/add [:item/id %1] :cell/order %2])
               (db/cell-ids-after db order)
               (iterate inc (inc order))))))

(rp/reg-event-ds ::hover-cell
  (fn [db [_ order]]
    [[:db/add [:item/id :item/globals] :globals/hovered-position order]]))
