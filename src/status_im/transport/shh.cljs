(ns status-im.transport.shh
  (:require [taoensso.timbre :as log]
            [re-frame.core :as re-frame]
            [status-im.transport.utils :as transport.utils]
            [status-im.transport.message.transit :as transit]))

(defn get-new-key-pair [{:keys [web3 on-success on-error]}]
  (if web3
    (.. web3
        -shh
        (newKeyPair (fn [err resp]
                      (if-not err
                        (on-success resp)
                        (on-error err)))))
    (on-error "web3 not available.")))

(re-frame/reg-fx
  :shh/get-new-key-pair
  (fn [{:keys [web3 success-event error-event]}]
    (get-new-key-pair {:web3       web3
                       :on-success #(re-frame/dispatch [success-event %])
                       :on-error   #(re-frame/dispatch [error-event %])})))

(defn get-public-key [{:keys [web3 key-pair-id on-success on-error]}]
  (if (and web3 key-pair-id)
    (.. web3
        -shh
        (getPublicKey key-pair-id (fn [err resp]
                                    (if-not err
                                      (on-success resp)
                                      (on-error err)))))
    (on-error "web3 or key-pair id not available.")))

(re-frame/reg-fx
  :shh/get-public-key
  (fn [{:keys [web3 key-pair-id success-event error-event]}]
    (get-public-key {:web3       web3
                     :key-pair-id key-pair-id
                     :on-success #(re-frame/dispatch [success-event %])
                     :on-error   #(re-frame/dispatch [error-event %])})))

(defn generate-sym-key-from-password
  [{:keys [web3 password on-success on-error]}]
  (.. web3
      -shh
      (generateSymKeyFromPassword password (fn [err resp]
                                             (if-not err
                                               (on-success resp)
                                               (on-error err))))))

(re-frame/reg-fx
  :shh/generate-sym-key-from-password
  (fn [{:keys [web3 password on-success on-error]}]
    (generate-sym-key-from-password {:web3       web3
                                     :password   password
                                     :on-success on-success
                                     :on-error   on-error})))

(defn post-message
  [{:keys [web3 whisper-message on-success on-error]}]
  (.. web3
      -shh
      (post (clj->js whisper-message) (fn [err resp]
                                        (if-not err
                                          (on-success resp)
                                          (on-error err))))))
(re-frame/reg-fx
  :shh/post
  (fn [{:keys [web3 message success-event error-event]
        :or {error-event   :protocol/send-status-message-error}}]
    (post-message {:web3       web3
                   :whisper-message (update message :payload (comp transport.utils/from-utf8
                                                                   transit/serialize))
                   :on-success (if success-event
                                 #(re-frame/dispatch success-event)
                                 #(log/debug :ssh/post-success))
                   :on-error   #(re-frame/dispatch [error-event %])})))

(re-frame/reg-fx
  :shh/multi-post
  (fn [{:keys [web3 message public-keys success-event error-event]
        :or {error-event   :protocol/send-status-message-error}}]
    (let [whisper-message (update message :payload (comp transport.utils/from-utf8
                                                         transit/serialize))]
      (doseq [public-key public-keys]
        (post-message {:web3            web3
                       :whisper-message (assoc whisper-message :pubKey public-key)
                       :on-success      (if success-event
                                          #(re-frame/dispatch success-event)
                                          #(log/debug :ssh/post-success))
                       :on-error        #(re-frame/dispatch [error-event %])})))))

(defn add-sym-key
  [{:keys [web3 sym-key on-success on-error]}]
  (.. web3
      -shh
      (addSymKey sym-key (fn [err resp]
                           (if-not err
                             (on-success resp)
                             (on-error err))))))

(defn get-sym-key
  [{:keys [web3 sym-key-id on-success on-error]}]
  (.. web3
      -shh
      (getSymKey sym-key-id (fn [err resp]
                              (if-not err
                                (on-success resp)
                                (on-error err))))))

(defn new-sym-key
  [{:keys [web3 on-success on-error]}]
  (.. web3
      -shh
      (newSymKey (fn [err resp]
                   (if-not err
                     (on-success resp)
                     (on-error err))))))

(defn log-error [error]
  (log/error :shh/get-new-sym-key-error error))


;;TODO (yenda) remove once go implements persistence
(re-frame/reg-fx
  :shh/add-sym-keys
  (fn [{:keys [web3 transport on-success]}]
    (doseq [[chat-id {:keys [sym-key]}] transport]
      (add-sym-key {:web3 web3
                    :sym-key sym-key
                    :on-success (fn [sym-key-id]
                                  (on-success chat-id sym-key sym-key-id))
                    :on-error log-error}))))

(re-frame/reg-fx
  :shh/add-new-sym-key
  (fn [{:keys [web3 sym-key on-success]}]
    (add-sym-key {:web3       web3
                  :sym-key    sym-key
                  :on-success (fn [sym-key-id]
                                (on-success sym-key sym-key-id))
                  :on-error log-error})))

(re-frame/reg-fx
  :shh/get-new-sym-key
  (fn [{:keys [web3 on-success]}]
    (new-sym-key {:web3       web3
                  :on-success (fn [sym-key-id]
                                (get-sym-key {:web3 web3
                                              :sym-key-id sym-key-id
                                              :on-success (fn [sym-key]
                                                            (on-success sym-key sym-key-id))
                                              :on-error log-error}))
                  :on-error log-error})))

;; public-chat
(re-frame/reg-fx
  :shh/generate-sym-key-from-password
  (fn [{:keys [web3 password on-success]}]
    (generate-sym-key-from-password {:web3       web3
                                     :password   password
                                     :on-success (fn [sym-key-id]
                                                   (get-sym-key {:web3 web3
                                                                 :sym-key-id sym-key-id
                                                                 :on-success (fn [sym-key]
                                                                               (on-success sym-key sym-key-id))
                                                                 :on-error log-error}))
                                     :on-error log-error})))