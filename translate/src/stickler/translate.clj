(ns stickler.translate
  "Convert directories of-disk protobuf3 files into EDN schemas suitable for use
  by `io.datopia/sticker-codec`."
  (:require [clojure.java.io :as io]
            [clojure.walk    :as walk]
            [clojure.pprint]
            [clojure.string  :as str]
            [clojure.edn     :as edn]
            [clj-antlr.core  :as antlr]
            [path.path       :as p]
            [path.util       :as p.util]))

(def ^:private grammar-name      "Protobuf3.g4")
(def ^:private f-modifier?       #{"repeated"}) ;; grammar doesn't support "optional"
(def ^:private predicate-options #{"packed"})

;; clj-antlr wants the filename to be identical to the grammar & and won't accept a URI
(let [tmp-dir     (p.util/create-temp-dir "grammar")
      tmp-grammar (p/resolve tmp-dir grammar-name)]
  (p/spit tmp-grammar (slurp (io/resource grammar-name)))
  (def ^:private parser (antlr/parser (str tmp-grammar))))

(let [package-k :packageStatement]
  (defn- package-name [tree]
    (first
     (for [[k v] tree
           :when (identical? k package-k)]
       (apply str v)))))

(let [import-k :importStatement]
  (defn- gather-imports [tree]
    (reduce
     (fn [acc [k v]]
       (if (identical? k import-k)
         (conj acc (p/get (edn/read-string v)))
         acc))
      #{} tree)))

(defmulti ^:private handle-node first)

(defn- ->predicate-k [s]
  (keyword (str s "?")))

(defn- field->options [field]
  (when (seq? (last field))
    (let [[_ & pairs] (last field)]
      (into {}
        (for [[k v] pairs
              :when (predicate-options k)]
          [(->predicate-k k) (edn/read-string v)])))))

(defmethod handle-node :field [[_ & field]]
  (let [[mods  [f-type f-name tag]] (split-with f-modifier? field)
        opts   (field->options field)
        f-type (if (seq? f-type)
                 (keyword (str/join "." (remove #{"."} (butlast f-type))) (last f-type))
                 (keyword f-type))]
    {(keyword f-name) (merge {:type f-type
                              :tag  (Integer/valueOf ^String tag)}
                             (zipmap (map ->predicate-k mods) (repeat true))
                             opts)}))

(defmethod handle-node :enumField [[_ e-name e-tag]]
  {(keyword e-name) (Integer/valueOf ^String e-tag)})

(defmethod handle-node :enumBody [[ _ & fields]]
  (into {:stickler/enum? true} (map handle-node
                                    (filter #(identical? :enumField (first %)) fields))))

(defmethod handle-node :enumDef [[ _ e-name [& tail]]]
  {(keyword e-name) (handle-node tail)})

(defmethod handle-node :oneof [[_ o-name & fields]]
  {(keyword o-name) (into {:stickler/one-of? true}
                      (map handle-node
                           (walk/postwalk-replace {:oneofField :field} fields)))})

(defmethod handle-node :messageDef [[_ msg-name args]]
  {(keyword msg-name) (into {:stickler/msg? (keyword msg-name)}
                        (when (not (identical? args :messageBody))
                          (map handle-node (rest args))))})

(defmethod handle-node :default [node]
  nil)

(let [lift? #{:ident       :type_     :topLevelDef :messageElement :messageType
              :messageName :fieldName :fieldOption :enumElement    :optionName
              :fieldNumber :enumName  :fullIdent   :constant       :intLit
              :strLit      :keywords  :oneofName}
      tokens #{"{" "}" "[" "]" "=" ";" "\"" "message" "enum" "oneof" "package" "import"}]
  (defn- parse-file [f]
    (let [[_ & tree] (parser (p/slurp f))
          tree       (walk/postwalk
                      (fn parse-tree-walker [v]
                        (if (seq? v)
                          (let [v (remove tokens (cond-> v (lift? (first v)) rest))]
                            (cond-> v (= 1 (count v)) first))
                          v))
                      tree)
          schema     (into {} (filter map? (map handle-node tree)))
          package    (package-name tree)]
      {:schema  schema
       :imports (gather-imports tree)
       :package package})))

(defn- resolve-import [paths import importer]
  (if (p/absolute? import)
    (if (p/file? import)
      import
      (throw (ex-info (str "Non-existent absolute import" import) {:import   import
                                                                   :importer importer})))
    (let [candidate (p/resolve importer import)]
      (if (p/file? candidate)
        candidate
        (let [candidate (p/resolve-sibling importer import)]
          (if (p/file? candidate)
            candidate
            (if-let [path (->> paths (map #(p/resolve % import)) (filter p/file?) first)]
              path
              (throw (ex-info (str "Can't resolve import " import) {:import   import
                                                                    :importer importer})))))))))

(defn- merge-by-package [schema schema' package]
  (update-in schema (map keyword (str/split package #"\.")) merge schema'))

(defn- parse-all [{:keys [files paths ignore?]}]
  (loop [schema         {}
         [file & files] files]
    (if-not file
      schema
      (let [{schema' :schema
             imports :imports
             package :package} (parse-file file)
            new-imports        (for [import (remove ignore? imports)]
                                 (resolve-import paths import file))]
        (recur (merge-by-package schema schema' package)
               (into files new-imports))))))

(def ^:private proto-xform
  (mapcat
   (fn [d]
     (filter
      #(str/ends-with? (p/name %) ".proto")
      (p/path-seq d)))))

(defn- ->ignore-fn [strs]
  (apply some-fn (map (fn [s]
                        #(str/includes? (str %) s)) strs)))

(defn translate [{:keys [dirs files paths ignores]}]
  (let [files     (into #{} (map p/get) files)
        ignore-fn (if ignores
                    (->ignore-fn ignores)
                    (constantly false))]
    (doseq [p files]
      (when-not (p/exists? p)
        (throw (ex-info (str "File " (str p) " doesn't exist") {:path p})))
      (when-not (p/file? p)
        (throw (ex-info (str (str p) " is not a file.") {:path p}))))
    (parse-all {:files   (remove ignore-fn (into files proto-xform dirs))
                :paths   (->> (map p/get paths) (concat (map p/get dirs)) (into #{}))
                :ignore? ignore-fn})))
