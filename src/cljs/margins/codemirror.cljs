(ns margins.codemirror
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            ["codemirror" :as cm]
            ["codemirror/mode/clojure/clojure"]
            ["codemirror/keymap/vim"]
            ["codemirror/addon/edit/closebrackets"]
            ["codemirror/addon/edit/matchbrackets"]))

(defn codemirror [id active? content]
  (let [textarea (r/atom nil)]
    (r/create-class
      {:component-did-mount
       (fn [this]
         (rf/dispatch [::init id active? @textarea]))
       :reagent-render
       (fn [_ _ content]
         [:div
          [:textarea {:ref (partial reset! textarea)
                      :default-value content
                      :style {:display "none"}}]])})))

(defn key-bindings [id]
  {"Meta-Enter" #(rf/dispatch [:margins.events/eval id (.getValue %)])})

(rf/reg-event-fx ::init
  (fn [{:keys [db]} [_ id active? node]]
    (let [extra-keys (clj->js (key-bindings id))]
      (cm/normalizeKeyMap extra-keys)
      {::init {:node node
               :focus? active?
               :config {:lineWrapping true
                        :viewportMargin js/Infinity
                        :matchBrackets true
                        :autoCloseBrackets true
                        :mode "clojure"
                        :theme "elegant"
                        :cursorHeight 0.9
                        :vimMode true
                        :extraKeys extra-keys}
               :on-change #(rf/dispatch [:margins.events/edit id (.getValue %)])}})))

(rf/reg-fx ::init
  (fn [{:keys [node config focus? on-change]}]
    (let [cm (cm/fromTextArea node (clj->js config))]
      (.on cm "change" on-change)
      (.addEventListener (.getWrapperElement cm) "keydown" #(.stopPropagation %))
      (when focus?
        (.focus cm)
        (.setCursor cm (.lineCount cm) 0)))))
