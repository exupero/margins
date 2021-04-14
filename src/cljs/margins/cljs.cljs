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
     (binding [reader/*data-readers* data-readers]
       (loop [forms []]
         (let [form (reader/read {:eof :eof} rdr)]
           (if (= form :eof)
             forms
             (recur (conj forms form)))))))))

(defonce state (cljs.js/empty-state))

#_
(defn find-args-in-state [nm]
  (-> @state :cljs.analyzer/namespaces (get 'cljs.user) :defs (get nm) :method-params))

(defn expand-form [id nm form]
  (let [arg (gensym "arg")]
    (if nm
      (list 'def nm form)
      form)))

(defonce loaded? (atom false))
(defonce eval-queue (atom []))

(defn eval-form [form id nm callback]
  (cond
    (nil? form) (callback nil)
    @loaded? (cljs.js/eval
               state
               ; eval doesn't seem to like bare string or symbol forms, so we wrap it in
               ; a vector and in the callback we'll take the first element of the resulting
               ; value.
               [(expand-form id nm form)]
               {:eval cljs.js/js-eval
                :load (partial boot/load state)}
               (fn [{[v] :value :as b}]
                 (when callback
                   (callback v))))
    :else (swap! eval-queue conj {:id id :name nm :form form :callback callback})))

(defn eval-forms [forms]
  (when-let [[{nm :name :keys [id form callback]} & forms] (seq forms)]
    (eval-form form id nm (fn [value]
                            (callback value)
                            (eval-forms forms)))))

(defn init! []
  (boot/init state {:path "/bootstrap"}
             (fn []
               (reset! loaded? true)
               (eval-forms @eval-queue))))

(defn possible-dependencies [available-dependencies form]
  (into #{}
        (filter (every-pred symbol? (set available-dependencies)))
        (tree-seq (some-fn sequential? map? set?) seq form)))
