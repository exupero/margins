(ns margins.latex
  (:require [clojure.string :as string]
            [reagent.core :as r]))

(defonce init
  (do
    (js/MathJax.Hub.Config
      (clj->js
        {:messageStyle "none"
         :showProcessingMessages false
         :skipStartupTypeset true
         :tex2jax {:inlineMath [["@@" "@@"]]}}))
    (js/MathJax.Hub.Configured)))

(defn latex-component [_]
  (let [node (r/atom nil)
        rerender (fn [this]
                   (js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub @node]))]
    (r/create-class
      {:reagent-render (fn [s]
                         [:div {:ref (partial reset! node)} s])
       :component-did-mount rerender
       :component-did-update rerender})))

(defn latex [& ss]
  (let [s (string/join ss)]
    (with-meta
      [latex-component (str "$$" s "$$")]
      {:dom true, :latex-source s})))
