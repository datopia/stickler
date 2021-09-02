(ns stickler.codec-test
  (:require [stickler.codec  :as codec]
            [stickler.codec-test.util
             :refer [map->Scalars float= double= msg=]]
            [clojure.java.io :as io]
            [clojure.edn     :as edn]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test
             :refer [defspec]])
  (:import [stickler.test
            Scalars
            Scalars$Builder
            ScalarContainer
            ScalarsRepeated
            RepeatedScalarPacked
            RepeatedScalarUnpacked
            RepeatedString
            OneOfInt]
           [java.util
            Arrays]))

(def ^:private trials 1000)

(def ^:private ^com.squareup.wire.ProtoAdapter s-adapter (. Scalars ADAPTER))

(def ^:private gen-int32 (gen/fmap unchecked-int gen/int))
(def ^:private gen-ints  (gen/vector gen-int32))
(def ^:private gen-float (gen/fmap unchecked-float gen/double))

(def ^:private field->gen
  {:int32        gen-int32
   :int64        gen/large-integer
   :uint32       gen-int32
   :uint64       gen/large-integer
   :sint32       gen-int32
   :sint64       gen/large-integer
   :sfixed32     gen-int32
   :sfixed64     gen/large-integer
   :bytes        gen/bytes
   :string       gen/string
   :bool         gen/boolean
   :double       gen/double
   :float        gen-float
   :stickler/msg (gen/return :stickler.test/Scalars)})

(defn- map->gen [m]
  (let [m-gen (apply gen/tuple
                     (mapcat
                      (fn [[k v]]
                        [(gen/return k)
                         (cond-> v (not (gen/generator? v)) gen/return)])
                      m))]
    (gen/fmap (partial apply hash-map) m-gen)))

(defn- dissoc-empty [m]
  (into {}
    (for [[k v] m :when (or (not (coll? v)) (not-empty v))]
      [k v])))

(def schema (codec/prepare-schema
             (edn/read-string (slurp (io/resource "schema.edn")))))

(defn encode-bytes ^bytes [bs]
  (codec/encode-bytes schema bs))

(def decode-bytes (partial codec/decode-bytes schema))

(defn- roundtrip [m]
  (decode-bytes (:stickler/msg m) (encode-bytes m)))

(let [msg :stickler.test/Scalars]
  (defmacro scalar-wire-symmetry-prop [field & [java-field]]
    (let [sym (symbol (str "." (name (or java-field field))))
          kf  (keyword field)]
      `(prop/for-all* [(get field->gen ~kf)]
         (fn [i#]
           (Arrays/equals
            (.encode s-adapter (-> (Scalars$Builder.) (~sym i#) .build))
            (encode-bytes {:stickler/msg ~msg
                           ~kf           i#}))))))

  (defmacro scalar-roundtrip-prop [field]
    (let [kf (keyword field)]
      `(prop/for-all* [(get field->gen ~kf)]
         (fn [i#]
           (let [m# {:stickler/msg ~msg
                     ~kf           i#}]
             (= m# (roundtrip m#))))))))

(defmacro wire-symmetry-prop [cls m xf]
  `(let [adapter# (. ~cls ~'ADAPTER)]
     (prop/for-all* [(cond-> ~m (not (gen/generator? ~m)) map->gen)]
       (fn [m#]
         (Arrays/equals
          (.encode ^com.squareup.wire.ProtoAdapter adapter# (~xf m#))
          (encode-bytes m#))))))

(defmacro roundtrip-prop [m]
  `(prop/for-all* [(map->gen ~m)]
     (fn [m#]
       (= (dissoc-empty m#) (roundtrip m#)))))

(def ^:private gen-message (map->gen field->gen))

(defspec int32-wire-symmetry trials
  (scalar-wire-symmetry-prop int32))

(defspec int32-roundtrip trials
  (scalar-roundtrip-prop int32))

(defspec int64-wire-symmetry trials
  (scalar-wire-symmetry-prop int64))

(defspec int64-roundtrip trials
  (scalar-roundtrip-prop int64))

(defspec uint32-wire-symmetry trials
  (scalar-wire-symmetry-prop uint32))

(defspec uint32-roundtrip trials
  (scalar-roundtrip-prop uint32))

(defspec uint64-wire-symmetry trials
  (scalar-wire-symmetry-prop uint64))

(defspec uint64-roundtrip trials
  (scalar-roundtrip-prop uint64))

(defspec sint32-wire-symmetry trials
  (scalar-wire-symmetry-prop sint32))

(defspec sint32-roundtrip trials
  (scalar-roundtrip-prop sint32))

(defspec sint64-wire-symmetry trials
  (scalar-wire-symmetry-prop sint64))

(defspec sint64-roundtrip trials
  (scalar-roundtrip-prop sint64))

(defspec sfixed32-wire-symmetry trials
  (scalar-wire-symmetry-prop sfixed32))

(defspec sfixed32-roundtrip trials
  (scalar-roundtrip-prop sfixed32))

(defspec sfixed64-wire-symmetry trials
  (scalar-wire-symmetry-prop sfixed64))

(defspec sfixed64-roundtrip trials
  (scalar-roundtrip-prop sfixed32))

(defspec bool-symmetry trials
  (scalar-wire-symmetry-prop bool))

(defspec bool-roundtrip trials
  (scalar-roundtrip-prop bool))

(defspec string-symmetry trials
  (scalar-wire-symmetry-prop string))

(defspec string-roundtrip trials
  (scalar-roundtrip-prop string))

(defspec double-symmetry trials
  (scalar-wire-symmetry-prop double double_))

(defspec double-roundtrip trials
  (prop/for-all [m (map->gen {:stickler/msg :stickler.test/Scalars
                              :double       gen/double})]
    (double= (:double m) (:double (roundtrip m)))))

(defspec float-symmetry trials
  (scalar-wire-symmetry-prop float float_))

(defspec float-roundtrip trials
  (prop/for-all [m (map->gen {:stickler/msg :stickler.test/Scalars
                              :float        gen-float})]
    (float= (:float m) (:float (roundtrip m)))))

(defspec bytes-wire-symmetry trials
  (wire-symmetry-prop
   Scalars
   {:stickler/msg :stickler.test/Scalars
    :bytes        gen/bytes}
   map->Scalars))

(defspec bytes-roundtrip trials
  (prop/for-all [m (map->gen {:stickler/msg :stickler.test/Scalars
                              :bytes        gen/bytes})]
    (Arrays/equals ^bytes (:bytes m)
                   ^bytes (:bytes (roundtrip m)))))

(defspec message-wire-symmetry trials
  (wire-symmetry-prop Scalars gen-message map->Scalars))

(defspec message-roundtrip trials
  (prop/for-all [m gen-message]
    (msg= m (roundtrip m))))

(defspec nested-message-wire-symmetry trials
  (wire-symmetry-prop
   ScalarContainer
   {:stickler/msg :stickler.test/ScalarContainer
    :value        gen-message}
   #(-> % :value map->Scalars ScalarContainer.)))

(defspec nested-message-roundtrip trials
  (prop/for-all [msg (map->gen {:stickler/msg :stickler.test/ScalarContainer
                                :value        gen-message})]
    (msg= (:value msg) (:value (roundtrip msg)))))

;; Per protobuf3, a repeated scalar with no options should default to packed
;; encoding; we compare our encoding of `RepeatedScalarNoOptions` with Wire's
;; encoding of `RepeatedScalarPacked`.

(defspec repeated-scalar-wire-symmetry trials
  (wire-symmetry-prop
   RepeatedScalarPacked
   {:stickler/msg :stickler.test/RepeatedScalarNoOptions
    :int32s       gen-ints}
   #(-> % :int32s RepeatedScalarPacked.)))

;; A packed repeated scalar field holding no values will not appear in the
;; output; the corresponding key will be absent from the decoded map.

(defspec repeated-scalar-roundtrip trials
  (roundtrip-prop {:stickler/msg :stickler.test/RepeatedScalarNoOptions
                   :int32s       gen-ints}))

;; A repeated scalar may explicitly declare unpacked encoding.

(defspec repeated-scalar-unpacked-wire-symmetry trials
  (wire-symmetry-prop
   RepeatedScalarUnpacked
   {:stickler/msg :stickler.test/RepeatedScalarUnpacked
    :int32s       gen-ints}
   #(-> % :int32s RepeatedScalarUnpacked.)))

(defspec repeated-scalar-unpacked-roundtrip trials
  (roundtrip-prop {:stickler/msg :stickler.test/RepeatedScalarUnpacked
                   :int32s       gen-ints}))

;; Repeated strings can't be packed.

(defspec repeated-string-wire-symmetry (/ trials 10)
  (wire-symmetry-prop
   RepeatedString
   {:stickler/msg :stickler.test/RepeatedString
    :strings      (gen/vector gen/string)}
   #(-> % :strings RepeatedString.)))

(defspec repeated-string-roundtrip trials
  (roundtrip-prop {:stickler/msg :stickler.test/RepeatedString
                   :strings      (gen/vector gen/string)}))

;; Nor can repeated messages.

(defspec repeated-message-wire-symmetry (/ trials 10)
  (wire-symmetry-prop
   ScalarsRepeated
   {:stickler/msg :stickler.test/ScalarsRepeated
    :values       (gen/vector gen-message)}
   #(->> % :values (mapv map->Scalars) ScalarsRepeated.)))

(def ^:private gen-one-of
  (gen/one-of [(gen/tuple (gen/return :int32) (:int32 field->gen))
               (gen/tuple (gen/return :int64) (:int64 field->gen))]))

(let [adapter (. OneOfInt ADAPTER)]
  (defspec oneof-wire-symmetry trials
    (prop/for-all [kv gen-one-of]
      (let [msg (apply hash-map :stickler/msg :stickler.test/OneOfInt kv)]
        (Arrays/equals
         (.encode adapter (OneOfInt. (:int32 msg) (:int64 msg)))
         (encode-bytes msg))))))

(defspec one-of-roundtrip trials
  (prop/for-all [kv gen-one-of]
    (let [msg (apply hash-map :stickler/msg :stickler.test/OneOfInt kv)
          out (roundtrip msg)]
      (and (= (:stickler.one-of/value out) (first kv))
           (= msg (dissoc out :stickler.one-of/value))))))
