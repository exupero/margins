(ns margins.views
  (:require [re-frame.core :as rf]
            [margins.codemirror :refer [codemirror]]
            [margins.subscriptions :as subs]
            [margins.events :as events]))

(defn insert-cell [order]
  (let [hovered-position @(rf/subscribe [::subs/hovered-position])
        show? (<= hovered-position order (inc hovered-position))]
    [:div.insert-cell.pointer
     {:class (when-not show? "hidden")
      :on-click #(rf/dispatch [::events/insert-cell order])}
     "+"]))

(defn cell-actions [{:keys [item/id cell/show-code? cell/order]} always-show?]
  [:div.cell__actions.pointer
   {:class (when always-show? "actions--show")
    :on-mouse-over #(rf/dispatch [::events/hover-cell order])
    :on-click (when-not always-show?
                #(rf/dispatch [::events/set-code-visibility id (not show-code?)]))}])

(defn cell [{:keys [item/id cell/code cell/show-code? cell/value cell/order]}]
  (let [hovered-position @(rf/subscribe [::subs/hovered-position])]
    [:div.cell__main
     {:class (when (or show-code? (nil? value) (= order hovered-position))
               "cell--side-border")
      :on-mouse-over #(rf/dispatch [::events/hover-cell order])}
     (when value
       [:div.cell__value.trim value])
     (when show-code?
       [:div.cell__code [codemirror id true code]])]))

(defn main []
  [:main
   [insert-cell 0]
   (doall
     (for [{:keys [item/id cell/show-code? cell/value cell/order] :as c} @(rf/subscribe [::subs/cells])]
       [:div {:key id}
        [:div.cell
         [cell-actions c (nil? value)]
         [cell c]]
        [insert-cell (inc order)]]))])
