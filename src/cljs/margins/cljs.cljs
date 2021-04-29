(ns margins.cljs
  (:require cljs.js
            [shadow.cljs.bootstrap.browser :as boot]
            [margins.db :as db]
            ; these need to be included as they may be used by evaluated expressions
            net.cgrand.xforms
            margins.csv
            margins.latex
            margins.markdown
            margins.vega))

(defonce state (cljs.js/empty-state))

#_
(defn find-args-in-state [nm]
  (-> @state :cljs.analyzer/namespaces (get 'cljs.user) :defs (get nm) :method-params))

(defonce loaded? (atom false))
(defonce eval-queue (atom []))

(defn eval-as-is [form id nm callback]
  (cljs.js/eval state form
    {:ns 'margins.user
     :eval cljs.js/js-eval
     :load (partial boot/load state)}
    (fn [{v :value :as b}]
      (when callback
        (callback v)))))

(defn expand-form [id nm form]
  (let [arg (gensym "arg")]
    (if nm
      (list 'def nm form)
      form)))

(defn eval-with-def [form id nm callback]
  (cljs.js/eval
    state
    ; eval doesn't seem to like bare string or symbol forms, so we wrap it in
    ; a vector and in the callback we'll take the first element of the resulting
    ; value.
    [(expand-form id nm form)]
    {:ns 'margins.user
     :eval cljs.js/js-eval
     :load (partial boot/load state)}
    (fn [{[v] :value :as b}]
      (when callback
        (callback v)))))

(defn eval-form [form id nm callback]
  (cond
    (nil? form) (callback nil)
    (not @loaded?) (swap! eval-queue conj {:id id :name nm :form form :callback callback})
    (and (list? form) ('#{ns require} (first form))) (eval-as-is form id nm callback)
    :else (eval-with-def form id nm callback)))

(defn eval-forms [forms]
  (when-let [[{nm :name :keys [id form load-include callback]} & forms] (seq forms)]
    (let [ev (fn [nm form]
               (eval-form form id nm (fn [value]
                                       (callback value)
                                       (eval-forms forms))))]
      (if (and (list? form) (= 'include (first form)))
        (let [[_ include-name] form]
          (load-include form #(ev (or nm include-name) %)))
        (ev nm form)))))

(defn init! []
  (boot/init state {:path "/js/bootstrap"}
             (fn []
               (reset! loaded? true)
               (eval-form '(ns margins.user) nil nil
                          (fn [_]
                            (set! js/margins.user.attachment #(db/attachment %))
                            (eval-forms @eval-queue))))))

(defn possible-dependencies [available-dependencies form]
  (into #{}
        (filter (every-pred symbol? (set available-dependencies)))
        (tree-seq (some-fn sequential? map? set?) seq form)))
