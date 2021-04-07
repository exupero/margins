(ns margins.cljs
  (:require cljs.js
            [shadow.cljs.bootstrap.browser :as boot]
            margins.markdown))

(def state (cljs.js/empty-state))

(defn eval-code [form callback]
  (cljs.js/eval
    state
    ; eval doesn't seem to like bare string or symbol forms, so we wrap it in
    ; a list and in the callback we'll take the first element of the resulting
    ; value.
    [form]
    {:eval cljs.js/js-eval
     :load (partial boot/load state)}
    (fn [{[v] :value :as b}]
      (when callback
        (callback v)))))

(defn init [& code]
  (boot/init state {:path "/bootstrap"} eval-code))
