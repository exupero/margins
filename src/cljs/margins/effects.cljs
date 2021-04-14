(ns margins.effects
  (:require [clojure.string :as string]
            [re-frame.core :as rf]
            [ajax.core :as ajax]
            [margins.cljs :as cljs]
            [margins.db :as db]))

(rf/reg-fx ::eval
  (fn [cells]
    (cljs/eval-forms (for [{:keys [id] :as cell} cells]
                       (assoc cell :callback #(rf/dispatch [:margins.events/evaled id %]))))))

(rf/reg-fx ::clear-globals
  (fn [_]
    (reset! db/globals {})))

(rf/reg-fx ::set-global
  (fn [[k v]]
    (swap! db/globals assoc k v)))

(defn request [m]
  (ajax/ajax-request
    (merge {:format (ajax/transit-request-format)
            :response-format (ajax/transit-response-format)}
           m)))

(rf/reg-fx ::query
  (fn [queries]
    (doseq [{:keys [query args handler]} queries]
      (request {:uri "/api"
                :method :post
                :params {:query query :args args}
                :handler (fn [[ok result]]
                           (if ok
                             (when handler (handler result))
                             (rf/dispatch [:margins.events/error result])))}))))

(rf/reg-fx ::mutate
  (fn [mutations]
    (doseq [{:keys [mutation handler]} mutations]
      (request {:uri "/api"
                :method :post
                :params {:mutation mutation}
                :handler (fn [[ok result]]
                           (if ok
                             (do
                               (rf/dispatch [:margins.events/transact (result :tx-data)])
                               (when handler (handler result)))
                             (rf/dispatch [:margins.events/error result])))}))))

(rf/reg-fx ::push-state
  (fn [url]
    (js/window.history.pushState nil "Margins" url)))

(rf/reg-fx ::set-title
  (fn [title]
    (when title
      (set! js/document.title (str title " / Margins")))))
