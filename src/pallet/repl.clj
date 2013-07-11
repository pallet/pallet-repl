(ns pallet.repl
  "A namespace that can be used to pull in most of pallet's namespaces.  uesful
  when working at the clojure REPL."
  (:require [pallet.core.data-api :as da]
            [pallet.node :as node]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [pallet.utils :refer [apply-map]]
            [pallet.compute.node-list :refer [node-list-service]]
            [pallet.node :as node]
            [pallet.api :as api]
            [pallet.actions :as actions])
  (:use [clojure.pprint :only [print-table]]
        [clojure.pprint :only [pprint with-pprint-dispatch code-dispatch
                               print-table pprint-indent]]
        [clojure.walk :only [prewalk]]
        [pallet.stevedore :only (with-source-line-comments)]))

(defmacro use-pallet
  "Macro that will use pallet's namespaces, to provide an easy to access REPL."
  []
  '(do
     (clojure.core/use
      'pallet.api
      'pallet.actions
      'clj-ssh.ssh
      'pallet.repl)))

(defn show-nodes*
  "Prints the list of nodes in `nodes` as a table. The columns of the
  table and their order can be modified by passing a vector with all
  the keys in `keys`"
  [nodes & [keys]]
  (if keys
    (print-table keys nodes)
    (print-table nodes)))

(def node-table-cols
  [:hostname :group-name :os-family :os-version :primary-ip :ssh-port :proxy-port
   :private-ip :terminated?])

(defn show-nodes
  "Prints a table with the information on all the nodes in a compute provider.
  The columns displayed can be modified by passing an optional `keys`
  vector containing the keys to display as columns (order is
  significative)"
  [compute & [keys]]
  (let [keys (or keys node-table-cols)
        nodes (da/nodes compute)]
    (show-nodes* nodes keys)))

(defn show-group
  "Prints a table with the information on all the nodes belonging to a
  `group` (identified by it's name) on a particular `compute`
  provider. The columns displayed can be modified by passing an
  optional `keys` vector containing the keys to display as
  columns (order is significative)"
  [compute group-name & keys]
  (let [nodes (da/nodes compute)
        group-filter (fn [n] (= group-name (:group-name n)))
        group-nodes (filter group-filter nodes)]
    (show-nodes* group-nodes (or keys node-table-cols))))

(defn- prefix-text [prefix text]
  (when (and (seq text)
             (not (and (= 1 (count text))
                       (= "" (first text)))))
    (doseq [line (string/split-lines text)]
      (println (str prefix line)))))

(defn- indent-string [steps]
  (apply str (repeat steps " ")))

(defmacro with-indent [steps & body]
  `(prefix-text (indent-string ~steps)
                (with-out-str ~@body)))

(defmacro with-indent-prefix [steps prefix & body]
  `(prefix-text (str (indent-string ~steps) ~prefix)
                (with-out-str ~@body)))

(defn- explain-action [{:keys [location action-type script language form
                              script-dir script-prefix sudo-user blocks]
                       :as action}
                      level & [ print-scripts print-forms]]
  (println "ACTION:" (-> action :action :action-symbol)
           "of type" (name action-type)
           "executed on" (name location))
  (with-indent
   2
   (when (or script-dir script-prefix sudo-user)
     (format "OPTIONS: sudo-user=%s, script-dir=%s, and script-prefix=%s\n"
             sudo-user script-dir (name script-prefix)))
   (when print-forms
     (println "FORM:")
     (with-indent 2 (with-pprint-dispatch code-dispatch
                 (pprint form))))
   (when print-scripts
     (println "SCRIPT:")
     (with-indent-prefix 2 "| " (println (second script))))))

(defn- explain-if-action [action level]
  (let [[true-block false-block] (:blocks action)
        is-true (first (:args action))]
    (when (or (and is-true (seq true-block))
              (and (not is-true) (seq false-block)))
      (println "BRANCH"))))

(defn- explain-actions
  [actions & {:keys [level print-scripts print-forms print-branches]}]
  (let [level (or level 0)]
    (doseq [{:keys [location action-type script language form
                    script-dir script-prefix sudo-user blocks] :as action}
            actions
            :when action]
      (if (= action-type :flow/if)
        (when blocks
          (let [test-val (first (:args action))
                when?  (seq (first blocks))
                run? (or (and when? test-val)
                         (and (not when?) (not test-val)))]
            (when (or run?
                      (and (not run?) print-branches))
              (if when?
                (do
                  (printf "WHEN %s %s:\n" test-val
                          (if run? "" "(not executed)"))
                  (with-indent 2
                    (explain-actions (first blocks) :level (inc level)
                                     :print-scripts print-scripts
                                     :print-forms print-forms)))
                (do
                  (printf "WHEN NOT %s %s:" test-val
                          (if run? "" "(not executed)"))
                  (with-indent 2
                    (explain-actions (second blocks) :level (inc level)
                                     :print-scripts print-scripts
                                     :print-forms print-forms)))))))
        (explain-action action level print-scripts print-forms)))))

(defn explain-plan
  "Prints the action plan and corresponding shell scripts built as a
result of executing a plan function `pfn`. If the plan function
requires settings to be set, adding the settings function call to
`:settings-phase`

  By default, the plan function is run against a mock node with
this configuration:

    [\"mock-node\" \"mock-group\" \"0.0.0.0\" :ubuntu :os-version \"12.04\"]

and you can override this by passing your own node vector as `:node`,
or just change the os-family/os-version for the node by passing
`:os-family` and `:os-version`. If a os-family is passed but no
os-version, then a sane default for os-version will be picked. If you
specify `:node` then `:os-family` and `:os-version` are ignored.

By default, both the generated action forms (clojure forms) and the
scripts corresponding to those action forms will be shown, but you can
disable them by passing `:print-scripts false` and/or `:print-forms
false` "
  [pfn & {:keys [settings-phase print-scripts print-forms
                 print-branches
                 node os-family os-version group-name]
          :or {print-scripts true
               print-branches false
               print-forms true
               os-family :ubuntu
               group-name "mock-group"}}]
  (let [os-version (or os-version
                       (os-family {:ubuntu "12.04"
                                   :centos "6.3"
                                   :debian "6.0"
                                   :rhel "6.1"}))
        node (or node
                 ["mock-node" group-name  "0.0.0.0" os-family
                  :os-version os-version])
        ;; echo what node we're about to use for the mock run
        _ (do (print "NODE: ") (pprint node))
        actions (da/explain-plan pfn node :settings-phase settings-phase)]
    (explain-actions actions
                     :print-scripts print-scripts
                     :print-forms print-forms
                     :print-branches print-branches)))

(defn explain-phase
  "Prints the action plan and corresponding shell scripts built as a result of
executing a phase from the `server-spec`.  The `:configure` phase is explained
by default. The phase can be specified with the `:phase` keyword.

  By default, the plan function is run against a mock node with
this configuration:

    [\"mock-node\" \"mock-group\" \"0.0.0.0\" :ubuntu :os-version \"12.04\"]

and you can override this by passing your own node vector as `:node`,
or just change the os-family/os-version for the node by passing
`:os-family` and `:os-version`. If a os-family is passed but no
os-version, then a sane default for os-version will be picked. If you
specify `:node` then `:os-family` and `:os-version` are ignored.

By default, both the generated action forms (clojure forms) and the
scripts corresponding to those action forms will be shown, but you can
disable them by passing `:print-scripts false` and/or `:print-forms
false` "
  [server-spec & {:keys [phase print-scripts print-forms
                         print-branches node os-family os-version ]
                  :or {print-scripts true
                       print-forms true
                       print-branches false
                       os-family :ubuntu
                       phase :configure}
                  :as options}]
  (apply-map explain-plan
             (-> server-spec :phases phase)
             :settings-phase (-> server-spec :phases :settings)
             (if-let [group-name (:group-name server-spec)]
               (merge {:group-name (name group-name)} options)
               options)))

(defn explain-session
  "Prints out detail of the actions executed in the context of the
session, as well as possible infrastructure changes. The session
parameter can be serialized or pure clojure.set

The optional key parameter `:show-detail` controls whether detail on
each action is to be shown or not. It defaults to `true`. When turned
off, minimal OK/ERROR information will be presented for each node."
  [{:keys [destroyed-nodes created-nodes runs] :as session}
   & {:keys [show-detail] :or {show-detail true}}]
  (let [ ;; check if the session has been serialized already or not
        {:keys [destroyed-nodes created-nodes runs] :as session-data}
        (if (:runs session) session (da/session-data session))
        phases (da/phase-seq session-data)
        groups (da/groups session-data)]
    (when (seq? created-nodes)
      (println "nodes created:" (count created-nodes))
      (with-indent 2
        (doseq [group groups]
          (let [nodes (filter #(= group  (:group-name %)) created-nodes)]
            (when (seq nodes)
              (printf "group %s:\n" (name group))
              (with-indent 2
                (doseq [node nodes]
                  (printf "%s %s\n" (:primary-ip node) (:hostname node)))))))))
    (when (seq? destroyed-nodes)
      (println "nodes destroyed:" (count destroyed-nodes))
      (with-indent 2
        (doseq [group groups]
          (let [nodes (filter #(= group  (:group-name %)) destroyed-nodes)]
            (when (seq nodes)
              (printf "group %s: %s\n" (name group) (count nodes))
              (with-indent 2
                (doseq [node nodes]
                  (printf "%s %s\n" (:primary-ip node) (:hostname node)))))))))
    (println "PHASES:" (apply str (interpose ", " (map name phases))))
    (println "GROUPS:" (apply str (interpose ", " (map name groups))))
    (println "ACTIONS:")
    (with-indent 2
      (doseq [phase phases]
        (printf "PHASE %s:\n" (name phase))
        (with-indent 2
          (doseq [group groups]
            (printf "GROUP %s:\n" (name group))
            (with-indent 2
              (doseq [run (filter #(and (= phase (:phase %))
                                        (= group (:group-name %)))
                                  runs)]
                (if show-detail
                  (do
                    (printf "NODE %s:\n" (:primary-ip (:node run)))
                    (with-indent 2
                      (doseq [{:keys [script out exit error
                                      action-num context summary]
                               :as action}
                              (:action-results run)
                              ]
                        (if (nil? script)
                          (println "LOCAL ACTION.")
                          (do
                            (println "ACTION ON NODE:")
                            (with-indent 2
                              (when context
                                (printf "CONTEXT: %s\n" context))
                              (when summary
                                (printf "SUMMARY: %s\n" summary))
                              (println "SCRIPT:")
                              (with-indent-prefix 0 "| "
                                (println script))
                              (printf "EXIT CODE: %s\n" exit)
                              (println "OUTPUT:")
                              (with-indent-prefix 0 "| "
                                (println out))
                              (when-let [{:keys [type message out]} error]
                                (println "ERROR:")
                                (with-indent 2
                                  (printf "TYPE: %s\n" type)
                                  (printf "MESSAGE: %s\n" message)
                                  (printf "OUTPUT: %s\n" out)))))))))
                  (let [errors (map :error (:action-results run))]
                    (if (every? nil? errors)
                      (printf "NODE %s: OK\n" (:primary-ip (:node run)))
                      (printf "NODE %s: ERROR\n" (:primary-ip (:node run))))))))))))))

(defn session-summary
  "Provides a summary of the session execution, just indicating, for
  each phase, which nodes ran OK vs. which ones got ERRORs."
  [s]
  (explain-session s :show-detail false))

(defn node-list-from-session
  "Creates a node-list provider from a session. This node list
  provider will only contain the nodes affected in the session. "
  [s]
  (let [nodes (map :node (:targets s))
        node-vec (for [node nodes]
                   [(node/hostname node)
                    (node/group-name node)
                    (node/primary-ip node)
                    (node/os-family node)
                    :os-version (node/os-version node)
                    :is-64bit? (node/is-64bit? node)])]
    (node-list-service node-vec)))


(defn run-script
  "Runs a script on a group or list of groups, and prints out the
  resulting session.

  If `compute-or-session` is a compute, it will work as a regular
  lift/confgerge, but if it is a session, then it will only consider
  the nodes in the session, and won't use the compute provider to find
  these nodes, which should be faster"
  [compute-or-session groups script]
  (let [compute (if (some #{:targets} (keys compute-or-session))
                  ; it's a session
                  (node-list-from-session compute-or-session)
                  ; nope, it's a compute
                  compute-or-session)
        s (api/lift groups
                :compute compute
                :phase (api/plan-fn
                        (actions/exec-script* script))
                :os-detect false)]
    (explain-session s)))


