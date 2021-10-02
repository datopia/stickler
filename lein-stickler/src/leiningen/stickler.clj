(ns leiningen.stickler
  (:require [clojure.string     :as str]
            [clojure.pprint     :as pprint]
            [stickler.translate :as translate]))

(defn- parse-args [args]
  (let [args (map #(cond-> % (str/starts-with? % ":") read-string) args)
        args (partition-by keyword? args)]
    (reduce
     (fn [acc [[arg] vs]]
       (update acc arg (fnil into #{}) vs))
     {} (partition 2 args))))

(defn ^{:no-project-needed true} stickler
  "protobuf3 -> EDN schema, for use with stickler.codec.

   :dirs /proto ...
      A sequence of absolute directories to process into a protobuf schema.
   The EDN is pretty-printed to stdout"
  [_ & args]
  (let [args (parse-args args)]
    (-> (apply translate/dirs->Schema (:dirs args))
        translate/Schema->edn
        pprint/pprint)))
