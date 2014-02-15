(defproject incise-git-deployer "0.1.0"
  :description "The default git branch deployer for incise."
  :url "https://github.com/RyanMcG/incise-git-deployer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[incise-core "0.2.0"]
                 [clj-jgit "0.4.0"]]
  :repl-options {:init-ns incise.repl}
  :main incise.core)
