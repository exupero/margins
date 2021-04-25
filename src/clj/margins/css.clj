(ns margins.css
  (:require [clojure.java.io :as io]
            [garden.core :refer [css]]
            [garden.color :as c]
            [garden.units :as u]
            hawk.core))

(def parchment (c/hsl 40 34 95))
(def parchment-dark (c/hsl 40 34 70))
(def parchment-darker (c/hsl 40 34 50))

(def accent (c/hsl 220 34 60))

(def util
  [[:.pointer {:cursor :pointer}]
   [:.grabber {:cursor :grab}]
   [:.hidden {:visibility :hidden}]
   [:.hide {:display :none}]
   [:.hover-darken [:&:hover {:color parchment-darker}]]
   [:.trim
    [:*
     [:&:first-child {:margin-top 0}]
     [:&:last-child {:margin-bottom 0}]]]])

(def layout
  [[:clearfix {:clear :both}]
   [:.text-right {:text-align :right}]
   [:.ml5 {:margin-left (u/rem 0.5)}]
   [:.mr5 {:margin-right (u/rem 0.5)}]
   [:.mr10 {:margin-right (u/rem 1)}]])

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
    [:&:hover {:background-color parchment}
     [:.actions__menu {:display :block}]]]
   [:.actions--show {:background-color parchment}]
   [:.actions__menu {:color parchment-dark
                     :font-size (u/pt 10)
                     :position :absolute
                     :right (u/rem 0.2)}]])

(def insert-cell
  [[:.insert-cell {:color parchment-dark
                   :margin-left (u/px -3.5)
                   :line-height 0.8
                   :position :relative}]
   [:.insert-cell__dropzone {:position :absolute
                             :top (u/px -15)
                             :bottom (u/px -15)
                             :left (u/px -1000)
                             :right (u/px -1000)}]
   [:.insert-cell__dropzone-marker {:position :absolute
                                    :top (u/px 5)
                                    :left (u/rem -1)
                                    :right (u/rem -1)
                                    :height (u/px 3)
                                    :background-color accent}]])

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
            :margin [[0 :auto (u/rem 10)]]}]
    [:code {:font-family ["'PT Mono'"]
            :font-size (u/pt 10)}]]
   util
   layout
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
