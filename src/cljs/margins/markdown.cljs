(ns margins.markdown
  (:require [clojure.string :as string]
            [markdown.core :refer [md->html]]))

(defn markdown [& ss]
  ^:dom [:div.trim {:dangerouslySetInnerHTML {:__html (md->html (string/join ss))}}])
