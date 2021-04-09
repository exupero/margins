(ns margins.css
  (:require [clojure.java.io :as io]
            [garden.core :refer [css]]
            [garden.color :as c]
            [garden.units :as u]
            hawk.core))

(def parchment (c/hsl 40 34 95))
(def parchment-dark (c/hsl 40 34 70))

(def util
  [[:.pointer {:cursor :pointer}]
   [:.hidden {:visibility :hidden}]
   [:.trim
    [:*
     [:&:first-child {:margin-top 0}]
     [:&:last-child {:margin-bottom 0}]]]])

(def cell
  [[:.cell {:position :relative}]
   [:.cell__main {:border-left [[(u/px 1) :solid :transparent]]}]
   [:.cell--side-border {:border-left-color parchment-dark}]
   [:.cell__value {:padding [[(u/rem 0.25) (u/rem 0.5)]]}
    [:* [:&:first-child {:margin-top 0}
         :&:last-child {:margin-bottom 0}]]]
   [:.cell__actions {:position :absolute
                     :text-align :right
                     :height (u/percent 100)
                     :width (u/px 1000)
                     :left (u/px -1000)}
    [:&:hover {:background-color parchment}]]
   [:.actions--show {:background-color parchment}]])

(def insert-cell
  [[:.insert-cell {:color parchment-dark
                   :margin-left (u/px -3.5)
                   :line-height 0.8}]])

(def codemirror
  [[:.CodeMirror
    {:height :auto
     :font-family ["'PT Mono'"]
     :background-color parchment}
    [:pre.CodeMirror-line {:font-size (u/pt 10)}]
    [:.CodeMirror-matchingbracket {:border-bottom [[(u/px 1) :solid :black]]
                                   :padding-bottom (u/px 1)
                                   :outline :none}]]])

(def styles
  [[:body {:font-family ["'PT Sans'"]
           :font-size (u/pt 12)}
    [:main {:max-width (u/px 800)
            :margin [[0 :auto]]}]
    [:code {:font-family ["'PT Mono'"]
            :font-size (u/pt 10)}]]
   util
   cell
   insert-cell
   codemirror])

(defn watch [{:keys [out-path]}]
  (println "Watching CSS for output to" out-path)
  (hawk.core/watch! [{:paths ["src/clj/margins/css.clj"]
                      :handler (fn [_ _]
                                 (require 'margins.css :reload)
                                 (spit out-path (css styles))
                                 (println "Rebuilt" out-path))}]))
