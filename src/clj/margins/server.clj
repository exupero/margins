(ns margins.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            margins.handler))

(defonce server (atom nil))

(defn stop []
  (.stop @server)
  (reset! server nil))

(defn run [& [port]]
  (let [port (Integer/parseInt (or port "8282"))]
    (prn #'margins.handler/handler)
    (reset! server (run-jetty #'margins.handler/handler {:port port :join? false}))))

(comment
  (run)
  (stop))
