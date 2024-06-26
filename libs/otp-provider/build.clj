(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'pasaporte/otp-provider)
(def version (format "1.2.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]})

  (b/compile-clj {:basis @basis
                 ;; :ns-compile '[]
                  :class-dir class-dir})

  (b/copy-file {:src "keycloak-server.json" :target "target/classes/META-INF/keycloak-server.json"})

  (b/jar {:class-dir class-dir
          :jar-file jar-file}))
