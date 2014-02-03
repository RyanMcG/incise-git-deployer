(ns incise.deployer.impl.git-branch-test
  (:require [stefon.util :refer [temp-dir]]
            [incise.utils :refer [delete-recursively]]
            [clojure.test :refer :all]
            (clojure.java [io :refer [file]]
                          [shell :refer [with-sh-dir sh]])
            [clj-jgit [porcelain :refer :all :exclude [with-repo git-push]]]
            [incise.config :as conf]
            [incise.test-helpers :refer [with-custom-config]]
            [incise.deployer.impl.git-branch :refer :all])
  (:import [org.eclipse.jgit.api Git]))

(def test-temp-dir (partial temp-dir "incise-deploy-git-branch-spec"))

(defn- create-dummy-repo
  "Create a git repository in a temporary directory."
  []
  (let [tmp-dir (test-temp-dir)
        repo (git-init tmp-dir)]
    (doseq [fname ["y" "x"]]
      (spit (file tmp-dir fname) "some garbage content"))
    (spit (file tmp-dir ".gitignore") "/x")
    (git-add repo ".")
    (git-commit repo "Initial commit.")
    tmp-dir))

(deftest test-with-repo
  (let [repo-dir (create-dummy-repo)]
    (with-repo repo-dir
      (testing "dynmaic vars are bound"
        (doseq [bvar [#'*repo* #'*out-dir* #'*work-dir*]]
          (is (bound? bvar))))
      (is (instance? Git *repo*))
      (is (= repo-dir *work-dir*))
      (is (= (file repo-dir ".git" "_incise") *out-dir*)))
    (delete-recursively repo-dir)))

(deftest test-move-to-work-dir
  (let [work-dir (test-temp-dir)
        out-dir (test-temp-dir)
        file-name "my-cool-file"
        ex-file (file out-dir file-name)]
    (binding [*work-dir* work-dir
              *out-dir* out-dir]
      (testing "remove-out-dir"
        (is (= file-name (remove-out-dir
                           (.getCanonicalPath (file out-dir file-name))))))
      (testing "move-to-work-dir"
        (is (= (file work-dir file-name) (move-to-work-dir ex-file)))))
    (delete-recursively work-dir)
    (delete-recursively out-dir)))

(defmacro with-dummy-repo [& body]
  `(let [~'repo-dir (create-dummy-repo)]
     (with-repo ~'repo-dir
       ~@body)
     (delete-recursively ~'repo-dir)))

(deftest test-add-file
  (with-dummy-repo
    (let [files (map (partial file repo-dir) ["a" "b" "c"])]
      (testing "adds files"
        (map add-file files)))))

(deftest test-branch-exists?
  (with-dummy-repo
    (let [branch-name "a-branch-name"]
      (testing "should not exist with an inital repo"
        (is (not (branch-exists? branch-name))))
      (testing "should exist after creating the branch"
        (checkout-orphaned-branch branch-name)
        (git-commit *repo* "Blarg blarg")
        (is (branch-exists? branch-name))))))

(defn- file-exists? [file-like]
  (.exists (file *work-dir* file-like)))

(deftest test-checkout-orphaned-branch
  (let [branch-name "testest"]
    (testing "changes the checked out branch"
      (with-dummy-repo
        (is (= "master" (git-branch-current *repo*)))
        (checkout-orphaned-branch branch-name)
        (is (= branch-name (git-branch-current *repo*)))))
    (testing "removes all version controlled files leaving ignored ones"
      (with-dummy-repo
        (is (file-exists? ".gitignore"))
        (is (file-exists? "x"))
        (is (file-exists? "y"))
        (checkout-orphaned-branch branch-name)
        (is (not (file-exists? ".gitignore")))
        (is (file-exists? "x"))
        (is (not (file-exists? "y")))))))

(deftest test-setup-branch
  (let [branch-name "stuffs"]
    (testing "creates a new orphaned branch if it does not exist"
      (with-dummy-repo
        (setup-branch branch-name)
        (is (= branch-name (git-branch-current *repo*)))))
    (testing "checks the branch out if it does exist"
      (with-dummy-repo
        (is (not (branch-exists? branch-name)))
        (checkout-orphaned-branch branch-name)
        (git-checkout *repo* "master")
        (setup-branch branch-name)
        (is (= branch-name (git-branch-current *repo*)))))))


(defmacro with-dummy-repo-and-custom-in-dir [& body]
  `(with-dummy-repo
     (let [~'repo-dir-path (.getCanonicalPath ~'repo-dir)]
       (with-custom-config {:precompiles []
                            :in-dir ~'repo-dir-path}
         ~@body))))

(deftest test-deploy
  (testing "deploys without commit or push"
    (with-dummy-repo-and-custom-in-dir
      (deploy {:path repo-dir
               :commit false
               :push false})))
  (testing "deploys with commit only"
    (with-dummy-repo-and-custom-in-dir
      (deploy {:path repo-dir
               :commit true
               :push false})))
  (testing "with a bare clone"
    (with-dummy-repo-and-custom-in-dir
      (let [remote-dir (test-temp-dir)
            remote-dir-path (.getCanonicalPath remote-dir)]
        (git-clone repo-dir-path remote-dir-path "origin" "master" true)
        (doto (-> *repo*
                  (.getRepository)
                  (.getConfig))
          (.setString "remote" "origin" "url" remote-dir-path)
          (.save))
        (testing "deploys with commit and push"
          (deploy {:path repo-dir
                   :commit true
                   :push true}))
        (delete-recursively remote-dir)))))

(run-tests)
