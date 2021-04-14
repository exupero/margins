(ns margins.slug
  (:require [clojure.string :as string]))

(defn slugify [s]
  (some-> s
    string/trim
    string/lower-case
    (string/replace #"\s+|-+" "-")
    (string/replace #"[^a-z0-9-]+" "")))
