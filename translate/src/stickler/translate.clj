(ns stickler.translate
  "Convert directories of-disk protobuf3 files into EDN schemas suitable for use
  by `io.datopia/sticker-codec`."
  (:require [clojure.java.io         :as    io]
            [stickler.translate.util :refer [assoc-when]]
            [clojure.walk            :as    walk]
            [clojure.string          :as    str]
            [clojure.pprint          :refer [pprint]])
  (:import [com.squareup.wire.schema
            Schema SchemaLoader ProtoType OneOf IdentifierSet$Builder ProtoMember Type])
  (:gen-class))

(defn- un-underscore [s]
  (str/replace (name s) #"_" "-"))

(def ^:dynamic ->field-name    (comp keyword un-underscore))
(def ^:dynamic ->type-name     identity)

(let [scalar-types #{ProtoType/BOOL     ProtoType/BYTES   ProtoType/DOUBLE
                     ProtoType/FLOAT    ProtoType/FIXED32 ProtoType/FIXED64
                     ProtoType/INT32    ProtoType/INT64   ProtoType/SFIXED32
                     ProtoType/SFIXED64 ProtoType/SINT32  ProtoType/SINT64
                     ProtoType/STRING   ProtoType/UINT32  ProtoType/UINT64}]
  (def ^:private scalar-proto-type->key
    (zipmap scalar-types (map (comp keyword str) scalar-types))))

(def ^:private wire-type->type
  {0 #{:int32   :int64    :uint32 :uint64 :sint32 :sint64 :bool :enum}
   1 #{:fixed64 :sfixed64 :double}
   2 #{:string  :bytes}
   5 #{:fixed32 :sfixed32 :float}})

(def ^:private msg-wire-type 2)

(def ^:private type->wire-type
  (into {}
    (for [[k vs] wire-type->type
          v  vs]
      [v k])))

(defn- proto->package-name [^com.squareup.wire.schema.ProtoFile proto]
  (.packageName proto))

(defn- type->simple-name [t]
  (-> t .type .simpleName))

(defn- proto+type->key [proto t]
  (let [package   (proto->package-name proto)
        t-name    (->type-name (type->simple-name t))
        enclosing (.enclosingTypeOrPackage ^ProtoType (.type t))]
    (if (= package enclosing)
      (keyword package   t-name)
      (keyword enclosing t-name))))

(let [packed      (ProtoMember/get "google.protobuf.FieldOptions#packed")
      unpackable? #{ProtoType/BYTES ProtoType/STRING}]
  (defn- packed? [^com.squareup.wire.schema.Field f]
    (let [t (.type f)]
      (when (and (.isRepeated f)
                 (.isScalar   t)
                 (not (unpackable? t)))
        (not= "false" (.get (.options f) packed))))))

(defn- convert-field [proto ^com.squareup.wire.schema.Field f]
  (let [m (assoc-when {:tag (.tag f)}
            :repeated? (.isRepeated f)
            :packed?   (packed? f))
        t (.type f)]
    [(->field-name (keyword (.name f)))
     (cond
       (.isScalar t) (let [type-k (scalar-proto-type->key t)]
                       (assoc m
                         :scalar?   true
                         :type      type-k
                         :wire-type (type->wire-type type-k)))
       (.isMap    t) (throw (RuntimeException. "Maps not supported."))
       :else         (assoc m
                       :type      (proto+type->key proto f)
                       :wire-type msg-wire-type))]))

(defn- convert-msg [proto fields one-ofs]
  (let [fields  (into {} (map (partial convert-field proto) fields))
        one-ofs (for [^OneOf one-of one-ofs
                      f      (.fields one-of)
                      :let [one-of-k (keyword (.name one-of))]]
                  (-> (convert-field proto f)
                      (assoc-in [1 :one-of] one-of-k)))]
    (assoc-when {}
      :fields (not-empty (into fields one-ofs)))))

(defn- ->constant-name [s]
  (-> s clojure.string/upper-case (clojure.string/replace \_ \-)))

(defprotocol ConvertType
  (-convert-type [t proto]))

(extend-protocol ConvertType
  com.squareup.wire.schema.MessageType
  (-convert-type [msg proto]
    (convert-msg proto (.fields msg) (.oneOfs msg)))
  com.squareup.wire.schema.EnumType
  (-convert-type [enum _]
    (let [ret (reduce
               (fn conv-enum [m ^com.squareup.wire.schema.EnumConstant c]
                 (let [k (-> c .name ->constant-name keyword)]
                   (assoc m k (.tag c))))
               {:enum? true}
               (.constants enum))]
      ret)))

(defn- convert-proto-file [^com.squareup.wire.schema.ProtoFile f]
  (loop [types      (into '() (.types f))
         name->type {}]
    (if-let [^Type t (peek types)]
      (recur
       (into (pop types) (.nestedTypes t))
       (assoc name->type
         (proto+type->key f t)
         (-convert-type   t f)))
      name->type)))

(defn- map->IdentifierSet [incl-excl]
  (as-> (IdentifierSet$Builder.) ^IdentifierSet$Builder b
    (reduce #(.include ^IdentifierSet$Builder %1 %2) b (:include incl-excl))
    (reduce #(.exclude ^IdentifierSet$Builder %1 %2) b (:exclude incl-excl))
    (.build b)))

(defn prune-Schema
  "Prune the given `schema` such that the sequences of keyword/string
  identifiers in `prune-spec`'s `:include` and `:exclude` keys are
  included/excluded, respectively."
  [^Schema schema prune-spec]
  (.prune schema (map->IdentifierSet prune-spec)))

(defn rename-packages
  "Using the given `str` -> `str` `renames` map, translate `edn-schema` into a
  map with adjusted package names."
  [edn-schema renames]
  (walk/prewalk
   (fn [form]
     (if (keyword? form)
       (if-let [ns (namespace form)]
         (keyword (renames ns ns) (name form))
         form)
       form))
   edn-schema))

(defn Schema->edn
  "Convert the given `schema` to a map suitable for use by `stickler-codec`."
  [^Schema schema]
  (apply merge (map convert-proto-file (.protoFiles schema))))

(defn- dirs->loader [& dirs]
  (reduce
   (fn [^SchemaLoader loader dir]
     (doto loader
       (.addSource (io/file dir))))
   (SchemaLoader.)
   dirs))

(defn dirs->Schema
  "Turn a sequence of `dirs` into a `Schema`.
   See [[prune-Schema]], [[Schema->edn]]."
  [& dirs]
  (.load ^SchemaLoader (apply dirs->loader dirs)))

(defn -main [& argv]
  (pprint (Schema->edn (apply dirs->Schema argv))))
