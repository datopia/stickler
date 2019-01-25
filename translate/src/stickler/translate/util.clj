(ns ^:no-doc stickler.translate.util)

(defn assoc-when
  "Like assoc but only assocs when value is truthy"
  [m & kvs]
  (assert (even? (count kvs)))
  (into (or m {})
    (for [[k v] (partition 2 kvs) :when v]
      [k v])))
