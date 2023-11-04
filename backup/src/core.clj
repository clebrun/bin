;; TODOS:
;; - [ ] Backup to local devices
;;   - [ ] Check for device availability
;;   - [ ] Check remaining available space to prevent OOM
;; - [ ] Are there newer files on the backup?
;; - [ ] file ignore patterns
;; - [ ] per directory deletion options
;; - [ ] show progress (-P)
;; - [ ] dry run (-n)
;; - [ ] named filesets
;; - [ ] backup compression
;; - [ ] rolling archives
;; - [ ] Backup to remote devices

(ns bin.backup.core
  (:require [babashka.deps :as deps]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as s]
            [failjure.core :as f]))

(def assert-file-exists?
  "failjure assertion for file existance"
  (partial f/assert-with fs/exists?))

(defn try-read-edn
  "Failjure version of edn/read"
  [edn-path]
  (f/try* ; returns failure if anything in body throws an exception
    (f/ok-> edn-path
            (assert-file-exists? (format "Couldn't find edn file %s" edn-path))
            slurp
            edn/read-string)))

(def expand-home
  "version of fs/expand-home that retuns a string, not a path object"
  (comp str fs/expand-home))

(def DEFAULT-CONFIG-PATH (expand-home "~/.backup_config.edn"))

(def DEFAULT-CONFIG
  "config map with default values
  :backup-from - A list of places to backup from e.g. ~/Documents
  :backup-to - A list of folders to backup to e.g. /dev/flashdrive/backup"
  {:backup-to [], :backup-from [], :ignore-patterns []})

(defn try-get-config
  "Reads config file path and processes values"
  [path]
  (f/as-ok-> (try-read-edn path)
             $
             ;; Need folders/files to backup and a place to back them up to
             (f/assert-not-empty? (:backup-from $)
                                  "No files/folders to backup are specified")
             (f/assert-not-empty? (:backup-to $)
                                  "No backup locations are specified")
             (merge $ DEFAULT-CONFIG)
             ;; expand any paths with the home shortcut (~)
             (update-in $ [:backup-from] (partial map expand-home))
             (update-in $ [:backup-to] (partial map expand-home))))

(def rsync-options-map
  {:archive "-a", :delete "--delete", :progress "--progress"})

(defn try-rsync-command
  ;; TODO: document keys
  "Build an rsync command"
  [& {:keys [sources dest options], :or {options []}}]
  (f/attempt-all
    [option-flags (map #(get rsync-options-map %) options)
     unrecognized-flags (filter #(->> % vals (not-any? nil?)) option-flags)
     _ (f/assert-empty? unrecognized-flags
                        (str "Unrecognized or unsupported rsync flags detected: " (s/join ", " unrecognized-flags)))
     sources (f/assert-not-empty? sources "invalid rsync command: no sources")
     dest (f/assert-not-empty? dest "invalid rsync command: no destination")]
    (s/join " " (concat ["rsync"] option-flags sources [dest]))))

#_(rsync-command :options [:archive :delete :non])

(defn fatal-error
  [message]
  (binding [*out* *err*] (println (str "Aborted Backup: " message)))
  (System/exit 1))

(defn -main
  []
  (f/attempt-all [config (try-get-config DEFAULT-CONFIG-PATH) ; TODO: user
                  ; specifies config
                  ; path
                 ]
                 config
                 (f/when-failed [e]
                                (-> e
                                    f/message
                                    fatal-error))))
