(ns stickler.codec
  (:require [clojure.java.io :as io]
            [clojure.edn     :as edn]
            [clojure.walk    :as walk]
            [clojure.string :as str]
            [stickler.translate :as stickler])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [java.nio.charset Charset]
           [org.datopia.stickler CodecUtil]))

(def ^:private tag-type-bits      3)
(def ^:private repeated-wire-type 2)
(def ^:private embedded-msg-type  2)

(let [explode (fn explode [m]
                (reduce-kv
                 (fn [acc ks v]
                   (into acc (for [k ks]
                               [k v])))
                 {} m))]
    (def ^:private type->wire-type
      (explode
       {#{:int32 :int64 :uint32 :uint64 :sint32 :sint64 :bool :enum} 0
        #{:fixed64 :sfixed64 :double}                                1
        #{:string :bytes}                                            2
        #{:fixed32 :sfixed32 :float}                                 5})))

(def ^:private explode-k
  (memoize
   (fn [k]
     (map keyword (conj (str/split (namespace k) #"\.") (name k))))))

(defn fetch [schema k]
  (get-in schema (explode-k k)))

(defn- verify-schema [schema t]
  (let [t-schema (fetch schema t)]
    (assert t-schema (str "Unknown message: " t))
    t-schema))

(defn- verify-encode [schema msg]
  (let [t (:stickler/msg msg)]
    (assert (keyword? t) "Missing :stickler/msg key")
    (verify-schema schema t)))

(defn- field->tag [{wire-type :wire-type tag :tag}]
  {:pre [wire-type tag]}
  (bit-or (bit-shift-left tag tag-type-bits) wire-type))

(defn- tag->field [t-schema v]
  {:pre  [(:tag->field t-schema) v]
   :post [(map? %)]}
  (let [tag (unsigned-bit-shift-right v tag-type-bits)]
    (get-in t-schema [:tag->field tag])))

(defn- stream->field [t-schema ^ByteArrayInputStream s]
  (when-not (zero? (.available s))
    (let [tag (CodecUtil/readVarint32 s)]
      (tag->field t-schema tag))))

(defn- encode-byte-array [^ByteArrayOutputStream stream ^bytes v]
  (CodecUtil/writeVarint32 stream (alength v))
  (.write stream v 0 (alength v)))

(defn- decode-byte-array [^ByteArrayInputStream stream & [len]]
  (let [len (or len (CodecUtil/readVarint32 stream))
        out (byte-array len)]
    (.read stream out 0 len)
    out))

(def ^:private utf8 (Charset/forName "UTF-8"))

(declare encode-stream)

(defn- encode-message-field-value [schema stream v]
  (let [sub-stream (ByteArrayOutputStream.)]
    (encode-stream schema sub-stream v)
    (CodecUtil/writeVarint32 stream (.size sub-stream))
    (.writeTo sub-stream stream)))

(defn- encode-field-value [schema ^ByteArrayOutputStream stream field v]
  (case (:type field)
    :uint32   (CodecUtil/writeUnsigned32 stream (unchecked-int  v))
    :uint64   (CodecUtil/writeUnsigned64 stream (unchecked-long v))

    :int32    (CodecUtil/writeInt32      stream (unchecked-int  v))
    :int64    (CodecUtil/writeInt64      stream (unchecked-long v))

    :sint32   (CodecUtil/writeSigned32   stream (unchecked-int  v))
    :sint64   (CodecUtil/writeSigned64   stream (unchecked-long v))

    :sfixed32 (CodecUtil/writeFixed32    stream (unchecked-int  v))
    :sfixed64 (CodecUtil/writeFixed64    stream (unchecked-long v))

    :bool     (CodecUtil/writeBoolean    stream v)
    :float    (CodecUtil/writeFloat      stream (unchecked-float  v))
    :double   (CodecUtil/writeDouble     stream (unchecked-double v))
    :bytes    (encode-byte-array stream v)
    :string   (encode-byte-array stream (.getBytes ^String v ^Charset utf8))
    (encode-message-field-value schema stream v)))

(defn- encode-packed-field [schema stream field v]
  (when-not (empty? v)
    (CodecUtil/writeVarint32 stream (field->tag (assoc field
                                                  :wire-type repeated-wire-type)))
    (let [sub-stream (ByteArrayOutputStream.)]
      (doseq [item v]
        (encode-field-value schema sub-stream field item))
      (CodecUtil/writeVarint32 stream (.size sub-stream))
      (.writeTo sub-stream stream))))

(defn- encode-field [schema stream field v]
  (if (:stickler/repeated? field)
    (if (:packed? field)
      (encode-packed-field schema stream field v)
      (doseq [item v]
        (encode-field schema stream (dissoc field :stickler/repeated?) item)))
    (do
      (CodecUtil/writeVarint32 stream (field->tag field))
      (encode-field-value schema stream field v))))

(declare decode-bytes)

(defn- decode-message-field-value [schema ^ByteArrayInputStream stream field]
  {:pre [(:type field)]}
  (let [len  (CodecUtil/readVarint32 stream)
        body (byte-array len)]
    (.read stream body 0 len)
    (decode-bytes schema (:type field) body)))

(defn- decode-field-value [schema ^ByteArrayInputStream stream field & [len]]
  (case (:type field)
    :uint32   (CodecUtil/readUnsigned32 stream)
    :uint64   (CodecUtil/readUnsigned64 stream)

    :int32    (CodecUtil/readInt32      stream)
    :int64    (CodecUtil/readInt64      stream)

    :sint32   (CodecUtil/readSigned32   stream)
    :sint64   (CodecUtil/readSigned64   stream)

    :sfixed32 (CodecUtil/readFixed32    stream)
    :sfixed64 (CodecUtil/readFixed64    stream)

    :bool     (CodecUtil/readBoolean    stream)
    :float    (CodecUtil/readFloat      stream)
    :double   (CodecUtil/readDouble     stream)
    :bytes    (decode-byte-array stream len)
    :string   (String. ^bytes (decode-byte-array stream len) ^Charset utf8)
    (decode-message-field-value schema stream field)))

(defn- decode-packed-field [schema ^ByteArrayInputStream stream field]
  (let [size (CodecUtil/readVarint32 stream)
        bs   (byte-array size)]
    (.read stream bs 0 size)
    (let [sub-stream (ByteArrayInputStream. bs)]
      (loop [vs []]
        (if (zero? (.available sub-stream))
          vs
          (let [v (decode-field-value schema sub-stream field size)]
            (recur (conj vs v))))))))

(defn- decode-field [schema ^ByteArrayInputStream stream field]
  (if (:stickler/repeated? field)
    (if (:packed? field)
      (decode-packed-field schema stream field)
      (recur schema stream (dissoc field :stickler/repeated?)))
    (decode-field-value schema stream field)))

(defn encode-stream [schema stream msg]
  (let [t-schema (verify-encode schema msg)]
    (reduce-kv
     (fn [one-ofs k field]
       (if (identical? (namespace k) :stickler)
         one-ofs
         (if-some [v (msg k)]
           (if-let [one-of (and (:stickler/one-of? field) field)]
             (if (one-ofs one-of)
               one-ofs
               (do
                 (encode-field schema stream field v)
                 (conj one-ofs one-of)))
             (do
               (encode-field schema stream field v)
               one-ofs))
           one-ofs)))
     #{}
     t-schema)))

(defn encode-bytes [schema msg]
  (let [baos (ByteArrayOutputStream.)]
    (encode-stream schema baos msg)
    (.toByteArray baos)))

(defn- decoded-one-of [field m]
  (let [one-of   (:one-of field)
        one-of-k (keyword "stickler.one-of" (name one-of))]
    (-> m
        (dissoc (one-of-k m))
        (assoc one-of-k (:name field)))))

(defn- decode-stream [schema stream t]
  (let [t-schema (verify-schema schema t)]
    (loop [m {:stickler/msg t}]
      (if-let [field (stream->field t-schema stream)]
        (let [v        (decode-field schema stream field)
              one-of   (:one-of field)
              m        (if (and (:stickler/repeated? field) (not (:packed? field)))
                         (update m (:name field) (fnil conj []) v)
                         (assoc  m (:name field)                v))]
          (recur (cond->> m (:one-of field) (decoded-one-of field))))
        m))))

(defn decode-bytes [schema t body]
  (let [bais (ByteArrayInputStream. body)]
    (decode-stream schema bais t)))

(defn- sorted-map-by-tag [fields]
  (into
    (sorted-map-by
     (fn [a b]
       (let [t-a (-> fields a :tag)
             t-b (-> fields b :tag)]
         (if (or (keyword? t-a) (keyword? t-b))
           -1
           (compare t-a t-b)))))
    fields))

(let [msg? (every-pred map? (some-fn :stickler/msg? :stickler/enum?))]
  (defn prepare-schema [schema]
    (walk/postwalk
     (fn [m]
       (cond (msg? m)
             (let [t->f (into {}
                          (for [[k v] m :when (map? v)]
                            [(:tag v) (assoc v :name k)]))
                   m    (into {}
                          (for [[k v] m :when (map? v)]
                            {k (assoc v :name k)}))]
               (-> m
                   (assoc :tag->field t->f)
                   sorted-map-by-tag))
             (:type m)
             (if-let [wt (type->wire-type (:type m))]
               (assoc m :wire-type wt)
               m)
             :else m))
     schema)))
