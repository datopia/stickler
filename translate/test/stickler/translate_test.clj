(ns stickler.translate-test
  (:require [clojure.test
             :refer [deftest is]]
            [clojure.string     :as str]
            [stickler.translate :as t]
            [path.path          :as p]
            [path.util          :as p.util]))

(defn spit-files [m]
  (let [root-dir (p.util/create-temp-dir "translate-test")]
    (doseq [[path contents] m
            :let [p (p/resolve root-dir path)]]
      (p/create-dirs (p/parent p))
      (p/spit p contents))
    (str root-dir)))

(defn proto [package & [imports]]
  (let [imports (str/join
                 "\n"
                 (for [import imports]
                   (str "import \"" import "\";")))]
    (str "syntax = \"proto3\";

package " package ";
" imports "

message Test {
  string test = 1;
}")))

(deftest relative-import
  (let [root (spit-files
              {"a/b.proto" (proto "a.b" ["../b/c.proto"])
               "b/c.proto" (proto "b.c")})
        res  (t/translate {:files [(p/resolve root "a/b.proto")
                                   (p/resolve root "b/c.proto")]})]
    (is (-> res :a :b :Test :stickler/msg?))
    (is (-> res :b :c :Test :stickler/msg?))))

(deftest path-import
  (let [root (spit-files
              {"a/b.proto" (proto "a.b" ["b/c.proto"])
               "b/c.proto" (proto "b.c")})
        res  (t/translate {:files [(p/resolve root "a/b.proto")]
                           :paths [root]})]
    (is (-> res :a :b :Test :stickler/msg?))
    (is (-> res :b :c :Test :stickler/msg?))))
