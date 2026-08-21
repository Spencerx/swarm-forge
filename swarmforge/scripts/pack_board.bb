#!/usr/bin/env bb

(ns pack-board
  (:require [babashka.fs :as fs]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(def usage-text
  (str "Usage:\n"
       "  pack_board.sh create --name <name> --lane <lane> [--root <dir>] [--text <text>]\n"
       "  pack_board.sh create <name> <lane>\n"
       "  pack_board.sh move --name <name> --lane <lane> [--root <dir>]\n"
       "  pack_board.sh move <name> <lane>\n"
       "  pack_board.sh done --name <name> [--root <dir>]\n"
       "  pack_board.sh done <name>\n"
       "  pack_board.sh list [--root <dir>]"))

(def flags {"--root" :root "--name" :name "--lane" :lane "--text" :text})

(defn usage []
  (binding [*out* *err*]
    (println usage-text)))

(defn exit! [status message]
  (binding [*out* *err*]
    (when message
      (println message)))
  (System/exit status))

(defn command [dir & args]
  (apply sh (concat args [:dir (str dir)])))

(defn git-root []
  (let [result (command "." "git" "rev-parse" "--show-toplevel")]
    (when (zero? (:exit result))
      (str/trim (:out result)))))

(defn git-common-dir []
  (let [result (command "." "git" "rev-parse" "--git-common-dir")]
    (when (zero? (:exit result))
      (let [path (str/trim (:out result))]
        (if (fs/absolute? path)
          (str (fs/path path))
          (str (fs/absolutize path)))))))

(defn project-root []
  (if-let [root (git-root)]
    (if (fs/exists? (fs/path root ".swarmforge" "roles.tsv"))
      root
      (if-let [common (git-common-dir)]
        (let [candidate (str (fs/parent common))]
          (if (fs/exists? (fs/path candidate ".swarmforge" "roles.tsv"))
            candidate
            (exit! 1 "Cannot find SwarmForge project root")))
        (exit! 1 "Cannot find SwarmForge project root")))
    (exit! 1 "Cannot find SwarmForge project root")))

(defn parse-args [args]
  (loop [args args opts {} positionals []]
    (if (empty? args)
      (assoc opts :positional positionals)
      (let [head (first args)
            flag (get flags head)]
        (cond
          (nil? flag)
          (recur (rest args) opts (conj positionals head))

          (nil? (second args))
          (exit! 1 (str "Missing value for " head))

          :else
          (recur (drop 2 args) (assoc opts flag (second args)) positionals))))))

(defn resolve-root [opts]
  (or (:root opts) (project-root)))

(defn board-dir [root]
  (fs/path root ".swarmforge" "board"))

(defn tasks-file [root]
  (fs/path (board-dir root) "tasks.tsv"))

(defn timestamp []
  (.format java.time.format.DateTimeFormatter/ISO_INSTANT
           (java.time.Instant/now)))

(defn read-rows [file]
  (if (fs/exists? file)
    (->> (str/split-lines (slurp (str file)))
         (remove str/blank?)
         vec)
    []))

(defn write-rows [file rows]
  (fs/create-dirs (fs/parent file))
  (spit (str file)
        (if (seq rows)
          (str (str/join "\n" rows) "\n")
          "")))

(defn row-name [line]
  (first (str/split line #"\t")))

(defn find-task [rows name]
  (some #(when (= name (row-name %)) %) rows))

(defn task-row [name lane now]
  (str/join "\t" [name lane now now]))

(defn task-name [opts]
  (or (:name opts) (second (:positional opts))))

(defn task-lane [opts]
  (or (:lane opts) (nth (:positional opts) 2 nil)))

(defn require-value! [value label]
  (when (str/blank? value)
    (exit! 1 (str "Missing " label))))

(defn create! [opts]
  (let [name (task-name opts)
        lane (task-lane opts)
        file (tasks-file (resolve-root opts))]
    (require-value! name "task name")
    (require-value! lane "lane")
    (let [rows (read-rows file)]
      (when (find-task rows name)
        (exit! 1 (str "Duplicate task name: " name)))
      (write-rows file (conj rows (task-row name lane (timestamp)))))))

(defn rewrite-lane [line name lane]
  (let [[row-name _ created] (str/split line #"\t")]
    (if (= name row-name)
      (str/join "\t" [name lane created (timestamp)])
      line)))

(defn set-lane! [opts lane]
  (let [name (task-name opts)
        file (tasks-file (resolve-root opts))]
    (require-value! name "task name")
    (require-value! lane "lane")
    (let [rows (read-rows file)]
      (when-not (find-task rows name)
        (exit! 1 (str "Unknown task name: " name)))
      (write-rows file (mapv #(rewrite-lane % name lane) rows)))))

(defn move! [opts]
  (set-lane! opts (task-lane opts)))

(defn done! [opts]
  (set-lane! opts "done"))

(defn list! [opts]
  (let [file (tasks-file (resolve-root opts))]
    (when (fs/exists? file)
      (print (slurp (str file)))
      (flush))))

(defn -main [& args]
  (let [opts (parse-args args)]
    (case (first (:positional opts))
      "create" (create! opts)
      "move" (move! opts)
      "done" (done! opts)
      "list" (list! opts)
      (do (usage)
          (exit! 1 nil))))
  (System/exit 0))

(apply -main *command-line-args*)
