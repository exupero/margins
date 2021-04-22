(ns margins.parse
  (:require #?(:clj [clojure.tools.reader.reader-types :refer [string-push-back-reader]]
               :cljs [cljs.tools.reader.reader-types :refer [string-push-back-reader]])
            [margins.reader :as reader]))

(def data-readers {'md #(list* 'margins.markdown/markdown %)})

(defn parse-string
  ([s] (parse-string s data-readers))
  ([s data-readers]
   (let [rdr (string-push-back-reader s)]
     (binding [reader/*data-readers* data-readers]
       (loop [forms []]
         (let [form (reader/read {:eof :eof} rdr)]
           (if (= form :eof)
             forms
             (recur (conj forms form)))))))))

(defn parse-name-and-form [code]
  (let [[nm-or-form form] (parse-string code)]
    (cond
      (and form (symbol? nm-or-form)) [nm-or-form form]
      (and (list? nm-or-form) (= 'include (first nm-or-form))) [(second nm-or-form) nm-or-form]
      :else [nil nm-or-form])))
