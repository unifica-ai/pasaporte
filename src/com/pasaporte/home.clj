(ns com.pasaporte.home
  (:require
            [com.biffweb :as biff]
            [com.pasaporte.middleware :as mid]
            [com.pasaporte.ui :as ui]
            [com.pasaporte.settings :as settings]
            [rum.core :as rum]
            [xtdb.api :as xt]))

(def email-disabled-notice
  [:.text-sm.mt-3.bg-blue-100.rounded.p-2
   "Until you add API keys for MailerSend and reCAPTCHA, we'll print your sign-up "
   "link to the console. See config.edn."])

(defn home-page [{:keys [params] :as ctx}]
  (ui/page
   ctx
   (when-some [error (:error params)]
    [:<>
     [:.h-1]
     [:.text-sm.text-red-600
      (case error
        "not-signed-in" "You must be signed in to view that page.")]] )
   [:h1.text-2xl "Pasaporte"]
   (biff/form
     {:action "/auth/redirect"}
     [:<>
      [:button.link {:type "submit"} "Sign in"]
      [:.h-1]
      [:a.link {:href "/auth/config"} "Show config"]])))

(def module
  {:routes [["" {:middleware [mid/wrap-redirect-signed-in]}
             ["/" {:get home-page}]]]})
