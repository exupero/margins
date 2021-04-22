(ns margins.events
  (:require [clojure.string :as string]
            [re-frame.core :as rf]
            [re-posh.core :as rp]
            [margins.cljs :as cljs]
            [margins.db :as db]
            [margins.effects :as effects]
            [margins.parse :refer [parse-name-and-form]]
            [margins.slug :refer [slugify]]
            [margins.topo-sort :as topo]))

(defn current-slug []
  (let [slug (string/replace js/window.location.pathname #"^/" "")]
    (when-not (string/blank? slug)
      slug)))

(rf/reg-event-fx ::initialize
  (fn [_ _]
    (if-let [slug (current-slug)]
      {:dispatch [::go-to-notebook slug]}
      {:db (doto (db/empty-db) rp/connect!)
       ::effects/set-global [:route :index]
       ::effects/query [{:query '[:find [(pull ?e [*]) ...]
                                  :where [?e :item/type :type/notebook]]
                         :handler #(rf/dispatch [::set-notebooks %])}]})))

(rp/reg-event-ds ::set-notebooks
  (fn [_ [_ notebooks]]
    notebooks))

(rf/reg-event-fx ::create-notebook
  (fn [_ _]
    {::effects/mutate [{:mutation ['create-notebook]
                        :handler (fn [{{:keys [notebook/slug notebook/title]} :new-notebook}]
                                   (rf/dispatch [::push-state (str "/" slug)])
                                   (when title (rf/dispatch [::set-title title]))
                                   (rf/dispatch [::go-to-notebook slug]))}]}))

(rf/reg-event-fx ::push-state
  (fn [_ [_ url]]
    {::effects/push-state url}))

(rf/reg-event-fx ::set-title
  (fn [_ [_ title]]
    {::effects/set-title title}))

(rf/reg-event-fx ::go-to-notebook
  (fn [_ [_ slug title]]
    {:db (doto (db/empty-db) rp/connect!)
     ::effects/set-global [:route :notebook]
     ::effects/query [{:query '[:find (pull ?e [*]) .
                                :in $ ?slug
                                :where [?e :notebook/slug ?slug]]
                       :args [slug]
                       :handler #(rf/dispatch [::set-notebook %])}
                      {:query '[:find [(pull ?e [*]) ...]
                                :in $ ?slug
                                :where
                                [?e :item/type :type/cell]
                                [?e :cell/notebook ?n]
                                [?n :notebook/slug ?slug]]
                       :args [slug]
                       :handler #(rf/dispatch [::set-cells %])}]}))

(rf/reg-event-fx ::set-notebook
  (fn [{:keys [db]} [_ {:keys [notebook/title] :as notebook}]]
    {:db (doto db (db/transact! [notebook]))
     ::effects/set-title title}))

(rf/reg-event-fx ::set-cells
  (fn [{:keys [db]} [_ cells]]
    {:db (doto db (db/transact! cells))
     ::effects/eval (for [{:keys [item/id cell/code]} (topo/topo-sort-cells cells)
                          :let [[nm form] (parse-name-and-form code)]]
                      {:id id, :name nm, :form form})}))

(rp/reg-event-ds ::edit
  (fn [_ [_ id code]]
    [[:db/add [:item/id id] :cell/code code]
     [:db/add [:item/id id] :cell/dirty? true]]))

(rp/reg-event-ds ::transact
  (fn [_ [_ datoms]]
    (for [[entity attr value tx added?] datoms]
      [(if added? :db/add :db/retract) entity attr value tx])))

(rf/reg-event-fx ::eval
  (fn [{:keys [db]} [_ id code]]
    (let [[nm form] (parse-name-and-form code)
          dependencies (cljs/possible-dependencies (db/cell-names @db) form)]
      {:db (doto db
             (db/transact! (cond-> [[:db/add [:item/id id] :cell/dependencies dependencies]
                                    [:db/add [:item/id id] :cell/dirty? false]]
                                   nm (conj [:db/add [:item/id id] :cell/name nm])
                                   (not nm) (conj [:db/retract [:item/id id] :cell/name]))))
       ::effects/mutate [{:mutation ['update-cell {:item/id id :cell/code code :cell/name nm :cell/dependencies dependencies}]}]
       ::effects/eval (cons {:id id, :name nm, :form form}
                            (for [{nm :cell/name :keys [item/id cell/code]} (db/dependent-cells @db nm)
                                  :let [[nm form] (parse-name-and-form code)]]
                              {:id id, :name nm, :form form}))})))

(rf/reg-event-fx ::eval-dependents
  (fn [{:keys [db]} [_ nm]]
    {::effects/eval (for [{nm :cell/name :keys [item/id cell/code]} (db/dependent-cells @db nm)
                          :let [[nm form] (parse-name-and-form code)]]
                      {:id id, :name nm, :form form})}))

(rp/reg-event-ds ::evaled
  (fn [_ [_ id value]]
    (when value
      [[:db/add [:item/id id] :cell/value value]])))

(rf/reg-event-fx ::set-code-visibility
  (fn [{:keys [db]} [_ id show-code?]]
    {:db (doto db (db/transact! [[:db/add [:item/id id] :cell/show-code? show-code?]]))
     ::effects/mutate [{:mutation ['update-cell {:item/id id :cell/show-code? show-code?}]}]}))

(rf/reg-event-fx ::insert-cell
  (fn [db [_ notebook-id order]]
    {::effects/mutate [{:mutation ['insert-cell {:cell/notebook-id notebook-id :cell/order order}]}]}))

(rf/reg-event-fx ::hover-position
  (fn [db [_ order]]
    {::effects/set-global [:hovered-position order]}))

(rf/reg-event-fx ::check-title
  (fn [{:keys [db]} _]
    (let [{current-title :notebook/title :keys [item/id]} (db/current-notebook @db (current-slug))
          new-title (some-> (js/document.querySelector "h1") .-innerHTML)]
      (when (and new-title (not= new-title current-title))
        {::effects/mutate [{:mutation ['update-notebook {:item/id id :notebook/title new-title}]
                            :handler (fn [{{:keys [notebook/slug notebook/title]} :updated-notebook}]
                                       (rf/dispatch [::push-state (str "/" slug)])
                                       (when title (rf/dispatch [::set-title title])))}]}))))
