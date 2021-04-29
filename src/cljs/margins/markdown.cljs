(ns margins.markdown
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [markdown.core :refer [md->html]]))

(defn markdown-component [_]
  (let [node (r/atom nil)
        rerender (fn [this]
                   (js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub @node]))]
    (r/create-class
      {:reagent-render
       (fn [s]
         [:div.trim {:dangerouslySetInnerHTML {:__html (md->html s)}}])
       :component-did-mount rerender
       :component-did-update rerender})))

(defn markdown [& ss]
  ^:dom [markdown-component
         (transduce
           (map (fn [s]
                  (if-let [src (-> s meta :latex-source)]
                    (str "@@" src "@@")
                    s)))
           str ss)])
