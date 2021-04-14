(ns margins.handler
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.reload :refer [wrap-reload]]
            [margins.db :as db]))

(def index-response
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

(defmethod act 'update-cell [_ {:keys [item/id] :as cell}]
  (db/update-cell! id cell))

(defn api [{{:keys [query args mutation]} :body-params}]
  (cond
    mutation
    (let [[action body] mutation]
      {:status 200
       :body (-> (act action body)
               (dissoc :db-before :db-after :tempids :tx-meta)
               (update :tx-data (partial map (partial into []))))})
    query
    {:status 200
     :body (db/query query args)}))

(defn core [{:keys [uri] :as req}]
  (cond
    (= uri "/")
    index-response
    (re-find #"^/js" uri)
    {:status 200
     :headers {"Content-type" "text/javascript"}
     :body (slurp (io/resource (str "public" uri)))}
    (re-find #"^/css" uri)
    {:status 200
     :headers {"Content-type" "text/css"}
     :body (slurp (io/resource (str "public" uri)))}
    (re-find #"^/bootstrap" uri)
    {:status 200
     :headers {"Content-type" "text/javascript"}
     :body (slurp (io/resource (str "public/js" uri)))}
    (re-matches #"^/api$" uri)
    (api req)
    (re-matches #"^/[^/]+$" uri)
    index-response))

(def handler (-> core
               wrap-params
               (wrap-restful-format :formats [:transit-json])
               wrap-stacktrace
               wrap-reload))
