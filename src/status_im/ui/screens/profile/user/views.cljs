(ns status-im.ui.screens.profile.user.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [re-frame.core :as re-frame]
            [status-im.i18n :as i18n]
            [status-im.protocol.core :as protocol]
            [status-im.ui.components.action-button.styles :as action-button.styles]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.common.styles :as common.styles]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.ui.components.list-selection :as list-selection]
            [status-im.ui.components.qr-code-viewer.views :as qr-code-viewer]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.status-bar.view :as status-bar]
            [status-im.ui.components.toolbar.view :as toolbar]
            [status-im.ui.screens.profile.components.views :as profile.components]
            [status-im.ui.screens.profile.components.styles :as profile.components.styles]
            [status-im.ui.screens.profile.user.styles :as styles]
            [status-im.utils.config :as config]
            [status-im.utils.platform :as platform]
            [status-im.utils.utils :as utils]
            [status-im.ui.components.icons.vector-icons :as icons]
            [status-im.ui.components.common.common :as components.common]
            [clojure.string :as string]))

(defn my-profile-toolbar []
  [toolbar/toolbar {}
   nil
   [toolbar/content-title ""]
   [react/touchable-highlight
    {:on-press            #(re-frame/dispatch [:my-profile/start-editing-profile])
     :accessibility-label :edit-button}
    [react/view
     [react/text {:style      common.styles/label-action-text
                  :uppercase? true}
      (i18n/label :t/edit)]]]])

(defn my-profile-edit-toolbar []
  [toolbar/toolbar {}
   nil
   [toolbar/content-title ""]
   [toolbar/default-done {:handler             #(re-frame/dispatch [:my-profile/save-profile])
                          :icon                :icons/ok
                          :icon-opts           {:color colors/blue}
                          :accessibility-label :done-button}]])

(def profile-icon-options
  [{:label  (i18n/label :t/image-source-gallery)
    :action #(re-frame/dispatch [:my-profile/update-picture])}
   {:label  (i18n/label :t/image-source-make-photo)
    :action (fn []
              (re-frame/dispatch [:request-permissions
                                  [:camera :write-external-storage]
                                  #(re-frame/dispatch [:navigate-to :profile-photo-capture])
                                  #(utils/show-popup (i18n/label :t/error)
                                                     (i18n/label :t/camera-access-error))]))}])

(defn qr-viewer-toolbar [label value]
  [toolbar/toolbar {}
   [toolbar/default-done {:icon-opts           {:color colors/black}
                          :accessibility-label :done-button}]
   [toolbar/content-title label]
   [toolbar/actions [{:icon      :icons/share
                      :icon-opts {:color               :black
                                  :accessibility-label :share-code-button}
                      :handler   #(list-selection/open-share {:message value})}]]])

(defview qr-viewer []
  (letsubs [{:keys [value contact]} [:get :qr-modal]]
    [react/view styles/qr-code-viewer
     [status-bar/status-bar {:type :modal}]
     [qr-viewer-toolbar (:name contact) value]
     [qr-code-viewer/qr-code-viewer {:style styles/qr-code}
      value (i18n/label :t/qr-code-public-key-hint) (str value)]]))

(defn- show-qr [contact source value]
  #(re-frame/dispatch [:navigate-to :profile-qr-viewer {:contact contact
                                                        :source  source
                                                        :value   value}]))

(defn share-contact-code [current-account public-key]
  [react/touchable-highlight {:on-press (show-qr current-account :public-key public-key)}
   [react/view styles/share-contact-code
    [react/view styles/share-contact-code-text-container
     [react/text {:style      styles/share-contact-code-text
                  :uppercase? true}
      (i18n/label :t/share-contact-code)]]
    [react/view {:style               styles/share-contact-icon-container
                 :accessibility-label :share-my-contact-code-button}
     [vector-icons/icon :icons/qr {:color colors/blue}]]]])

(defn my-profile-settings [{:keys [seed-backed-up? mnemonic]}]
  [react/view
   [profile.components/settings-title (i18n/label :t/settings)]
   [profile.components/settings-item {:label-kw :t/main-currency
                                      :value "USD"
                                      :active? false}]
   [profile.components/settings-item-separator]
   [profile.components/settings-item {:label-kw :t/notifications
                                      :action-fn #(.openURL react/linking "app-settings://notification/status-im")}
    :notifications-button]
   [profile.components/settings-item-separator]
   (when (and (not seed-backed-up?) (not (string/blank? mnemonic)))
     [react/view
      [profile.components/settings-item
       {:label-kw     :t/backup-your-seed
        :action-fn    #(re-frame/dispatch [:navigate-to :backup-seed])
        :icon-content [components.common/counter {:size 22} 1]}]
      [profile.components/settings-item-separator]])])

(defn navigate-to-accounts [sharing-usage-data?]
  ;; TODO(rasom): probably not the best place for this call
  (protocol/stop-whisper!)
  (re-frame/dispatch [:navigate-to :accounts])
  (when sharing-usage-data?
    (re-frame/dispatch [:unregister-mixpanel-tracking])))

(defn handle-logout [sharing-usage-data?]
  (utils/show-confirmation (i18n/label :t/logout-title)
                           (i18n/label :t/logout-are-you-sure)
                           (i18n/label :t/logout) #(navigate-to-accounts sharing-usage-data?)))

(defn logout [sharing-usage-data?]
  [react/view {}
   [react/touchable-highlight
    {:on-press            #(handle-logout sharing-usage-data?)
     :accessibility-label :log-out-button}
    [react/view profile.components.styles/settings-item
     [react/text {:style styles/logout-text
                  :font  (if platform/android? :medium :default)}
      (i18n/label :t/logout)]]]])

(defview advanced [{:keys [network networks dev-mode?]}]
  (letsubs [advanced? [:get :my-profile/advanced?]]
    [react/view
     [react/touchable-highlight {:on-press #(re-frame/dispatch [:set :my-profile/advanced? (not advanced?)])
                                 :style styles/advanced-button}
      [react/view {:style styles/advanced-button-container}
       [react/view {:style styles/advanced-button-container-background}
        [react/view {:style styles/advanced-button-row}
         [react/text {:style styles/advanced-button-label}
          (i18n/label :t/wallet-advanced)]
         [icons/icon (if advanced? :icons/up :icons/down) {:color colors/blue}]]]]]
     (when advanced?
       [react/view
        [profile.components/settings-item
         {:label-kw :t/network
          :value (get-in networks [network :name])
          :action-fn #(re-frame/dispatch [:navigate-to :network-settings])
          :accessibility-label :network-button}]
        (when config/offline-inbox-enabled?
          [profile.components/settings-item-separator])
        (when config/offline-inbox-enabled?
          [profile.components/settings-item
           {:label-kw :t/offline-messaging-settings
            :action-fn #(re-frame/dispatch [:navigate-to :offline-messaging-settings])
            :accessibility-label :offline-messages-settings-button}])
        [profile.components/settings-item-separator]
        [profile.components/settings-switch-item
         {:label-kw :t/dev-mode
          :value dev-mode?
          :action-fn #(re-frame/dispatch [:switch-dev-mode %])}]])]))

(defview my-profile []
  (letsubs [{:keys [public-key sharing-usage-data?] :as current-account} [:get-current-account]
            editing?        [:get :my-profile/editing?]
            changed-account [:get :my-profile/profile]]
    (let [shown-account (merge current-account changed-account)]
      [react/view profile.components.styles/profile
       (if editing?
         [my-profile-edit-toolbar]
         [my-profile-toolbar])
       [react/scroll-view
        [react/view profile.components.styles/profile-form
         [profile.components/profile-header shown-account editing? true profile-icon-options :my-profile/update-name]]
        [react/view action-button.styles/actions-list
         [share-contact-code current-account public-key]]
        [react/view styles/my-profile-info-container
         [my-profile-settings current-account]]
        [logout sharing-usage-data?]
        [advanced shown-account]]])))
