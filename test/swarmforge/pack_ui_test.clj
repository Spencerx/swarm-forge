(ns swarmforge.pack-ui-test
  (:require [babashka.fs :as fs]
            [clojure.java.shell :as sh]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests use-fixtures]]))

(def repo-root (fs/cwd))
(def scripts-dir (fs/path repo-root "swarmforge" "scripts"))
(def temp-dirs (atom []))

(use-fixtures :once
  (fn [tests]
    (try
      (tests)
      (finally
        (doseq [dir @temp-dirs]
          (fs/delete-tree dir))))))

(defn script [name]
  (str (fs/path scripts-dir name)))

(defn tmp-dir []
  (let [dir (fs/create-temp-dir {:prefix "swarmforge-pack-ui-test."})]
    (swap! temp-dirs conj dir)
    dir))

(defn run
  [{:keys [dir env ok?]} & args]
  (let [result (apply sh/sh (concat args [:dir (str dir)
                                          :env (merge {"PATH" (System/getenv "PATH")
                                                       "GIT_CONFIG_NOSYSTEM" "1"}
                                                      env)]))]
    (when (and (not (false? ok?)) (not= 0 (:exit result)))
      (throw (ex-info (str "Command failed: " (str/join " " args))
                      (assoc result :args args))))
    result))

(defn write-file [path text]
  (fs/create-dirs (fs/parent path))
  (spit (str path) text))

(defn setup-pack!
  ([root] (setup-pack! root ["specifier"]))
  ([root roles]
   (write-file
    (fs/path root ".swarmforge/roles.tsv")
    (apply str
           (for [role roles]
             (format "%s\t%s\t%s\t%s\t%s\tcodex\ttask\n"
                     role role root role (str/capitalize role)))))
   (doseq [dir [".swarmforge/handoffs/outbox"
                ".swarmforge/handoffs/sent"
                ".swarmforge/handoffs/failed"
                ".swarmforge/handoffs/inbox/new"]]
     (fs/create-dirs (fs/path root dir)))))

(defn pack-board
  ([root ok? & args]
   (apply run {:dir root :ok? ok?} (script "pack_board.sh") args)))

(defn create-task
  ([root name lane] (create-task root name lane true))
  ([root name lane ok?]
   (pack-board root ok?
               "create"
               "--root" (str root)
               "--name" name
               "--lane" lane
               "--text" "Integrate HTW stories")))

(defn list-tasks [root]
  (pack-board root true "list" "--root" (str root)))

(defn task-row [listed name]
  (some #(when (str/starts-with? % (str name "\t")) %)
        (str/split-lines listed)))

(defn task-lane [root name]
  (let [cols (str/split (or (task-row (:out (list-tasks root)) name) "") #"\t")]
    (nth cols 1 nil)))

(defn queue-handoff! [root {:keys [from to task]}]
  (write-file
   (fs/path root ".swarmforge/handoffs/outbox"
            (str "50_from_" from "_to_" (str/replace to #"," "_") ".handoff"))
   (str "from: " from "\n"
        "to: " to "\n"
        "priority: 50\n"
        "type: git_handoff\n"
        "task: " task "\n"
        "\n"
        "payload\n")))

(defn start-tmux! [root sessions]
  (let [sock (str (fs/path root "tmux.sock"))]
    (write-file (fs/path root ".swarmforge/tmux-socket") (str sock "\n"))
    (doseq [session sessions]
      (run {:dir root} "tmux" "-S" sock "new-session" "-d" "-s" session "sleep" "120"))
    sock))

(defn stop-tmux! [sock]
  (run {:dir "." :ok? false} "tmux" "-S" sock "kill-server"))

(defn handoffd-once [root]
  (run {:dir root} "bb" (script "handoffd.bb") "--once" (str root)))

(deftest pack-board-creates-a-task-in-the-master-lane
  ;; Given a pack with specifier on master
  ;; When New Task records name htw-console-app
  ;; Then the card sits in lane specifier
  (let [root (tmp-dir)
        _ (setup-pack! root)
        created (create-task root "htw-console-app" "specifier")
        listed (:out (list-tasks root))
        on-disk (slurp (str (fs/path root ".swarmforge/board/tasks.tsv")))
        cols (str/split (or (task-row listed "htw-console-app") "") #"\t")]
    (is (zero? (:exit created)))
    (is (= listed on-disk))
    (is (= "htw-console-app" (nth cols 0 nil)))
    (is (= "specifier" (nth cols 1 nil)))
    (is (re-matches #"\d{4}-\d{2}-\d{2}T.*Z" (nth cols 2 "")))
    (is (= (nth cols 2 nil) (nth cols 3 nil)))))

(deftest new-task-writes-the-card-and-body
  ;; Given specifier is master
  ;; When create name=htw-console-app text="Integrate HTW stories…"
  ;; Then lane is specifier AND board/htw-console-app.txt has the text
  (let [root (tmp-dir)
        text "Integrate HTW stories…"]
    (write-file
     (fs/path root ".swarmforge/roles.tsv")
     (str "specifier\tmaster\t" root "\tsession\tSpecifier\tcodex\ttask\n"))
    (let [created (pack-board root true
                              "create"
                              "--root" (str root)
                              "--name" "htw-console-app"
                              "--lane" "specifier"
                              "--text" text)
          body (slurp (str (fs/path root ".swarmforge/board/htw-console-app.txt")))]
      (is (zero? (:exit created)))
      (is (= "specifier" (task-lane root "htw-console-app")))
      (is (= text body)))))

(deftest pack-board-lists-lanes-in-role-order
  ;; Given roles specifier, coder, QA
  ;; When pack_board lanes
  ;; Then it prints those roles in conf order
  (let [root (tmp-dir)
        _ (setup-pack! root ["specifier" "coder" "QA"])
        result (pack-board root true "lanes" "--root" (str root))]
    (is (= "specifier\ncoder\nQA\n" (:out result)))))

(deftest pack-board-reports-the-master-lane
  ;; Given specifier's worktree is master
  ;; When pack_board master-lane
  ;; Then it prints specifier
  (let [root (tmp-dir)]
    (write-file
     (fs/path root ".swarmforge/roles.tsv")
     (str "specifier\tmaster\t" root "\tsession\tSpecifier\tcodex\ttask\n"
          "coder\tcoder\t" root "/.worktrees/coder\tsession\tCoder\tcodex\ttask\n"))
    (let [result (pack-board root true "master-lane" "--root" (str root))]
      (is (= "specifier\n" (:out result))))))

(deftest pack-board-rejects-a-duplicate-task-name
  ;; Given a card named htw-console-app
  ;; When New Task records the same name again
  ;; Then the create is rejected and the original card is unchanged
  (let [root (tmp-dir)
        _ (setup-pack! root)
        _ (create-task root "htw-console-app" "specifier")
        before (:out (list-tasks root))
        duplicate (create-task root "htw-console-app" "specifier" false)
        after (:out (list-tasks root))]
    (is (not (zero? (:exit duplicate))))
    (is (str/includes? (str (:err duplicate) (:out duplicate)) "Duplicate"))
    (is (= before after))))

(deftest handoffd-moves-the-task-card-to-the-recipient
  ;; Given card htw-console-app in specifier
  ;; When a git_handoff specifier→coder for that task is delivered
  ;; Then the card lane is coder
  (let [root (tmp-dir)
        roles ["specifier" "coder"]
        sock (do (setup-pack! root roles)
                 (create-task root "htw-console-app" "specifier")
                 (queue-handoff! root {:from "specifier" :to "coder" :task "htw-console-app"})
                 (start-tmux! root roles))]
    (try
      (handoffd-once root)
      (is (= "coder" (task-lane root "htw-console-app")))
      (finally
        (stop-tmux! sock)))))

(deftest handoffd-marks-the-task-card-done-for-multi-recipient-handoff
  ;; Given card htw-console-app in QA
  ;; When a git_handoff QA→specifier,coder,cleaner,architect,hardender is delivered
  ;; Then the card lane is done
  (let [root (tmp-dir)
        roles ["QA" "specifier" "coder" "cleaner" "architect" "hardender"]
        to "specifier,coder,cleaner,architect,hardender"
        sock (do (setup-pack! root roles)
                 (create-task root "htw-console-app" "QA")
                 (queue-handoff! root {:from "QA" :to to :task "htw-console-app"})
                 (start-tmux! root roles))]
    (try
      (handoffd-once root)
      (is (= "done" (task-lane root "htw-console-app")))
      (finally
        (stop-tmux! sock)))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'swarmforge.pack-ui-test)]
    (System/exit (+ fail error))))
