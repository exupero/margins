(ns margins.vega
  (:require ["vega-embed" :as vega-embed]))

(defn vega [plot]
  (let [plot (assoc plot :$schema "https://vega.github.io/schema/vega/v5.json")]
    ^:dom [:div {:ref #(vega-embed % (clj->js plot) #js {:mode "vega" :renderer "canvas"})}]))
