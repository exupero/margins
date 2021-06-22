(ns margins.handler
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.resource :refer [wrap-resource]]
            [margins.db :as db]))

(defn index-response []
  {:status 200
   :headers {"Content-type" "text/html"}
   :body (slurp (io/resource "public/index.html"))})

(defmulti act (fn [action _] action))

(defmethod act 'create-notebook [_ _]
  (db/create-notebook!))

(defmethod act 'update-notebook [_ {:keys [item/id notebook/title]}]
  (db/update-notebook! id title))

(defmethod act 'insert-cell [_ {:keys [cell/notebook-id cell/order]}]
  (db/insert-cell! notebook-id order))

(defmethod act 'move-cell [_ {:keys [item/id cell/order]}]
  (db/move-cell! id order))

(defmethod act 'update-cell [_ {:keys [item/id] :as cell}]
  (db/update-cell! id cell))

(defmethod act 'delete-cell [_ {:keys [item/id]}]
  (db/delete-cell! id))

(defmethod act 'attach [_ attachment]
  (db/attach! attachment))

(defmulti ask (fn [q _] q))

(defmethod ask 'include [_ form]
  (let [[_ nm & {:keys [from using]}] form]
    (db/transclude (name from) nm using)))

(defn api [{{g :get :keys [query args mutation]} :body-params}]
  (cond
    mutation
    (let [[action body] mutation]
      {:status 200
       :body (-> (act action body)
               (dissoc :db-before :db-after :tempids :tx-meta)
               (update :tx-data (partial map (partial into []))))})
    g
    (let [[q params] g]
      {:status 200
       :body (ask q params)})
    query
    {:status 200
     :body (apply db/query query args)}))

(defn core [{:keys [uri] :as req}]
  (cond
    (= uri "/") (index-response)
    (re-matches #"^/api$" uri) (api req)
    (re-matches #"^/[^/]+$" uri) (index-response)))

(def handler (-> core
               wrap-params
               (wrap-restful-format :formats [:transit-json])
               (wrap-resource "public")
               wrap-stacktrace
               wrap-reload))
