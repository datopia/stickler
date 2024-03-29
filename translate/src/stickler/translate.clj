(ns stickler.translate
  "Convert directories of-disk protobuf3 files into EDN schemas suitable for use
  by `org.datopia/sticker-codec`."
  (:require [clojure.java.io         :as    io]
            [stickler.translate.util :refer [assoc-when]]
            [clojure.walk            :as    walk]
            [clojure.string          :as    str]
            [clojure.pprint          :refer [pprint]])
  (:import [com.squareup.wire.schema
            Schema SchemaLoader ProtoType OneOf ProtoMember Type])
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
    (reduce
     (fn conv-enum [m ^com.squareup.wire.schema.EnumConstant c]
       (let [k (-> c .name ->constant-name keyword)]
         (assoc-in m [:fields k] (.tag c))))
     {:enum? true}
     (.constants enum))))

(defn- convert-proto-file [^com.squareup.wire.schema.ProtoFile f]
  (reduce
   (fn reduce-top-levels [acc ^Type t]
     (reduce
      (partial apply assoc)
      acc
      (map
       (fn reduce-top-level-and-nested [t]
         [(proto+type->key f t)
          (-convert-type   t f)])
       (into [t] (.nestedTypes t)))))
   {}
   (.types f)))

(defn- unfuck-enums [schema]
  (let [enums (into #{} (for [[k v] schema :when (:enum? v)] k))]
    (walk/postwalk
     (fn [form]
       (if (and (map? form) (enums (:type form)))
         (assoc form :wire-type (type->wire-type :enum))
         form))
     schema)))

(defn Schema->edn
  "Convert the given `schema` to a map suitable for use by `stickler-codec`."
  [^Schema schema]
  (->> schema
       .protoFiles
       (map convert-proto-file)
       (apply merge)
       unfuck-enums))

(defn- direct-deps [schema k]
  (when-not (:enum? (k schema))
    (for [v (-> k schema :fields vals) :when (not (:scalar? v))]
      (:type v))))

(defn prune-edn [schema {incl :include}]
  (let [incl (map keyword incl)
        deps (loop [[k & incl] incl
                    acc        []]
               (if (not k)
                 acc
                 (let [deps (direct-deps schema k)]
                   (recur (into incl deps) (into acc deps)))))]
    (select-keys schema (into deps incl))))

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

(defn translate [& argv]
  (Schema->edn (apply dirs->Schema argv)))

(defn -main [& args]
  (pprint (apply translate args)))
