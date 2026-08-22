#!/usr/bin/env bb

(ns done-with-current
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.java.shell :as sh]
            [clojure.string :as str]))

(def script-dir (fs/parent *file*))

(defn exit! [status message]
  (binding [*out* *err*]
    (println message))
  (System/exit status))

(defn command [& args]
  (apply sh/sh args))

(defn git-root []
  (let [result (command "git" "rev-parse" "--show-toplevel")]
    (when (zero? (:exit result))
      (str/trim (:out result)))))

(defn git-common-dir []
  (let [result (command "git" "rev-parse" "--git-common-dir")]
    (when (zero? (:exit result))
      (let [path (str/trim (:out result))]
        (if (fs/absolute? path)
          path
          (str (fs/absolutize path)))))))

(defn roles-at? [root]
  (and root (fs/exists? (fs/path root ".swarmforge" "roles.tsv"))))

(defn project-root []
  (or (let [parent (some-> (git-common-dir) fs/parent str)]
        (when (roles-at? parent) parent))
      (when (roles-at? (git-root)) (git-root))
      (when (roles-at? (fs/cwd)) (str (fs/cwd)))
      (exit! 1 "Cannot find SwarmForge project root")))

(defn same-path? [a b]
  (try
    (= (str (fs/canonicalize a)) (str (fs/canonicalize b)))
    (catch Exception _
      (= (str a) (str b)))))

(defn infer-role-from-worktree []
  (let [here (or (git-root) (str (fs/absolutize ".")))]
    (some (fn [line]
            (let [cols (str/split line #"\t")
                  role-name (first cols)
                  wt (when (>= (count cols) 3) (nth cols 2))]
              (when (and (not-empty role-name) (not-empty wt) (same-path? wt here))
                role-name)))
          (str/split-lines (slurp (str (fs/path (project-root) ".swarmforge" "roles.tsv")))))))

(defn role []
  (or (not-empty (System/getenv "SWARMFORGE_ROLE"))
      (infer-role-from-worktree)
      (exit! 1 "Set SWARMFORGE_ROLE.")))

(defn receive-mode [role-name]
  (let [roles (str/split-lines (slurp (str (fs/path (project-root) ".swarmforge" "roles.tsv"))))]
    (or (some (fn [line]
                (let [fields (str/split line #"\t" -1)]
                  (when (= role-name (first fields))
                    (not-empty (get fields 6 "task")))))
              roles)
        (exit! 1 (str "Unknown role: " role-name)))))

(defn run-helper! [script]
  (process/exec (str (fs/path script-dir script))))

(defn -main []
  (case (receive-mode (role))
    "batch" (run-helper! "done_with_current_batch.sh")
    "task" (run-helper! "done_with_current_task.sh")
    (exit! 2 (str "INVALID_RECEIVE_MODE: " (receive-mode (role)) " for role " (role)))))

(-main)
