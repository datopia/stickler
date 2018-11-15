(ns stickler.codec-test.util
  (:import [stickler.test Scalars$Builder]
           [java.util Arrays]))

;; taken from encore
(defmacro ^:private doto-cond [[name x] & clauses]
  (let [g     (gensym)
        pstep (fn [[test-expr step]]
                `(when-let [~name ~test-expr]
                   (-> ~g ~step)))]
    `(let [~g ~x]
       ~@(map pstep (partition 2 clauses))
       ~g)))

(defn- ^Scalars$Builder map->ScalarsBuilder [m]
  (doto-cond [b (Scalars$Builder.)]
    (:uint32   m) (.uint32   (:uint32 m))
    (:uint64   m) (.uint64   (:uint64 m))

    (:int32    m) (.int32    (:int32  m))
    (:int64    m) (.int64    (:int64  m))

    (:sint32   m) (.sint32   (:sint32 m))
    (:sint64   m) (.sint64   (:sint64 m))

    (:sfixed32 m) (.sfixed32 (:sfixed32 m))
    (:sfixed64 m) (.sfixed64 (:sfixed64 m))

    (:bytes    m) (.bytes    (okio.ByteString/of ^bytes (:bytes m)))
    (:string   m) (.string   (:string m))
    (:float    m) (.float_   (:float  m))
    (:double   m) (.double_  (:double m))

    (contains? m :bool) (.bool (:bool m))))

(defn map->Scalars [m]
  (.build (map->ScalarsBuilder m)))

(defn float= [x y]
  (or (= x y) (and (Float/isNaN x) (Float/isNaN y))))

(defn double= [x y]
  (or (= x y) (and (Double/isNaN x) (Double/isNaN y))))

(defn msg= [x y]
  (let [x' (dissoc x :bytes :float :double)
        y' (dissoc y :bytes :float :double)]
    (and (= x' y')
         (Arrays/equals ^bytes (:bytes x) ^bytes (:bytes y))
         (float=  (:float  x) (:float  y))
         (double= (:double x) (:double y)))))
