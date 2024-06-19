(ns com.pasaporte.auth
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [clj-http.client :as http]
   [clojure.core.protocols :as p]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.biffweb :as biff]
   [com.pasaporte.ui :refer [dl] :as ui]
   [ring.util.codec :as ru])
  (:import
   (java.security.cert CertificateFactory)
   (java.time Instant)
   (org.keycloak TokenVerifier)))

(defn authinfo
  "Returns info for the currently loged in user"
  ([{:keys [session] :as ctx}]
   (authinfo ctx (:uid session)))
  ([{:keys [biff/db]} uid]
   (biff/lookup db '[:keycloak.auth/access-token] :keycloak.auth/user uid)))

(defn merge-context
  "Merge user authentication tokens for user (or signed-in user) into context"
  ([ctx]
   (merge ctx (authinfo ctx))))

(defn wrap-auth-context [handler]
  (fn [ctx]
    (handler (merge-context ctx))))

(defn oidc-get-tokens
  "Gets a token from an authorization code"
  [{:biff/keys [base-url secret]
    :keycloak/keys [token-endpoint client-id code debug debug-body]}]
  (let [url token-endpoint
        params {:grant_type "authorization_code"
                :redirect_uri (str base-url "/auth/signin")
                :client_id client-id
                :client_secret (secret :keycloak/client-secret)}
        tokens (try (-> (http/post url {:form-params (merge params {:code code})
                                        :as :json
                                        :debug debug :debug-body debug-body})
                        :body (->> (cske/transform-keys csk/->kebab-case-keyword)))
                    (catch Exception e
                      (log/error e "Error getting OIDC authorization token: ")))]
    (if tokens
      (assoc tokens :success true)
      {:success false})))

;; https://www.keycloak.org/docs/latest/securing_apps/#userinfo-endpoint
(defn oidc-get-userinfo
  "Gets userinfo"
  [{:keycloak/keys [userinfo-endpoint debug]
    :keycloak.auth/keys [access-token]}]
  (-> (http/get userinfo-endpoint {:oauth-token access-token :as :json :debug debug})
      :body))

(defn token-verifier [{:keycloak/keys [jwks-keys]} access-token]
  (let [verifier (TokenVerifier/create access-token org.keycloak.representations.AccessToken)
        kid (-> verifier .getHeader .getKeyId)
        cert (-> jwks-keys
                 (->> (filter (fn [{k :kid}] (= k kid))))
                 first)
        x5c (-> cert :x5c first biff/base64-decode io/input-stream)
        pkey (-> (CertificateFactory/getInstance "x509")
                 (.generateCertificate x5c)
                 .getPublicKey)]
    (-> verifier (.publicKey pkey))))

(defn authenticate
  [{:keys [auth/auth-fn] :or {auth-fn oidc-get-tokens} :as ctx}]
   (auth-fn ctx))

(defn persist-token-tx
  [_ctx {:keys [access-token scope exp subject]}]
  [{:db/doc-type :keycloak.auth
    :db.op/upsert {:keycloak.auth/user subject}
    :keycloak.auth/access-token access-token
    :keycloak.auth/created-at :db/now
    :keycloak.auth/scope scope
    :keycloak.auth/exp exp}])

(defn persist-token-userinfo-tx
  [_ctx
   {:keys [given-name name email subject] :as _claims}]
  [{:db/doc-type :user
    :db.op/upsert {:xt/id subject}
    :user/given-name given-name
    :user/name name
    :user/email email
    :user/joined-at :db/now}])

(defn- persist-access-token
  "Persist token and claims to the database"
  [{:auth/keys [persist-token-tx persist-token-userinfo-tx] :as ctx} claims]
  (let [tx (concat
            (persist-token-tx ctx claims)
            (persist-token-userinfo-tx ctx claims))]
    (biff/submit-tx ctx tx)))

(extend-protocol p/Datafiable
  org.keycloak.representations.AccessToken
  (datafy [o]
    {:given-name (.getGivenName o)
     :name (.getName o)
     :email (.getEmail o)
     :scope (-> (.getScope o) (str/split #" ") (->> (map keyword) vec))
     :subject (-> (.getSubject o) parse-uuid)
     :exp (-> (.getExp o) Instant/ofEpochSecond)}))

(defn oidc-signin [{:keys [session params] :as ctx}]
  (log/info params)
  (let [{:keys [code state scope]} params
        {:keys [success access-token] :as tokens} (authenticate (assoc ctx :keycloak/code code))
        verifier (delay (token-verifier ctx access-token))
        claims (delay (-> @verifier .verify .getToken))]
    (if success
      (do
        (persist-access-token ctx (-> (p/datafy @claims) (assoc :access-token access-token)) )
        {:status 303
         :headers {"location" "/"}
         :session (-> session
                      (assoc :uid (-> (.getSubject @claims)
                                      parse-uuid)))})
      {:status 303
       :headers {"location" "/?error=auth-error"}})))

;; https://openid.net/specs/openid-connect-core-1_0.html#AuthorizationEndpoint
(defn oidc-redirect [{:biff/keys [base-url]
                      :keycloak/keys [authorization-endpoint client-id] :as ctx}]
  (let [scope "openid email"
        state "State"
        url (str authorization-endpoint
                 "?" (ru/form-encode {:response_type "code"
                                      :client_id client-id
                                      :scope scope
                                      :state state
                                      :redirect_uri (str base-url "/auth/signin")}))]
    {:status 303
     :headers {"location" url}}))

(defn oidc-get-well-known-config
  [{:keycloak/keys [realm-url debug]}]
  (let [url (str realm-url "/.well-known/openid-configuration")]
    (-> (http/get url {:as :json :debug debug}) :body
        (->> (cske/transform-keys csk/->kebab-case-keyword))
        (update-keys (comp (partial keyword "keycloak") name)))))

(defn config [ctx]
  (let [conf (oidc-get-well-known-config ctx)]
    (ui/page
     ctx
     [:h2 "Keycloak Config"
      (dl conf)])))

(defn jwks-keys
  [{:keycloak/keys [jwks-uri]}]
  (-> (http/get jwks-uri {:as :json}) :body :keys))

(defonce ^:private keycloak-config (atom {}))

(defn use-keycloak [ctx]
  (let [cfg (oidc-get-well-known-config ctx)
        keys (jwks-keys cfg)
        cfg (assoc cfg :keycloak/jwks-keys keys)]
    (reset! keycloak-config cfg))
 ctx)

(defn- wrap-options [handler options]
  (fn [ctx]
    (handler (merge @keycloak-config options ctx))))

(defn wrap-keycloak
  "Add Keycloak config in other modules"
  [handler]
  (let [p (select-keys @keycloak-config [:keycloak/userinfo-endpoint])]
    (fn [ctx]
      (handler (merge p ctx)))))

(def default-options
  #:auth{:app-path "/app"
         :persist-token-tx persist-token-tx
         :persist-token-userinfo-tx persist-token-userinfo-tx})

(def ^:private schema
  {:keycloak.auth/id :uuid
   :keycloak.auth
   [:map {:closed true}
    [:xt/id :keycloak.auth/id]
    [:keycloak.auth/access-token :string]
    [:keycloak.auth/user :uuid]
    [:keycloak.auth/scope [:vector keyword?]]
    [:keycloak.auth/exp inst?]
    [:keycloak.auth/created-at inst?]]})

(defn module [options]
  {:schema schema
   :routes ["/auth" {:middleware [[wrap-options (merge default-options options)]]}
            ["/config" {:get config}]
            ["/redirect" {:post oidc-redirect}]
            ["/signin" {:get oidc-signin}]]})
