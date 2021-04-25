(ns margins.csv
  (:require [clojure.string :as string]))

(defn parse-csv [s]
  (let [[headers & rows] (string/split-lines s)
        headers (map string/trim (string/split headers #","))]
    (sequence
      (comp
        (map #(string/split % #","))
        (map (partial zipmap headers)))
      rows)))
