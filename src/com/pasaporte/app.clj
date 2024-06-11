(ns com.pasaporte.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.pasaporte.middleware :as mid]
            [com.pasaporte.ui :as ui]
            [com.pasaporte.settings :as settings]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [cheshire.core :as cheshire]
            [com.pasaporte.auth :as auth]))

(comment
  (auth/oidc-get-userinfo ctx*))

(defn app [ctx]
  (def ctx* ctx)
  (ui/page
   ctx
   [:h1.text-2xl "App"]
   (biff/form
    {:action "/app/signout"
     :class "inline"}
    [:button.link {:type "submit"}
     "Sign out"])))

(defn signout [{:keys [session]}]
  {:status 303
   :headers {"location" "/"}
   :session (dissoc session :uid)})

(def module
  {:routes ["/app" {:middleware [mid/wrap-signed-in auth/wrap-auth-context auth/wrap-keycloak]}
            ["/signout" {:post signout}]
            ["" {:get app}]]})
