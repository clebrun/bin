(ns bin.backup.app
  (:require [babashka.deps :as deps]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.edn :as edn]))

(deps/add-deps '{:deps {failjure/failjure {:mvn/version "2.3.0"}}})

(require '[failjure.core :as f])

(defn fatal-error
  [message]
  (binding [*out* *err*] (println (str "Aborted Backup: " message)))
  (System/exit 1))

(def expand-home
  "version of fs/expand-home that retuns a string, not a path object"
  (comp str fs/expand-home))

(def DEFAULT-CONFIG-PATH
  (expand-home "~/.backup_config.edn"))

(def DEFAULT-CONFIG
  "config map with default values
  :backup-from - A list of places to backup from e.g. ~/Documents
  :backup-to - A list of folders to backup to e.g. /dev/flashdrive/backup"
  {:backup-to []
   :backup-from []
   :ignore-patterns []})

(def assert-file-exists?
  "failjure assertion for file existance"
  (partial f/assert-with fs/exists?))

(defn try-read-edn
  "Failjure version of edn/read"
  [edn-path]
  (f/try* ; returns failure if anything in body throws an exception
   (f/ok->
     edn-path
     (assert-file-exists? (format "Couldn't find edn file %s" edn-path))
     slurp
     edn/read-string)))

(defn try-get-config [path]
  "Reads config file path and processes values"
  (f/as-ok-> (try-read-edn path) $
             ;; Need folders/files to backup and a place to back them up to
             (f/assert-not-empty? (:backup-from $) "No files/folders to backup are specified")
             (f/assert-not-empty? (:backup-to $) "No backup locations are specified")
             (merge $ DEFAULT-CONFIG)
             ;; expand any paths with the home shortcut (~)
             (update-in $ [:backup-from] (partial map expand-home))
             (update-in $ [:backup-to] (partial map expand-home))))

(defn -main []
  (f/attempt-all [user-config (try-get-config DEFAULT-CONFIG-PATH) ; TODO: user specifies config path
                  ]
    config
    (f/when-failed [e] (-> e f/message fatal-error))))
