(ns pallet.repl-test
  (:require
   [clojure.test :refer :all]
   [pallet.actions :refer [exec-script]]
   [pallet.api :refer [plan-fn]]
   [pallet.repl :as repl]))

(deftest use-test
  (repl/use-pallet))

(deftest explain-plan-test
  (is (with-out-str (repl/explain-plan (plan-fn (exec-script ("ls")))))))
