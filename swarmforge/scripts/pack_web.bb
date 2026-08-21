#!/usr/bin/env bb

(ns pack-web
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(def script-dir (fs/parent *file*))

(def usage-text
  (str "Usage:\n"
       "  pack_web.sh --test-state <root>\n"
       "  pack_web.sh --test-html\n"
       "  pack_web.sh --test-post-task <root> <name> <text>\n"
       "  pack_web.sh --test-post-chat <root> <text>\n"
       "  pack_web.sh --test-inject-payload [name text]\n"
       "  pack_web.sh --test-inject-argv <root> <file> <text>\n"
       "  pack_web.sh --test-approve <root> <id>\n"
       "  pack_web.sh --test-reject <root> <id>"))

(def example-task-name "htw-console-app")
(def example-task-text
  "Integrate the stories in ~/junk/htw-stories into one console application.")

(def ^:dynamic *tmux-stub* nil)

(defn usage []
  (binding [*out* *err*]
    (println usage-text)))

(defn exit! [status message]
  (binding [*out* *err*]
    (when message
      (println message)))
  (System/exit status))

(defn display-name-for-role [role]
  (->> (str/split (str/replace role #"[-_]" " ") #"\s+")
       (remove str/blank?)
       (map str/capitalize)
       (str/join " ")))

(defn task-payload
  ([] (task-payload example-task-name example-task-text))
  ([name text] (str "Task: " name "\n\n" (or text ""))))

(defn reject-message [task]
  (str "Rejected: " task))

(defn tmux-stub []
  (or *tmux-stub* (System/getenv "SWARMFORGE_TMUX_STUB")))

(defn record-argv! [file argv]
  (when-let [dir (fs/parent file)]
    (fs/create-dirs dir))
  (spit (str file) (str (pr-str (vec argv)) "\n") :append true))

(defn send-keys! [socket session & keys]
  (let [argv (into ["tmux" "-S" socket "send-keys" "-t" session] keys)]
    (if-let [stub (tmux-stub)]
      (record-argv! stub argv)
      (let [result (apply sh argv)]
        (when-not (zero? (:exit result))
          (throw (ex-info "tmux send-keys failed" result)))))))

(defn role-rows [root]
  (let [file (fs/path root ".swarmforge" "roles.tsv")]
    (if (fs/exists? file)
      (->> (str/split-lines (slurp (str file)))
           (remove str/blank?)
           (mapv #(str/split % #"\t" -1)))
      [])))

(defn master-session [root]
  (when-let [row (some #(when (= "master" (nth % 1 nil)) %) (role-rows root))]
    (let [session (nth row 3 nil)
          role (first row)]
      (if (str/blank? session)
        (str "swarmforge-" role)
        session))))

(defn tmux-socket [root]
  (let [file (fs/path root ".swarmforge" "tmux-socket")]
    (when (fs/exists? file)
      (not-empty (str/trim (slurp (str file)))))))

(defn inject-master! [root text]
  (try
    (let [socket (tmux-socket root)
          session (master-session root)]
      (when (and socket session (not (str/blank? text)))
        (send-keys! socket session "-l" text)
        (when-not (tmux-stub)
          (Thread/sleep 150))
        (send-keys! socket session "C-m")))
    (catch Exception _)))

(defn pack-board [root & args]
  (let [script (str (fs/path script-dir "pack_board.sh"))
        result (apply sh (concat [script] args ["--root" (str root)]))]
    (when-not (zero? (:exit result))
      (exit! 1 (str/trim (str (:err result) "\n" (:out result)))))
    (:out result)))

(defn lines [text]
  (->> (str/split-lines (or text ""))
       (remove str/blank?)
       vec))

(defn lanes [root]
  (lines (pack-board root "lanes")))

(defn master-role [root]
  (str/trim (pack-board root "master-lane")))

(defn task-entry [line]
  (let [[name lane _created updated] (str/split line #"\t")]
    {:name name :lane lane :updated_at updated}))

(defn tasks [root]
  (mapv task-entry (lines (pack-board root "list"))))

(defn parse-message [path]
  (let [content (slurp (str path))
        [header body] (str/split content #"\n\n" 2)
        headers (into {}
                      (for [line (str/split-lines header)
                            :let [[k v] (str/split line #": " 2)]
                            :when (and k v)]
                        [k v]))]
    {:headers headers :body (or body "")}))

(defn comma-list [text]
  (->> (str/split (or text "") #",")
       (map str/trim)
       (remove str/blank?)
       vec))

(defn pending-dir [root]
  (fs/path root ".swarmforge" "handoffs" "pending_approval"))

(defn pending-files [root]
  (let [dir (pending-dir root)]
    (if (fs/directory? dir)
      (->> (fs/list-dir dir)
           (filter #(and (fs/regular-file? %)
                         (str/ends-with? (fs/file-name %) ".handoff")))
           (sort-by #(fs/file-name %)))
      [])))

(defn approval-id [path]
  (str/replace (fs/file-name path) #"\.handoff$" ""))

(defn approval-entry [path]
  (let [headers (:headers (parse-message path))
        to (first (comma-list (get headers "to")))]
    {:id (approval-id path)
     :gate (str "spec → " to)
     :task (get headers "task")
     :artifacts (comma-list (get headers "artifacts"))}))

(defn approvals [root]
  (mapv approval-entry (pending-files root)))

(defn dashboard-state [root]
  (let [master (master-role root)]
    {:master_role master
     :master_display (display-name-for-role master)
     :lanes (lanes root)
     :tasks (tasks root)
     :approvals (approvals root)
     :work_in_flight []}))

(defn require-root! [root]
  (when (str/blank? root)
    (exit! 1 "Missing project root"))
  root)

(defn dashboard-page []
  (slurp (str (fs/path script-dir "pack" "dashboard.html"))))

(defn create-task! [root name text]
  (when (str/blank? name)
    (exit! 1 "Missing task name"))
  (pack-board root "create"
              "--name" name
              "--lane" (master-role root)
              "--text" (or text ""))
  (inject-master! root (task-payload name (or text ""))))

(defn json-ok []
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string {:ok true})})

(defn post-tasks [root body]
  (let [{:keys [name text]} (json/parse-string (or body "{}") true)]
    (create-task! root name text)
    (json-ok)))

(defn post-chat [root body]
  (let [{:keys [text]} (json/parse-string (or body "{}") true)]
    (inject-master! root (or text ""))
    (json-ok)))

(defn pending-file [root id]
  (fs/path (pending-dir root) (str id ".handoff")))

(defn require-pending! [root id]
  (let [path (pending-file root id)]
    (when-not (fs/regular-file? path)
      (exit! 1 (str "Unknown approval: " id)))
    path))

(defn with-approved [content]
  (if (re-find #"(?m)^approved: " content)
    content
    (str/replace-first content #"\n\n" "\napproved: true\n\n")))

(defn approve! [root id]
  (let [src (require-pending! root id)
        dest (fs/path root ".swarmforge" "handoffs" "outbox" (fs/file-name src))]
    (fs/create-dirs (fs/parent dest))
    (spit (str dest) (with-approved (slurp (str src))))
    (fs/delete-if-exists src)))

(defn write-reject-notify! [root task]
  (when-not (str/blank? task)
    (let [path (fs/path root ".swarmforge" "notify" (str "reject-" task))]
      (fs/create-dirs (fs/parent path))
      (spit (str path) "rejected\n"))))

(defn reject! [root id]
  (let [src (require-pending! root id)
        task (get-in (parse-message src) [:headers "task"])]
    (fs/delete-if-exists src)
    (write-reject-notify! root task)
    (when-not (str/blank? task)
      (inject-master! root (reject-message task)))))

(defn approval-route [uri]
  (let [path (first (str/split (or uri "") #"\?"))]
    (when-let [[_ id action] (re-matches #"/api/approvals/([^/]+)/(approve|reject)" path)]
      {:id (java.net.URLDecoder/decode id "UTF-8")
       :action action})))

(defn post-approval [root uri]
  (if-let [{:keys [id action]} (approval-route uri)]
    (do (if (= "approve" action)
          (approve! root id)
          (reject! root id))
        (json-ok))
    {:status 404 :body "Not found"}))

(defn query-value [uri key]
  (when-let [q (second (str/split (or uri "") #"\?" 2))]
    (some (fn [pair]
            (let [[k v] (str/split pair #"=" 2)]
              (when (= k key)
                (java.net.URLDecoder/decode (or v "") "UTF-8"))))
          (str/split q #"&"))))

(defn existing-path [root rel]
  (let [path (fs/path root rel)]
    (when (fs/exists? path)
      (fs/canonicalize path))))

(defn under-dir? [file dir]
  (and file dir (fs/starts-with? file dir)))

(defn allowed-doc? [root rel]
  (when-not (str/blank? rel)
    (let [file (existing-path root rel)]
      (and (some? file)
           (fs/regular-file? file)
           (or (under-dir? file (existing-path root "features"))
               (under-dir? file (existing-path root "qa")))))))

(defn get-doc [root uri]
  (let [rel (query-value uri "path")]
    (if (allowed-doc? root rel)
      {:status 200
       :headers {"Content-Type" "text/plain; charset=utf-8"}
       :body (slurp (str (existing-path root rel)))}
      {:status 404 :body "Not found"})))

(defn handle-get [root uri]
  (cond
    (= "/" uri)
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (dashboard-page)}

    (= "/api/state" uri)
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string (dashboard-state root))}

    (str/starts-with? (or uri "") "/doc")
    (get-doc root uri)

    :else {:status 404 :body "Not found"}))

(defn handle-post [root uri body]
  (cond
    (= "/api/tasks" uri) (post-tasks root body)
    (= "/api/chat" uri) (post-chat root body)
    (str/starts-with? (or uri "") "/api/approvals/") (post-approval root uri)
    :else {:status 404 :body "Not found"}))

(defn handle-request [root {:keys [method uri body]}]
  (case method
    "GET" (handle-get root uri)
    "POST" (handle-post root uri body)
    {:status 404 :body "Not found"}))

(defn test-state! [root]
  (println (:body (handle-request (require-root! root) {:method "GET" :uri "/api/state"}))))

(defn test-html! []
  (print (:body (handle-request nil {:method "GET" :uri "/"})))
  (flush))

(defn test-post-task! [root name text]
  (handle-request (require-root! root)
                  {:method "POST"
                   :uri "/api/tasks"
                   :body (json/generate-string {:name name :text (or text "")})}))

(defn test-post-chat! [root text]
  (handle-request (require-root! root)
                  {:method "POST"
                   :uri "/api/chat"
                   :body (json/generate-string {:text (or text "")})}))

(defn test-inject-payload! [name text]
  (println (if (and name text)
             (task-payload name text)
             (task-payload))))

(defn test-inject-argv! [root file text]
  (when (str/blank? file)
    (exit! 1 "Missing argv file"))
  (binding [*tmux-stub* file]
    (inject-master! (require-root! root) text)))

(defn test-approval! [root id action]
  (when (str/blank? id)
    (exit! 1 "Missing approval id"))
  (handle-request (require-root! root)
                  {:method "POST"
                   :uri (str "/api/approvals/" id "/" action)}))

(defn -main [& args]
  (case (first args)
    "--test-state" (test-state! (second args))
    "--test-html" (test-html!)
    "--test-post-task" (test-post-task! (second args) (nth args 2 nil) (nth args 3 nil))
    "--test-post-chat" (test-post-chat! (second args) (nth args 2 nil))
    "--test-inject-payload" (test-inject-payload! (second args) (nth args 2 nil))
    "--test-inject-argv" (test-inject-argv! (second args) (nth args 2 nil) (nth args 3 nil))
    "--test-approve" (test-approval! (second args) (nth args 2 nil) "approve")
    "--test-reject" (test-approval! (second args) (nth args 2 nil) "reject")
    (do (usage)
        (exit! 1 nil)))
  (System/exit 0))

(apply -main *command-line-args*)
