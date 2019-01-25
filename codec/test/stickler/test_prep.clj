(ns stickler.test-prep
  (:require [clojure.java.io    :as io]
            [stickler.translate :as stickler]
            [clojure.pprint     :as pprint])
  (:import [com.squareup.wire.schema Schema SchemaLoader ProtoType]
           [com.squareup.wire.java ProfileLoader JavaGenerator]
           [com.squareup.javapoet JavaFile]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute])
  (:gen-class))

(defn- output-java [dir ^JavaGenerator gen t]
  (let [t-spec  (.generateType      gen t)
        t-name  (.generatedTypeName gen t)
        builder (JavaFile/builder (.packageName t-name) t-spec)
        f       (.build builder)]
    (.writeTo f ^java.io.File dir)))

(defn- schema->generator [schema]
  (let [p-loader (doto (ProfileLoader. "java")
                   (.schema schema))]
    (-> (JavaGenerator/get schema)
        (.withAndroid false)
        (.withCompact true)
        (.withProfile (.load p-loader)))))

(defn- generate-java [^Schema schema out-dir]
  (let [gen     (schema->generator schema)
        out-dir (io/file out-dir)]
    (doseq [pf (.protoFiles schema)
            t  (.types ^com.squareup.wire.schema.ProtoFile pf)]
      (output-java out-dir gen t))))

(defn- generate-edn [schema out-dir]
  (with-open [writer (io/writer (io/file out-dir "schema.edn"))]
    (pprint/write (stickler/Schema->edn schema) :stream writer)))

(defn -main [& [out-dir schema-out]]
  {:pre [out-dir]}
  (let [tmp-in  (.toFile (Files/createTempDirectory "proto" (make-array FileAttribute 0)))
        proto-f (io/file tmp-in "test.proto")
        in-res  (io/resource "test.proto")]
    (spit proto-f (slurp in-res))
    (let [schema (stickler/dirs->Schema tmp-in)]
      (generate-java schema (io/file out-dir))
      (generate-edn  schema (io/file schema-out)))))
