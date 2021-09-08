# stickler

Clojure-idiomatic Google Protocol Buffers / protobuf3 schema generation & codec functionality.

## Subprojects

### [org.datopia/stickler-translate](translate)

protobuf->edn schema generation library.

[![Clojars
Project](http://clojars.org/io.datopia/stickler-translate/latest-version.svg)](http://clojars.org/io.datopia/stickler-translate)

`stickler-translate` relies on Square's [Wire](https://github.com/square/wire)
project, capturing information from its representation of the parsed protocol
specification. It then generates an EDN map consumable by `stickler-codec` to
encode or decode messages (to/from plain maps). `stickler-codec` has no external
dependencies, and you'll never have to deal with generated code/interop. `lein
run <input-dir> ...` will output EDN (to stdout) for all protobuf files under
the input dirs. This functionality is exposed as part of an API which includes
helpful utility functions, per its
[documentation](https://datopia.github.io/stickler/stickler-translate/).

Note that without pruning, a lot of extraneous definitions of protocol buffers'
internals are included in the EDN, as Wire's representation of the protocol
dependency graph includes them.  It would be hacky and brittle to remove them
on our side,  `stickler.translate/prune-schema` can do this for you.

#### Example

`protodir/test.proto`:

```protocol-buffer
syntax = "proto3";

package test;

message Nest {
  Egg egg = 1;

  message Egg {
    EggSize size = 1;

    enum EggSize {
      SMALL = 0;
      MED   = 1;
      LARGE = 2;
    }
  }
}
```

#### Pruned Output EDN:

```clojure
{:egg.test/Nest
 {:fields
  {:egg
   {:tag       1
    :type      :egg.test.Nest/Egg
    :wire-type 2}}}

 :egg.test.Nest/Egg
 {:fields
  {:size
   {:tag       1
    :type      :egg.test.Nest.Egg/EggSize
    :wire-type 2}}}

 :egg.test.Nest.Egg/EggSize
 {:stickler/enum? true
  :SMALL          0
  :MED            1
  :LARGE          2}}
```

### [org.datopia/stickler-codec](codec)

Runtime codec library.

[![Clojars
Project](http://clojars.org/io.datopia/stickler-codec/latest-version.svg)](http://clojars.org/io.datopia/stickler-codec)

`stickler-codec`, given access to an EDN prototocol as generated by
`stickler-translate`, encodes/decodes messages to/from their wire-level
representation. If your protocol definitions are frequently changing, it may
make sense to include `stickler.translate` as a dependency in your `:dev`
profile (if using Leiningen), and to perhaps add an alias which invokes it.

```clojure
(ns example
  (:require [clojure.java.io :as io]
            [stickler.codec  :as c]))

(def schema (-> "nested-egg.edn" io/resource c/prepare-schema))

(c/encode-bytes
 schema
 {:stickler/msg :test/Nest
  :egg           {:stickler/msg :test.Nest/Egg
                  :size         :MEDIUM}}) ;; -> output-bytes

(c/decode-bytes schema :test/Nest output-bytes) ;; =>

{:stickler/msg :test/Nest
  :egg           {:stickler/msg :test.Nest/Egg
                  :size         :MEDIUM}}
```

`decode-stream` and `encode-stream` are also available, see the [documentation](https://datopia.github.io/stickler/stickler-translate/) for more details.

## Missing Features

 * Maps aren't supported.  I'll implement them if anyone asks.

## License

[MIT](LICENSE)
