(ns pasaporte.keycloak.otp-provider
  (:gen-class
   :name pasaporte.keycloak.OTPProvider
   :main false
   :implements [org.keycloak.provider.Provider]
   :methods [[sendOTP [] Void]]))

(defn -close [this] (println "In CLOSE method"))

(defn -sendOTP [this] (println "In SENDOTP method"))
