(ns margins.events
  (:require [clojure.string :as string]
            [re-frame.core :as rf]
            [re-posh.core :as rp]
            [margins.db :as db]
            [margins.effects :as effects]
            [margins.cljs :as cljs]))

(rf/reg-event-db :initialize
  (fn [_ _]
    (doto (db/empty-db)
      rp/connect!
      (db/transact! [(db/new-cell)]))))

(rp/reg-event-ds ::edit
  (fn [_ [_ id code]]
    [[:db/add [:item/id id] :cell/code code]
     [:db/add [:item/id id] :cell/dirty? true]]))

(defn parse-code [code]
  (let [[nm-or-form form] (cljs/parse-string code)]
    (if (and form (symbol? nm-or-form))
      [nm-or-form form]
      [nil nm-or-form])))

(rf/reg-event-fx ::eval
  (fn [{:keys [db]} [_ id code]]
    (let [[nm form] (parse-code code)
          dependencies (cljs/possible-dependencies (db/cell-names @db) form)]
      {:db (doto db
             (db/transact! (cond-> [[:db/add [:item/id id] :cell/dependencies dependencies]
                                    [:db/add [:item/id id] :cell/dirty? false]]
                                   nm (conj [:db/add [:item/id id] :cell/name nm])
                                   (not nm) (conj [:db/retract [:item/id id] :cell/name]))))
       ::effects/eval (cons {:id id, :name nm, :form form}
                            (for [{nm :cell/name :keys [item/id cell/code]} (db/dependent-cells @db nm)
                                  :let [[nm form] (parse-code code)]]
                              {:id id, :name nm, :form form}))})))

(rf/reg-event-fx ::eval-dependents
  (fn [{:keys [db]} [_ nm]]
    {::effects/eval (for [{nm :cell/name :keys [item/id cell/code]} (db/dependent-cells @db nm)
                          :let [[nm form] (parse-code code)]]
                      {:id id, :name nm, :form form})}))

(rp/reg-event-ds ::evaled
  (fn [_ [_ id value]]
    [[:db/add [:item/id id] :cell/value value]]))

(rp/reg-event-ds ::set-code-visibility
  (fn [_ [_ id show-code?]]
    [[:db/add [:item/id id] :cell/show-code? show-code?]]))

(rp/reg-event-ds ::insert-cell
  (fn [db [_ order]]
    (cons (assoc (db/new-cell) :cell/order order)
          (map #(do [:db/add [:item/id %1] :cell/order %2])
               (db/cell-ids-after db order)
               (iterate inc (inc order))))))

(rf/reg-event-fx ::hover-position
  (fn [db [_ order]]
    {::effects/set-global [:hovered-position order]}))
