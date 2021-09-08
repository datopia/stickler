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

  :files file ...
      Include the given protobuf3 file/s.
  :dirs dir ...
      Recursively include all protofobuf3 files beneath the given dir/s.
  :paths path ...
      Look for includes in the given path/s, without processing them unless
      included.
  :ignores str ...
      Don't process or import files w/ paths containing any of the given strings.

   Passing neither :files nor :dirs will result in the current directory
   being recursively processed.

   Relative filenames are acceptable.

   The EDN is pretty-printed to stdout"
  [_ & args]
  (pprint/pprint
   (if args
     (translate/translate (parse-args args))
     (translate/translate {:dirs ["."]}))))
