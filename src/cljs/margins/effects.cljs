(ns margins.effects
  (:require [clojure.string :as string]
            [re-frame.core :as rf]
            [margins.cljs :as cljs]
            [margins.db :as db]))

(def _emit
  (aset js/window "emit" (fn [id nm value]
                           (when nm
                             (cljs/define nm value))
                           (rf/dispatch [:margins.events/evaled id value])
                           (rf/dispatch [:margins.events/eval-dependents nm])
                           value)))

(defn eval-cells [cells]
  (when-let [[{nm :name :keys [id form]} & cells] (seq cells)]
    (cljs/eval-code form id nm (fn [value]
                                 (rf/dispatch [:margins.events/evaled id value])
                                 (eval-cells cells)))))

(rf/reg-fx ::eval
  (fn [cells]
    (eval-cells cells)))

(rf/reg-fx ::set-global
  (fn [[k v]]
    (swap! db/globals assoc k v)))
