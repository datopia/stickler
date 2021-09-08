(ns stickler.test-prep
  (:require [clojure.java.io    :as io]
            [stickler.translate :as stickler]
            [clojure.pprint     :as pprint]
            [path.path          :as p]
            [path.util          :as p.util])
  (:import [com.squareup.wire.schema
            Schema SchemaLoader]
           [com.squareup.wire.java
            ProfileLoader JavaGenerator]
           [com.squareup.javapoet
            JavaFile])
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

(defn- ^SchemaLoader dirs->loader [& dirs]
  (reduce
   (fn [^SchemaLoader loader dir]
     (doto loader
       (.addSource (io/file dir))))
   (SchemaLoader.)
   dirs))

(defn dirs->Schema [& dirs]
  (.load ^SchemaLoader (apply dirs->loader dirs)))

(defn- generate-edn [schema out-dir]
  (with-open [writer (io/writer (io/file out-dir "schema.edn"))]
    (pprint/write schema :stream writer)))

(defn -main [& [out-dir schema-out]]
  (let [out-dir    (or out-dir    "test/gen-java")
        schema-out (or schema-out "test/resources")
        in-res     (io/resource   "test.proto")
        tmp-dir    (p/->file (p.util/create-temp-dir "proto"))
        tmp-f      (p/resolve tmp-dir "test.proto")]
    (p/spit tmp-f (slurp in-res))
    (let [wire-schema (dirs->Schema tmp-dir)
          edn-schema  (stickler/translate {:dirs [tmp-dir]})]
      (generate-java wire-schema (io/file out-dir))
      (generate-edn  edn-schema  (io/file schema-out)))))
