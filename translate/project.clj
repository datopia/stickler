(defproject org.datopia/stickler-translate "0.1.0-SNAPSHOT"
  :description  "protobuf3 -> EDN schema generator."
  :url          "https://github.com/datopia/stickler"
  :license      {:name "MIT License"
                 :url  "http://opensource.org/licenses/MIT"}
  :scm          {:name "git"
                 :url  "https://github.com/datopia/stickler"}
  :dependencies [[org.clojure/clojure           "1.10.3"]
                 [clj-antlr                     "0.2.10"]
                 [org.datopia/path              "0.1.0-SNAPSHOT"]]
  :profiles
  {:dev {:global-vars  {*warn-on-reflection* true}
         :dependencies [[io.datopia/codox-theme "0.1.0"]]
         :plugins      [[lein-codox "0.10.5"]]
         :codox        {:namespaces [#"^stickler\."]
                        :metadata   {:doc/format :markdown}
                        :themes     [:default [:datopia
                                               {:datopia/github
                                                "https://github.com/datopia/stickler"}]]}}})
