(ns margins.server
  (:require [ring.adapter.jetty :refer [run-jetty]]))

(defn handler [req]
  {:status 200 :body "Hello"})

(defonce server (atom nil))

(defn run [& [port]]
  (let [port (Integer/parseInt (or port "8282"))]
    (reset! server (run-jetty handler {:port port :join? false}))))

(defn stop-server []
  (.stop @server)
  (reset! server nil))
