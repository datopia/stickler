(defproject io.datopia/stickler-codec "0.1.0"
  :description        "Idiomatic Clojure codec functionality for protobuf3."
  :url                "https://github.com/datopia/stickler"
  :license            {:name "MIT License"
                       :url  "http://opensource.org/licenses/MIT"}
  :scm                {:name "git"
                       :url  "https://github.com/datopia/stickler"}
  :dependencies       [[org.clojure/clojure "1.10.0"]]
  :java-source-paths  ["src/java"]
  :profiles
  {:dev {:java-source-paths ["test/gen-java"]
         :global-vars       {*warn-on-reflection* true}
         :aliases
         {"test-prep" ["run" "-m" "stickler.test-prep"]}
         :plugins      [[lein-codox "0.10.5"]]
         :codox        {:namespaces [#"^stickler\."]
                        :metadata   {:doc/format :markdown}
                        :themes     [:default [:datopia {:datopia/github "https://github.com/datopia/stickler"}]]}
         :dependencies
         [[io.datopia/stickler-translate         "0.1.0"]
          [io.datopia/codox-theme                "0.1.0"]
          [com.squareup.wire/wire-java-generator "2.3.0-RC1"]
          [org.clojure/test.check                "0.10.0-alpha3"]]}})
