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
       "  pack_web.sh --test-post-task <root> <name> <text>"))

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

(defn dashboard-state [root]
  (let [master (master-role root)]
    {:master_role master
     :master_display (display-name-for-role master)
     :lanes (lanes root)
     :tasks (tasks root)
     :approvals []
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
              "--text" (or text "")))

(defn post-tasks [root body]
  (let [{:keys [name text]} (json/parse-string (or body "{}") true)]
    (create-task! root name text)
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string {:ok true})}))

(defn handle-request [root {:keys [method uri body]}]
  (case [method uri]
    ["GET" "/"]
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (dashboard-page)}

    ["GET" "/api/state"]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string (dashboard-state root))}

    ["POST" "/api/tasks"]
    (post-tasks root body)

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

(defn -main [& args]
  (case (first args)
    "--test-state" (test-state! (second args))
    "--test-html" (test-html!)
    "--test-post-task" (test-post-task! (second args) (nth args 2 nil) (nth args 3 nil))
    (do (usage)
        (exit! 1 nil)))
  (System/exit 0))

(apply -main *command-line-args*)
