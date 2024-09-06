(ns tasks
  (:require [com.biffweb.tasks :as tasks]
            [com.biffweb.config :as config]))

;; (defn portal
;;   "Opens an SSH tunnel so you can connect to the server via nREPL."
;;   []
;;   (let [{:keys [biff.tasks/server keycloak/port]} @config]
;;     (println "Connect to Keycloak on port" port)
;;     (shell "ssh" "-NL" (str port ":localhost:" port) (str "root@" server))))

;; Tasks should be vars (#'hello instead of hello) so that `clj -M:dev help` can
;; print their docstrings.
(def custom-tasks
  #_{"keycloak" #'portal} {})


(def tasks (merge tasks/tasks custom-tasks))
