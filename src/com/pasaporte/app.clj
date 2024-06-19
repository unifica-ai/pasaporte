(ns com.pasaporte.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.pasaporte.middleware :as mid]
            [com.pasaporte.ui :as ui]
            [com.pasaporte.auth :as auth]))

(defn app [ctx]
  (ui/page
   ctx
   [:h1.text-2xl "App"]
   (biff/form
    {:action "/app/signout"
     :class "inline"}
    [:a.link {:href "/auth/config"} "Show config"])))

(defn signout [{:keys [session]}]
  {:status 303
   :headers {"location" "/"}
   :session (dissoc session :uid)})

(def module
  {:routes ["/app" {:middleware [mid/wrap-signed-in auth/wrap-auth-context auth/wrap-keycloak]}
            ["/signout" {:post signout}]
            ["" {:get app}]]})
