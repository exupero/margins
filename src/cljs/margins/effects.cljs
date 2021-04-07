(ns margins.effects
  (:require [clojure.string :as string]
            [re-frame.core :as rf]
            [margins.cljs :as cljs]
            [margins.reader :as reader]))

(def readers {'md #(list* 'margins.markdown/markdown %)})

(rf/reg-fx ::eval
  (fn [{:keys [db id code]}]
    (cljs/eval-code (reader/read-string {:readers readers} code)
                    (fn [value]
                      (rf/dispatch [:margins.events/evaled id value])))))
