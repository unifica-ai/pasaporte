(ns pasaporte.keycloak.otp-provider-factory
  (:require
   [pasaporte.keycloak.otp-provider])
  (:import
   (pasaporte.keycloak OTPProvider))
  (:gen-class
   :name pasaporte.keycloak.OTPProviderFactory
   :main false
   :implements [org.keycloak.provider.ProviderFactory]))

(defn -create [session]
  (OTPProvider.))

(defn -init [this] ())

(defn -postInit [this] ())

(defn -close [this] ())

(defn -getId [this] "OTPProvider")
