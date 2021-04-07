(ns margins.core
  (:require reagent.dom
            [re-frame.core :as rf]
            margins.events
            margins.views))

(defn render []
  (reagent.dom/render [margins.views/main] (js/document.getElementById "app")))

(defn ^:dev/after-load clear-cache-and-render! []
  (rf/clear-subscription-cache!)
  (render))

(defn run []
  (rf/dispatch-sync [:initialize])
  (render))
