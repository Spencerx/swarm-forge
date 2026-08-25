#!/usr/bin/env bb

(ns swarm-handoff
  (:require [babashka.fs :as fs]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(def usage-text
  (str "Usage:\n"
       "  swarm_handoff.sh <draft-file>\n"
       "  swarm_handoff.sh --help\n\n"
       "Write the draft under ./tmp/ in the assigned worktree.\n"
       "Do not use /tmp or the handoff outbox as scratch.\n\n"
       "Draft formats:\n\n"
       "type: git_handoff\n"
       "to: <role>[,<role>...]\n"
       "priority: NN\n"
       "task_id: <hidden-task-id>\n"
       "task: <short-stable-task-name>\n\n"
       "The helper fills priority 50, commit, and artifacts from the sender worktree HEAD.\n"
       "Do not type a SHA. Extra headers (coverage, CRAP) are invalid.\n"
       "Extra lines after the headers are ignored.\n\n"
       "type: note\n"
       "to: <role>[,<role>...]\n"
       "priority: NN\n"
       "message: <one line, max 80 chars>"))

(def reserved-fields #{"id" "from" "role" "recipient" "created_at" "enqueued_at"
                       "dequeued_at" "completed_at" "task_base_commit" "non-forwarding"})
(def allowed-fields #{"type" "to" "priority" "task_id" "task" "commit" "message"})
(def allowed-types #{"git_handoff" "note"})

(defn usage []
  (binding [*out* *err*]
    (println usage-text)))

(defn exit! [status message]
  (binding [*out* *err*]
    (when message
      (println message)))
  (System/exit status))

(defn command
  ([dir & args]
   (let [result (apply sh (concat args [:dir (str dir)]))]
     result)))

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

(defn roles-at? [root]
  (and root (fs/exists? (fs/path root ".swarmforge" "roles.tsv"))))

(defn project-root []
  (or (let [common (git-common-dir)
            parent (when common (str (fs/parent common)))]
        (when (roles-at? parent) parent))
      (when (roles-at? (git-root)) (git-root))
      (when (roles-at? (fs/cwd)) (str (fs/cwd)))
      (exit! 1 "Cannot find SwarmForge project root")))

(defn roles-file []
  (fs/path (project-root) ".swarmforge" "roles.tsv"))

(defn role-known? [role]
  (some (fn [line]
          (= role (first (str/split line #"\t"))))
        (str/split-lines (slurp (str (roles-file))))))

(defn same-path? [a b]
  (try
    (= (str (fs/canonicalize a)) (str (fs/canonicalize b)))
    (catch Exception _
      (= (str a) (str b)))))

(defn infer-role-from-worktree []
  (let [here (or (git-root) (str (fs/absolutize ".")))]
    (some (fn [line]
            (let [cols (str/split line #"\t")
                  role (first cols)
                  wt (when (>= (count cols) 3) (nth cols 2))]
              (when (and (not-empty role) (not-empty wt) (same-path? wt here))
                role)))
          (str/split-lines (slurp (str (roles-file)))))))

(defn sender-role []
  (or (not-empty (System/getenv "SWARMFORGE_ROLE"))
      (infer-role-from-worktree)
      (exit! 1 "Set SWARMFORGE_ROLE.")))

(defn board-cards-in-lane [lane]
  (let [file (fs/path (project-root) ".swarmforge" "board" "tasks.tsv")]
    (if (fs/exists? file)
      (into []
            (keep (fn [line]
                    (let [[name task-lane _created _updated task-id] (str/split line #"\t" -1)]
                      (when (= lane task-lane)
                        {:name name :id (or (not-empty task-id) name)}))))
            (str/split-lines (slurp (str file))))
      [])))

(defn in-process-dir []
  (fs/path (System/getProperty "user.dir") ".swarmforge" "handoffs" "inbox" "in_process"))

(defn handoff-files [dir]
  (if (fs/exists? dir)
    (->> (fs/list-dir dir)
         (filter #(and (fs/regular-file? %) (str/ends-with? (fs/file-name %) ".handoff")))
         (sort-by #(fs/file-name %))
         vec)
    []))

(defn batch-dirs [dir]
  (if (fs/exists? dir)
    (->> (fs/list-dir dir)
         (filter #(and (fs/directory? %) (str/starts-with? (fs/file-name %) "batch_")))
         (sort-by #(fs/file-name %))
         vec)
    []))

(defn header-field [file field]
  (let [prefix (str field ": ")]
    (some (fn [line]
            (when (str/starts-with? line prefix)
              (subs line (count prefix))))
          (take-while (complement str/blank?) (str/split-lines (slurp (str file)))))))

(defn handoff-task-id [file]
  (or (not-empty (header-field file "task_id"))
      (header-field file "task")))

(defn top-batch-task []
  (let [batches (batch-dirs (in-process-dir))]
    (when (= 1 (count batches))
      (when-let [file (first (handoff-files (first batches)))]
        (header-field file "task")))))

(defn top-batch-task-id []
  (let [batches (batch-dirs (in-process-dir))]
    (when (= 1 (count batches))
      (when-let [file (first (handoff-files (first batches)))]
        (handoff-task-id file)))))

(defn in-process-task-files []
  (into (handoff-files (in-process-dir))
        (mapcat handoff-files (batch-dirs (in-process-dir)))))

(defn current-in-process-task-id []
  (when-let [file (first (in-process-task-files))]
    (handoff-task-id file)))

(defn current-task-base []
  (when-let [file (first (in-process-task-files))]
    (header-field file "task_base_commit")))

(defn with-lane-task [headers sender]
  (let [cards (board-cards-in-lane sender)
        drafted-id (get headers "task_id")
        drafted (get headers "task")]
    (cond
      (some #(= drafted-id (:id %)) cards) headers
      (not (str/blank? drafted-id)) headers
      (some #(= drafted (:name %)) cards)
      (let [card (first (filter #(= drafted (:name %)) cards))]
        (assoc headers "task_id" (:id card) "task" (:name card)))
      (= 1 (count cards))
      (let [card (first cards)]
        (assoc headers "task_id" (:id card) "task" (:name card)))
      :else headers)))

(defn with-board-task [headers sender]
  (if-not (= "git_handoff" (get headers "type"))
    headers
    (if-let [batch-task-id (not-empty (top-batch-task-id))]
      (cond-> (assoc headers "task_id" batch-task-id)
        (not-empty (top-batch-task)) (assoc "task" (top-batch-task)))
      (with-lane-task headers sender))))

(defn pack-role-names []
  (->> (str/split-lines (slurp (str (roles-file))))
       (remove str/blank?)
       (map #(first (str/split % #"\t")))
       vec))

(defn last-pack-role? [role]
  (= role (last (pack-role-names))))

(defn with-non-forwarding [headers sender]
  (if (and (= "git_handoff" (get headers "type"))
           (last-pack-role? sender))
    (assoc headers "non-forwarding" "true")
    headers))

(defn inbound-handoffs []
  (in-process-task-files))

(defn inbound-non-forwarding? []
  (boolean (some #(= "true" (header-field % "non-forwarding"))
                 (inbound-handoffs))))

(defn role-worktree [role]
  (some (fn [line]
          (let [cols (str/split line #"\t")]
            (when (and (= role (first cols)) (>= (count cols) 3))
              (nth cols 2))))
        (str/split-lines (slurp (str (roles-file))))))

(defn git-cwd []
  (or (not-empty (role-worktree (sender-role)))
      (git-root)
      "."))

(defn worktree-head []
  (let [result (command (git-cwd) "git" "rev-parse" "--short=10" "HEAD")]
    (when-not (zero? (:exit result))
      (exit! 1 "Cannot read HEAD commit."))
    (str/trim (:out result))))

(defn under-dir? [file dir]
  (let [file (str (fs/canonicalize file))
        dir (str (fs/canonicalize dir))]
    (str/starts-with? file (str dir "/"))))

(defn worktree-tmp []
  (let [dir (fs/path (git-cwd) "tmp")]
    (fs/create-dirs dir)
    dir))

(defn require-worktree-tmp-draft! [draft]
  (when-not (under-dir? draft (worktree-tmp))
    (exit! 1 (str "Draft must live in ./tmp/ in the assigned worktree; got " draft))))

(defn commit-on-sender-branch? [sha]
  (zero? (:exit (command (git-cwd) "git" "merge-base" "--is-ancestor" sha "HEAD"))))

(defn commit-descends-from? [base sha]
  (zero? (:exit (command (git-cwd) "git" "merge-base" "--is-ancestor" base sha))))

(defn named-files [result]
  (->> (:out result)
       str/split-lines
       (remove str/blank?)
       distinct
       vec))

(defn commit-artifacts [sha]
  (if-let [base (not-empty (current-task-base))]
    (named-files (command (git-cwd) "git" "diff" "--name-only" "--diff-filter=ACMRT" base sha))
    (let [against-parent (command (git-cwd) "git" "diff" "--name-only" "--diff-filter=ACMRT" (str sha "^") sha)]
      (if (zero? (:exit against-parent))
        (named-files against-parent)
        (named-files (command (git-cwd) "git" "diff-tree" "--root"
                              "--no-commit-id" "--name-only" "--diff-filter=ACMRT" "-r" sha))))))

(defn state-dir []
  (fs/path (project-root) ".swarmforge" "handoffs"))

(defn timestamp []
  (.format java.time.format.DateTimeFormatter/ISO_INSTANT
           (java.time.Instant/now)))

(defn id-timestamp []
  (.format (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd'T'HHmmss'Z'")
           (.atZone (java.time.Instant/now) java.time.ZoneOffset/UTC)))

(defn valid-priority? [priority]
  (boolean (and priority (re-matches #"[0-9][0-9]" priority))))

(defn fill-commit [headers]
  (if (= "git_handoff" (get headers "type"))
    (assoc headers "commit" (worktree-head))
    headers))

(defn fill-priority [headers]
  (if (valid-priority? (get headers "priority"))
    headers
    (assoc headers "priority" "50")))

(defn fill-task-id [headers]
  (if (and (= "git_handoff" (get headers "type"))
           (str/blank? (get headers "task_id"))
           (not (str/blank? (get headers "task"))))
    (assoc headers "task_id" (get headers "task"))
    headers))

(defn prepare-headers [headers sender]
  (-> headers
      fill-commit
      (with-board-task sender)
      fill-task-id
      (with-non-forwarding sender)
      fill-priority))

(defn state-root []
  (fs/path (project-root) ".swarmforge"))

(defn board-rows []
  (let [file (fs/path (state-root) "board" "tasks.tsv")]
    (if (fs/exists? file)
      (->> (str/split-lines (slurp (str file)))
           (remove str/blank?)
           (map #(let [[name lane _created _updated task-id] (str/split % #"\t" -1)]
                   {:name name :lane lane :id (or (not-empty task-id) name)}))
           vec)
      [])))

(defn board-present? []
  (fs/exists? (fs/path (state-root) "board" "tasks.tsv")))

(defn board-task [task-id]
  (some #(when (= task-id (:id %)) %) (board-rows)))

(defn rejected-task? [task]
  (let [name (:name task)]
    (and (not (str/blank? name))
         (fs/exists? (fs/path (state-root) "notify" (str "reject-" name))))))

(defn task-state-errors [headers sender]
  (if-not (= "git_handoff" (get headers "type"))
    []
    (let [task-id (or (not-empty (get headers "task_id"))
                      (get headers "task"))
          in-process-id (current-in-process-task-id)
          task (board-task task-id)]
      (cond-> []
        (str/blank? task-id)
        (conj "Missing required header 'task_id' for git_handoff.")
        (and in-process-id (not= task-id in-process-id))
        (conj (format "Handoff task_id '%s' does not match current in-process task_id '%s'."
                      task-id in-process-id))
        (and (board-present?) (nil? in-process-id) (not task))
        (conj (format "Handoff task_id '%s' is not a current board task." task-id))
        (and task (= "done" (:lane task)))
        (conj (format "Task '%s' is done and cannot accept new handoffs." (:name task)))
        (rejected-task? task)
        (conj (format "Task '%s' is rejected and must be retried before handoff." (:name task)))))))

(def active-states
  [["pending approvals" (fn [] [(fs/path (state-dir) "pending_approval")])]
   ["sent" (fn []
             (concat [(fs/path (state-dir) "sent")]
                     (for [line (str/split-lines (slurp (str (roles-file))))
                           :let [cols (str/split line #"\t" -1)
                                 wt (nth cols 2 nil)]
                           :when (not (str/blank? wt))]
                       (fs/path wt ".swarmforge" "handoffs" "sent"))))]
   ["recipient inbox" (fn []
                        (for [line (str/split-lines (slurp (str (roles-file))))
                              :let [cols (str/split line #"\t" -1)
                                    wt (nth cols 2 nil)]
                              :when (not (str/blank? wt))
                              state ["new" "in_process"]]
                          (fs/path wt ".swarmforge" "handoffs" "inbox" state)))]] )

(defn recursive-handoff-files [dir]
  (if (fs/directory? dir)
    (->> (concat (fs/glob dir "*.handoff")
                 (fs/glob dir "**/*.handoff"))
         (filter fs/regular-file?)
         distinct
         vec)
    []))

(defn header-map [file]
  (into {}
        (for [line (take-while (complement str/blank?) (str/split-lines (slurp (str file))))
              :let [[k v] (str/split line #": " 2)]
              :when (and k v)]
          [k v])))

(defn same-active-handoff? [sender recipients headers canonical-commit path]
  (let [h (header-map path)
        task-id (or (not-empty (get headers "task_id")) (get headers "task"))
        other-id (or (not-empty (get h "task_id")) (get h "task"))]
    (and (= sender (get h "from"))
         (= (set recipients) (set (str/split (or (get h "to") "") #",")))
         (= task-id other-id)
         (= canonical-commit (get h "commit")))))

(defn duplicate-errors [sender recipients headers canonical-commit]
  (if-not (= "git_handoff" (get headers "type"))
    []
    (let [matches (for [[label dirs-fn] active-states
                        dir (dirs-fn)
                        file (recursive-handoff-files dir)
                        :when (same-active-handoff? sender recipients headers canonical-commit file)]
                    (str label ": " file))]
      (if (seq matches)
        [(str "Duplicate active handoff for same from/to/task_id/commit exists: "
              (str/join ", " matches))]
        []))))

(defn ancestry-errors [headers canonical-commit]
  (if-not (= "git_handoff" (get headers "type"))
    []
    (let [base (current-task-base)]
      (cond-> []
        (and (not (str/blank? base))
             (not (str/blank? canonical-commit))
             (not (commit-descends-from? base canonical-commit)))
        (conj (format "Result commit %s is not a descendant of task base %s."
                      canonical-commit base))))))

(defn ensure-field [ordered field]
  (if (some #{field} ordered)
    ordered
    (conj (vec ordered) field)))

(defn parse-draft [draft]
  (loop [lines (str/split-lines (slurp (str draft)))
         line-no 0
         body-seen? false
         headers {}
         ordered []
         errors []]
    (if-let [line (first lines)]
      (let [line-no (inc line-no)]
        (cond
          (or body-seen? (str/blank? line) (not (str/includes? line ": ")))
          (recur (next lines) line-no true headers ordered errors)

          :else
          (let [[field value] (str/split line #": " 2)]
            (cond
              (or (str/blank? field) (str/blank? value))
              (recur (next lines) line-no body-seen? headers ordered
                     (conj errors (format "Line %d: field and value must both be non-empty." line-no)))

              (reserved-fields field)
              (recur (next lines) line-no body-seen? headers ordered
                     (conj errors (format "Line %d: header '%s' is reserved and must not be written by agents." line-no field)))

              (not (allowed-fields field))
              (recur (next lines) line-no body-seen? headers ordered
                     (conj errors (format "Line %d: unknown header '%s'." line-no field)))

              (contains? headers field)
              (recur (next lines) line-no body-seen? headers ordered
                     (conj errors (format "Line %d: duplicate header '%s'." line-no field)))

              :else
              (recur (next lines) line-no body-seen? (assoc headers field value) (conj ordered field) errors)))))
      {:headers headers :ordered ordered :errors errors})))

(defn validate-recipients [to]
  (if (str/blank? to)
    [[] []]
    (let [recipients (str/split to #"," -1)]
      [recipients
       (loop [remaining recipients seen #{} errors []]
         (if-let [recipient (first remaining)]
           (let [errors (cond-> errors
                          (str/blank? recipient)
                          (conj "Header 'to' contains an empty recipient.")
                          (str/includes? recipient "_")
                          (conj (format "Recipient role '%s' is invalid; role names may not contain underscores." recipient))
                          (contains? seen recipient)
                          (conj (format "Duplicate recipient '%s'." recipient))
                          (and (not (str/blank? recipient)) (not (role-known? recipient)))
                          (conj (format "Unknown recipient role '%s'." recipient)))]
             (recur (next remaining) (conj seen recipient) errors))
           errors))])))

(defn canonical-commit [commit]
  (let [dir (git-cwd)
        matches (-> (command dir "git" "rev-parse" (str "--disambiguate=" commit))
                    :out
                    str/split-lines
                    vec)]
    (cond
      (not= 1 (count matches))
      [nil (format "Header 'commit' must resolve to exactly one Git object; '%s' matched %d." commit (count matches))]

      :else
      (let [object (first matches)
            object-type (str/trim (:out (command dir "git" "cat-file" "-t" object)))]
        (if (= "commit" object-type)
          [(str/trim (:out (command dir "git" "rev-parse" "--short=10" object))) nil]
          [nil (format "Header 'commit' must resolve to a commit; '%s' resolves to '%s'." commit object-type)])))))

(defn validate [headers ordered]
  (let [type (get headers "type")
        to (get headers "to")
        priority (get headers "priority")
        commit (get headers "commit")
        task-name (get headers "task")
        note-message (get headers "message")
        [recipients recipient-errors] (validate-recipients to)
        field-errors (for [field ordered
                           :let [valid? (case [type field]
                                          ["git_handoff" "type"] true
                                          ["git_handoff" "to"] true
                                          ["git_handoff" "priority"] true
                                          ["git_handoff" "task_id"] true
                                          ["git_handoff" "task"] true
                                          ["git_handoff" "commit"] true
                                          ["note" "type"] true
                                          ["note" "to"] true
                                          ["note" "priority"] true
                                          ["note" "message"] true
                                          false)]
                           :when (and type (not valid?))]
                       (format "Header '%s' is not allowed for type '%s'." field type))
        base-errors (cond-> []
                      (str/blank? type) (conj "Missing required header 'type'.")
                      (str/blank? to) (conj "Missing required header 'to'.")
                      (str/blank? priority) (conj "Missing required header 'priority'.")
                      (and (not (str/blank? type)) (not (allowed-types type)))
                      (conj (format "Header 'type' must be one of git_handoff or note; got '%s'." type))
                      (and (not (str/blank? priority)) (not (valid-priority? priority)))
                      (conj (format "Header 'priority' must be two digits from 00 to 99; got '%s'." priority)))
        [canonical commit-error]
        (if (= "git_handoff" type)
          (cond
            (str/blank? commit) [nil "Missing required header 'commit' for git_handoff."]
            (not (re-matches #"[0-9a-fA-F]{10}" commit))
            [nil (format "Header 'commit' must be exactly 10 hexadecimal characters; got '%s'." commit)]
            :else (canonical-commit commit))
          [nil nil])
        git-errors (cond-> []
                     (= "git_handoff" type)
                     (into (cond-> []
                             (str/blank? (get headers "task_id"))
                             (conj "Missing required header 'task_id' for git_handoff.")
                             (str/blank? task-name)
                             (conj "Missing required header 'task' for git_handoff.")
                             (> (count (or task-name "")) 80)
                             (conj (format "Header 'task' must be no longer than 80 characters; got %d." (count task-name)))))
                     (and (not= "git_handoff" type) (not (str/blank? commit)))
                     (conj "Header 'commit' is only allowed for git_handoff.")
                     (and (not= "git_handoff" type) (not (str/blank? task-name)))
                     (conj "Header 'task' is only allowed for git_handoff.")
                     commit-error
                     (conj commit-error))
        note-errors (cond-> []
                      (= "note" type)
                      (into (cond-> []
                              (str/blank? note-message)
                              (conj "Missing required header 'message' for note.")
                              (> (count (or note-message "")) 80)
                              (conj (format "Header 'message' must be no longer than 80 characters; got %d." (count note-message)))))
                      (and (not= "note" type) (not (str/blank? note-message)))
                      (conj "Header 'message' is only allowed for note."))]
    {:recipients recipients
     :canonical-commit canonical
     :errors (vec (concat base-errors recipient-errors field-errors git-errors note-errors))}))

(defn next-sequence []
  (let [dir (state-dir)
        seq-file (fs/path dir "sequence")
        lock-dir (fs/path dir "sequence.lock")]
    (fs/create-dirs dir)
    (loop []
      (if (try
            (fs/create-dir lock-dir)
            true
            (catch java.nio.file.FileAlreadyExistsException _
              false))
        nil
        (do
          (Thread/sleep 50)
          (recur))))
    (try
      (let [last-value (if (fs/exists? seq-file)
                         (try
                           (Long/parseLong (str/trim (slurp (str seq-file))))
                           (catch Exception _ 0))
                         0)
            next-value (inc last-value)
            formatted (format "%06d" next-value)]
        (spit (str seq-file) (str formatted "\n"))
        formatted)
      (finally
        (fs/delete lock-dir)))))

(defn body [type sender canonical-commit note-message]
  (case type
    "git_handoff" (str "Re-read your role and constitution.\n\nmerge_and_process.sh " sender " " canonical-commit)
    "note" (str "Re-read your role and constitution.\n\n" note-message)))

(defn write-handoff! [{:keys [headers recipients canonical-commit artifacts sender]}]
  (let [timestamp-id (id-timestamp)
        created-at (timestamp)
        sequence (next-sequence)
        id (str timestamp-id "_" sequence "_from_" sender)
        recipient-slug (str/join "_" recipients)
        priority (get headers "priority")
        type (get headers "type")
        filename (str priority "_" timestamp-id "_" sequence "_from_" sender "_to_" recipient-slug ".handoff")
        outbox-dir (fs/path (state-dir) "outbox")
        tmp-dir (fs/path outbox-dir "tmp")
        tmp-file (fs/path tmp-dir (str filename ".tmp"))
        outbox-file (fs/path outbox-dir filename)
        handoff-body (body type sender canonical-commit (get headers "message"))
        lines (cond-> [(str "id: " id)
                       (str "from: " sender)
                       (str "to: " (str/join "," recipients))
                       (str "priority: " priority)
                       (str "type: " type)]
                (= "git_handoff" type)
                (conj (str "role: " sender)
                      (str "task_id: " (get headers "task_id"))
                      (str "task: " (get headers "task"))
                      (str "commit: " canonical-commit)
                      (str "artifacts: " artifacts))
                (and (= "git_handoff" type) (not (str/blank? (current-task-base))))
                (conj (str "task_base_commit: " (current-task-base)))
                (= "true" (get headers "non-forwarding"))
                (conj "non-forwarding: true")
                (= "note" type)
                (conj (str "message: " (get headers "message")))
                true
                (conj (str "created_at: " created-at)
                      ""
                      handoff-body))]
    (doseq [dir [tmp-dir outbox-dir (fs/path (state-dir) "sent") (fs/path (state-dir) "failed")]]
      (fs/create-dirs dir))
    (spit (str tmp-file) (str (str/join "\n" lines) "\n"))
    (fs/move tmp-file outbox-file)
    outbox-file))

(defn error-report [draft errors]
  (binding [*out* *err*]
    (println "HANDOFF INVALID:" (str draft))
    (println)
    (println "Errors:")
    (doseq [error errors]
      (println "-" error))
    (println)
    (println usage-text)))

(defn help-arg? [args]
  (boolean (some #{"--help" "-h"} args)))

(defn -main [& args]
  (when (help-arg? args)
    (usage)
    (System/exit 0))
  (when (not= 1 (count args))
    (usage)
    (System/exit 1))
  (let [draft (fs/path (first args))]
    (when-not (fs/regular-file? draft)
      (exit! 1 (str "Draft file not found: " draft)))
    (let [sender (sender-role)]
      (when-not (role-known? sender)
        (exit! 1 (str "Unknown sender role: " sender)))
      (require-worktree-tmp-draft! draft)
      (let [{:keys [headers ordered errors]} (parse-draft draft)
            headers (prepare-headers headers sender)
            ordered (-> ordered
                        (ensure-field "priority")
                        (cond-> (= "git_handoff" (get headers "type"))
                          (ensure-field "commit")))
            sha (get headers "commit")]
        (when (and (= "git_handoff" (get headers "type"))
                   (inbound-non-forwarding?))
          (exit! 1 "Current inbound handoff is non-forwarding; do not send a git_handoff."))
        (when (and (= "git_handoff" (get headers "type"))
                   (not (commit-on-sender-branch? sha)))
          (exit! 1 (str "Result commit " sha " is not reachable from sender worktree")))
        (let [validation (validate headers ordered)
              all-errors (vec (concat errors
                                      (:errors validation)
                                      (task-state-errors headers sender)
                                      (ancestry-errors headers (:canonical-commit validation))
                                      (duplicate-errors sender
                                                        (:recipients validation)
                                                        headers
                                                        (:canonical-commit validation))))]
          (when (seq all-errors)
            (error-report draft all-errors)
            (System/exit 2))
          (let [files (when (= "git_handoff" (get headers "type"))
                        (commit-artifacts sha))]
            (when (and (= "git_handoff" (get headers "type")) (empty? files))
              (exit! 1 (str "Result commit " sha " has no changed files")))
            (let [outbox-file (write-handoff! {:headers headers
                                               :recipients (:recipients validation)
                                               :canonical-commit (:canonical-commit validation)
                                               :artifacts (when files (str/join "," files))
                                               :sender sender})]
              (fs/delete draft)
              (println "HANDOFF QUEUED:" (str outbox-file)))))))))

(apply -main *command-line-args*)
