(ns margins.cljs
  (:require cljs.js
            [cljs.tools.reader.reader-types :refer [string-push-back-reader]]
            [shadow.cljs.bootstrap.browser :as boot]
            [margins.reader :as reader]
            margins.markdown))

(def data-readers {'md #(list* 'margins.markdown/markdown %)})

(defn parse-string
  ([s] (parse-string s data-readers))
  ([s data-readers]
   (let [rdr (string-push-back-reader s)]
     (loop [forms []]
       (let [form (reader/read {:readers data-readers, :eof :eof} rdr)]
         (if (= form :eof)
           forms
           (recur (conj forms form))))))))

(defonce state (cljs.js/empty-state))

#_
(defn find-args-in-state [nm]
  (-> @state :cljs.analyzer/namespaces (get 'cljs.user) :defs (get nm) :method-params))

(defn expand-form [id nm form]
  (let [arg (gensym "arg")]
    (list 'let ['<- (list 'fn [arg] (list 'js/emit id (list 'quote nm) arg))]
      (if nm
        (list 'def nm form)
        form))))

(defn eval-code [form id nm callback]
  (when form
    (cljs.js/eval
      state
      ; eval doesn't seem to like bare string or symbol forms, so we wrap it in
      ; a list and in the callback we'll take the first element of the resulting
      ; value.
      [(doto (expand-form id nm form) prn)]
      {:eval cljs.js/js-eval
       :load (partial boot/load state)}
      (fn [{[v] :value :as b}]
        (when callback
          (callback v))))))

(defn init []
  (boot/init state {:path "/bootstrap"} eval-code))

(defn possible-dependencies [available-dependencies form]
  (into #{}
        (filter (every-pred symbol? (set available-dependencies)))
        (tree-seq sequential? seq form)))

(defn define [nm value]
  (swap! state update-in [:cljs.analyzer/namespaces 'cljs.user :defs nm] dissoc :tag)
  (aset js/cljs.user (name nm) value))
