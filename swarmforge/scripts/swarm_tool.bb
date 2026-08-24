#!/usr/bin/env bb

(ns swarm-tool
  (:require [babashka.fs :as fs]
            [clojure.java.shell :as sh]
            [clojure.string :as str]))

(def catalog
  {"gherkin-parser" {:source "github.com/unclebob/Acceptance-Pipeline-Specification"
                     :bb-task "gherkin-parser"}
   "ir-dry-checker" {:source "github.com/unclebob/Acceptance-Pipeline-Specification"
                     :bb-task "gherkin-ir-dry-checker"}
   "gherkin-mutator" {:source "github.com/unclebob/Acceptance-Pipeline-Specification"
                      :bb-task "gherkin-mutator"}
   "crap4clj" {:source "github.com/unclebob/crap4clj" :bb-task "crap4clj"}
   "dry4clj" {:source "github.com/unclebob/dry4clj" :bb-task "dry4clj"}
   "clj-mutate" {:source "github.com/unclebob/clj-mutate" :bb-task "clj-mutate"}
   "cloverage" {:source "github.com/unclebob/crap4clj" :bb-task "crap4clj"}
   "crap4go" {:source "github.com/unclebob/crap4go" :bb-task "crap4go"}
   "dry4go" {:source "github.com/unclebob/dry4go" :bb-task "dry4go"}
   "mutate4go" {:source "github.com/unclebob/mutate4go" :bb-task "mutate4go"}
   "crap4java" {:source "github.com/unclebob/crap4java" :bb-task "crap4java"}
   "dry4java" {:source "github.com/unclebob/dry4java" :bb-task "dry4java"}
   "mutate4java" {:source "github.com/unclebob/mutate4java" :bb-task "mutate4java"}})

(def usage-text
  (str "Usage:\n"
       "  swarm_tool.sh require <tool>\n"
       "  swarm_tool.sh ensure <tool>\n\n"
       "Tools: " (str/join ", " (sort (keys catalog)))))

(defn usage []
  (binding [*out* *err*]
    (println usage-text)))

(defn exit! [status message]
  (binding [*out* *err*]
    (when message
      (println message)))
  (System/exit status))

(defn sq [value]
  (str "'" (str/replace (str value) #"'" "'\"'\"'") "'"))

(defn roles-at? [root]
  (and root (fs/exists? (fs/path root ".swarmforge" "roles.tsv"))))

(defn git-common-dir []
  (let [git (sh/sh "git" "rev-parse" "--git-common-dir")]
    (when (zero? (:exit git))
      (let [path (fs/path (str/trim (:out git)))]
        (str (if (fs/absolute? path) path (fs/absolutize path)))))))

(defn project-root []
  (or (let [parent (some-> (git-common-dir) fs/parent str)]
        (when (roles-at? parent) parent))
      (let [git (sh/sh "git" "rev-parse" "--show-toplevel")
            root (when (zero? (:exit git)) (str/trim (:out git)))]
        (when (roles-at? root) root))
      (when (roles-at? (fs/cwd)) (fs/cwd))
      (exit! 1 "Cannot find SwarmForge project root")))

(defn tool-spec [tool]
  (or (get catalog tool)
      (exit! 1 (str "Unknown tool: " tool "\n\n" usage-text))))

(defn bin-dir [root]
  (fs/path root ".swarmforge" "bin"))

(defn wrapper-path [root tool]
  (fs/path (bin-dir root) tool))

(defn source-dir [root source]
  (if-let [override (not-empty (System/getenv "SWARMFORGE_TOOL_SRC"))]
    (fs/path override)
    (fs/path root ".swarmforge" "tools" (last (str/split source #"/")))))

(defn require-tool! [tool]
  (tool-spec tool)
  (let [path (wrapper-path (project-root) tool)]
    (if (fs/executable? path)
      (do (println "OK:" tool (str path))
          (System/exit 0))
      (exit! 1 (str "MISSING: " tool "\nRun: swarm_tool.sh ensure " tool)))))

(defn clone-source! [dir source]
  (fs/create-dirs (fs/parent dir))
  (let [url (str "https://" source ".git")
        result (sh/sh "git" "clone" "--depth" "1" url (str dir))]
    (when-not (zero? (:exit result))
      (exit! 1 (str "Failed to clone " url "\n" (:err result) (:out result))))))

(defn ensure-source! [root source]
  (let [dir (source-dir root source)]
    (when-not (fs/exists? (fs/path dir "bb.edn"))
      (when (System/getenv "SWARMFORGE_TOOL_SRC")
        (exit! 1 (str "SWARMFORGE_TOOL_SRC is missing bb.edn: " dir)))
      (clone-source! dir source))
    dir))

(defn write-wrapper! [root tool bb-task src-dir]
  (let [target (wrapper-path root tool)
        config (str (fs/path src-dir "bb.edn"))]
    (fs/create-dirs (fs/parent target))
    (spit (str target)
          (str "#!/usr/bin/env bash\n"
               "exec bb --config " (sq config) " " bb-task " \"$@\"\n"))
    (fs/set-posix-file-permissions target "rwxr-xr-x")
    target))

(defn ensure-tool! [tool]
  (let [{:keys [source bb-task]} (tool-spec tool)
        root (project-root)
        src (ensure-source! root source)
        target (write-wrapper! root tool bb-task src)]
    (println "INSTALLED:" tool (str target))))

(defn -main [& args]
  (when (some #{"--help" "-h"} args)
    (usage)
    (System/exit 0))
  (when (not= 2 (count args))
    (usage)
    (System/exit 1))
  (let [[command tool] args]
    (case command
      "require" (require-tool! tool)
      "ensure" (ensure-tool! tool)
      (do (usage)
          (System/exit 1)))))

(apply -main *command-line-args*)
