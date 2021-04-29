(ns margins.views
  (:require [clojure.string :as string]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [margins.codemirror :refer [codemirror]]
            [margins.events :as events]
            [margins.subscriptions :as subs]
            margins.renderable))

(defn insert-cell [order]
  (let [marker (r/atom nil)]
    (fn [order]
      (let [hovered-position @(rf/subscribe [::subs/hovered-position])
            show? (<= hovered-position order (inc hovered-position))
            notebook-id @(rf/subscribe [::subs/notebook-id])]
        [:div.insert-cell
         (when-let [dragged-id @(rf/subscribe [::subs/dragged-cell-id])]
           [:div
            [:div.insert-cell__dropzone
             {:on-drag-enter #(.remove (.-classList @marker) "hide")
              :on-drag-over #(do (.stopPropagation %) (.preventDefault %))
              :on-drag-leave #(.add (.-classList @marker) "hide")
              :on-drop #(do
                          (.add (.-classList @marker) "hide")
                          (rf/dispatch [::events/drop-cell dragged-id order]))}]
            [:div.insert-cell__dropzone-marker.hide
             {:ref (partial reset! marker)}]])
         [:span.pointer
          {:class (when-not show? "hidden")
           :on-click #(rf/dispatch [::events/insert-cell notebook-id order])}
          "+"]]))))

(defn ico [k]
  [:i {:class (str "icofont-" (name k))}])

(defn cell-actions [_ _]
  (let [show-actions? (r/atom false)]
    (fn [{:keys [item/id cell/show-code? cell/order]} always-show?]
      [:div.cell__actions.pointer
       {:class (when always-show? "actions--show")
        :on-mouse-over #(rf/dispatch [::events/hover-position order])
        :on-click (when-not always-show?
                    #(rf/dispatch [::events/set-code-visibility id (not show-code?)]))}
       [:div.actions__menu.hide
        (when @show-actions?
          [:span
           [:span.mr5.hover-darken
            {:on-click #(rf/dispatch [::events/delete-cell id])}
            [ico :ui-delete]]
           [:span.mr5.hover-darken
            {:on-click #(rf/dispatch [::events/attach id])}
            [ico :paper-clip]]])
        [:span.grabber
         {:draggable true
          :on-click #(do
                       (.stopPropagation %)
                       (swap! show-actions? not))
          :on-drag-start #(rf/dispatch [::events/drag-cell id])
          :on-drag-end #(rf/dispatch [::events/drop-cell])}
         [ico :navigation-menu]]]])))

(defn parse-args-from-js [f]
  (-> (str f)
    (->> (re-find #"^function [^(]*\(([^)]+)\)"))
    second
    (string/split #",")
    (->> (mapv symbol))
    vec))

(defn repr [v]
  (if (fn? v)
    (str "(fn " (pr-str (parse-args-from-js v)) ")")
    (pr-str v)))

(defn result [_ _]
  (r/create-class
    {:component-did-mount #(rf/dispatch [::events/check-title])
     :component-did-update #(rf/dispatch [::events/check-title])
     :reagent-render (fn [nm v]
                       (cond
                         (nil? v) [:code "nil"]
                         nm [:code nm " = " (repr v)]
                         (fn? v) [:code (repr v)]
                         (number? v) [:code v]
                         (-> v meta :dom) v
                         (satisfies? margins.renderable/IRender v) (margins.renderable/render v)
                         :else (pr-str v)))}))

(defn attachments [attaches]
  [:div.attachment
   (for [{nm :attachment/name :keys [attachment/content-type]} attaches]
     [:div.ml5 {:key nm}
      [ico :paper-clip]
      [:em.ml5 nm]])])

(defn error [e]
  [:div "An error occurred:"
   (.toString e)])

(defn error-boundary [& _]
  (let [err (r/atom nil)]
    (r/create-class
      {:get-derived-state-from-error
       (fn [e]
         (reset! err e)
         #js {})
       :component-did-catch
       (fn [_ e _]
         (reset! err e))
       :reagent-render
       (fn [& children]
         (if @err
           [:div [:pre (pr-str @err)]]
           (into [:<>] children)))})))

(defn cell [{nm :cell/name attaches :attachment/_cell :keys [item/id cell/code cell/show-code? cell/value cell/order cell/dirty?]}]
  (let [hovered-position @(rf/subscribe [::subs/hovered-position])]
    [:div.cell__main
     {:class (when (or show-code? dirty? (and (nil? value) (nil? attaches)))
               "cell--side-border")
      :on-mouse-over #(rf/dispatch [::events/hover-position order])}
     (when attaches
       [attachments attaches])
     (when value
       [:div.cell__value.trim
        [error-boundary [result nm value]]])
     (when show-code?
       [:div.cell__code [codemirror id true code]])]))

(defn notebook []
  [:div
   [insert-cell 0]
   (for [{attaches :attachment/_cell :keys [item/id cell/show-code? cell/value cell/order] :as c} @(rf/subscribe [::subs/cells])]
     [:div {:key id}
      [:div.cell
       [cell-actions c (and (nil? value) (nil? attaches))]
       [cell c]]
      [insert-cell (inc order)]])])

(defn index []
  [:div
   (for [{:keys [item/id notebook/title notebook/slug]} @(rf/subscribe [::subs/notebooks])]
     [:div {:key id}
      [:a {:href (str "/" slug)
           :on-click #(do (.preventDefault %) (rf/dispatch [::events/go-to-notebook slug]))}
       title]])])

(defn main []
  [:main
   [:div.text-right
    [:a.mr10 {:href "/"
              :on-click #(do (.preventDefault %) (rf/dispatch [::events/go-to-index]))}
     "All Notebooks"]
    [:button.pointer
     {:on-click #(rf/dispatch [::events/create-notebook])}
     "New Notebook"]]
   [:div.clearfix]
   (condp = @(rf/subscribe [::subs/route])
     :index [index]
     :notebook [notebook]
     [:div "Loading..."])])
