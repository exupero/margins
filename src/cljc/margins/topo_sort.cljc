(ns margins.topo-sort
  (:require [clojure.set :refer [difference union intersection]]))

; A modified version of https://gist.github.com/alandipert/1263783, which included the following notice:
;
;; Copyright (c) Alan Dipert. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(defn no-incoming
  "Returns the set of nodes in graph g for which there are no incoming
  edges, where g is a map of nodes to sets of nodes."
  [g]
  (let [nodes (set (keys g))
        have-incoming (apply union (vals g))]
    (difference nodes have-incoming)))

(defn normalize
  "Returns g with empty outgoing edges added for nodes with incoming
  edges only.  Example: {:a #{:b}} => {:a #{:b}, :b #{}}"
  [g]
  (let [have-incoming (apply union (vals g))]
    (reduce #(if (get % %2) % (assoc % %2 #{})) g have-incoming)))

(defn topo-sort
  "Proposes a topological sort for directed graph g using Kahn's
   algorithm, where g is a map of nodes to sets of nodes. If g is
   cyclic, returns nil."
  ([g]
   (topo-sort (normalize g) [] (no-incoming g)))
  ([g l s]
   (if (empty? s)
     (when (every? empty? (vals g)) l)
     (let [n (first s)
           s' (disj s n)
           m (g n)
           g' (reduce #(update-in % [n] disj %2) g m)]
       (recur g' (conj l n) (union s' (intersection (no-incoming g') m)))))))

(defn topo-sort-cells [deps]
  (let [name->id (into {}
                       (comp
                         (filter :cell/name)
                         (map (juxt :cell/name :item/id)))
                       deps)
        graph (into {}
                    (map (fn [{:keys [item/id cell/dependencies]}]
                           [id (into #{} (map name->id) dependencies)]))
                    deps)
        sorted (topo-sort graph)]
    (sort-by (comp #(.indexOf sorted %) :item/id) > deps)))
