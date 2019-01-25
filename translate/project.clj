(defproject io.datopia/stickler-translate "0.1.0-SNAPSHOT"
  :description  "protobuf3 -> EDN schema generator."
  :url          "https://github.com/datopia/stickler"
  :license      {:name "MIT License" :url "http://opensource.org/licenses/MIT"}
  :scm          {:name "git" :url "https://github.com/datopia/stickler"}
  :dependencies [[org.clojure/clojure            "1.10.0-RC3"]
                 [com.squareup.wire/wire-schema  "2.3.0-RC1"]]
  :main         stickler.translate
  :aot          [stickler.translate]
  :profiles     {:dev {:global-vars {*warn-on-reflection* true}}})
