(ns stickler.codec
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
           [java.nio.charset Charset]
           [io.datopia.stickler CodecUtil]))

(def ^:private tag-type-bits      3)
(def ^:private repeated-wire-type 2)

(defn- verify-schema [schema t]
  (let [t-schema (t schema)]
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

(defn- encode-enum-field-value [schema ^ByteArrayInputStream stream field v]
  {:pre [(keyword? v)]}
  (let [t (:type field)
        m (-> schema t :fields)]
    (CodecUtil/writeVarint32 stream (unchecked-int (m v)))))

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
    (if (:enum? ((:type field) schema))
      (encode-enum-field-value schema stream field v)
      (encode-message-field-value schema stream v))))

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
  (if (:repeated? field)
    (if (:packed? field)
      (encode-packed-field schema stream field v)
      (doseq [item v]
        (encode-field schema stream (dissoc field :repeated?) item)))
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

(defn- decode-enum-field-value [schema ^ByteArrayInputStream stream field]
  (let [x (CodecUtil/readVarint32 stream)
        t (:type field)
        m (-> schema t :tag->kw)]
    (m x)))

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

    (if (:enum? ((:type field) schema))
      (decode-enum-field-value   schema stream field)
      (decode-message-field-value schema stream field))))

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
  (if (:repeated? field)
    (if (:packed? field)
      (decode-packed-field schema stream field)
      (recur schema stream (dissoc field :repeated?)))
    (decode-field-value schema stream field)))

(defn encode-stream [schema stream msg]
  (let [t-schema (verify-encode schema msg)
        fields   (:fields t-schema)]
    (reduce-kv
     (fn [one-ofs k field]
       (if-some [v (k msg)]
         (if-let [one-of (:one-of field)]
           (if (one-of one-ofs)
             one-ofs
             (do
               (encode-field schema stream field v)
               (conj one-ofs one-of)))
           (do
             (encode-field schema stream field v)
             one-ofs))
         one-ofs))
     #{}
     fields)))

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
              m        (if (and (:repeated? field) (not (:packed? field)))
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
         (compare t-a t-b))))
    fields))

(defn- prepare-enums [schema]
  (reduce
   (fn [[msgs enums] [k msg]]
     (if (:enum? msg)
       [msgs (assoc enums k
               (assoc msg :tag->kw (into {}
                                     (for [[k v] (:fields msg)]
                                       [v k]))))]
       [(assoc msgs k msg) enums]))
   [{} {}]
   schema))

(defn- prepare-messages [schema]
  (for [[msg-k msg-schema] schema
        :let [msg-schema (update msg-schema :fields sorted-map-by-tag)
              tag->f
              (into {}
                (for [[field-k {tag :tag :as field}] (:fields msg-schema)]
                  [tag (assoc field :name field-k)]))]]
    [msg-k (assoc msg-schema :tag->field tag->f)]))

(defn prepare-schema [schema]
  (let [[schema enums] (prepare-enums schema)]
    (reduce into {}
            [enums
             (prepare-messages schema)])))
