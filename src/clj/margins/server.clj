(ns margins.server
  (:require [clojure.java.io :as io]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn handler [{:keys [uri]}]
  (cond
    (= uri "/")
    {:status 200
     :body (slurp (io/resource "public/index.html"))
     :headers {"Content-type" "text/html"}}
    (re-find #"^/js" uri)
    {:status 200
     :body (slurp (io/resource (str "public" uri)))
     :headers {"Content-type" "text/javascript"}}
    (re-find #"^/css" uri)
    {:status 200
     :body (slurp (io/resource (str "public" uri)))
     :headers {"Content-type" "text/css"}}
    (re-find #"^/bootstrap" uri)
    {:status 200
     :body (slurp (io/resource (str "public/js" uri)))}))

(defonce server (atom nil))

(defn run [& [port]]
  (let [port (Integer/parseInt (or port "8282"))]
    (reset! server (run-jetty #'handler {:port port :join? false}))))

(defn stop-server []
  (.stop @server)
  (reset! server nil))

(comment
  (run)
  (stop))
