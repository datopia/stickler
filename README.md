# stickler

Clojure-idiomatic protobuf3 schema generation & codec functionality.

The [Leiningen](https://leiningen.org/) plugin [org.datopia/lein-stickler](lein-stickler) uses [Wire](https://github.com/square/wire) (via [org.datopia/stickler-translate](translate)) to parse protobuf3 files, outputting an EDN map describing the schema.

Your project need only depend on
[org.datopia/stickler-codec](stickler-codec), a zero-dependency protobuf3<->EDN3 codec. When
provided with an appropriate EDN schema, it can encode and decode arbitrary
protobuf3 messages as maps.

## Getting Started

Add the plugin `[org.datopia/lein-stickler "0.1.1"]` to your Leiningen
[profile](https://github.com/technomancy/leiningen/blob/master/doc/PROFILES.md).
You _could_ add it to your project's plugins, however it doesn't require
invocation within a lein project, and your protobuf3 files may not be contained
within one, so a profile is probably the simplest bet. `lein stickler` requires
a `:dirs` argument, all of which will be recursively searched for protobuf
files. The resulting EDN is printed to stdout.

Let's imagine we've a directory `~/proto` containing the file `test.proto`:

```protobuf
syntax = "proto3"

package my.test;

message Nested {
  Inner inner = 1;

  message Inner {
    bool inner = 1;
  }
}
```

Let's have a look:

``` sh
~$ lein stickler :dirs proto

```
```edn

{:my.test.Nested/Inner
 {:fields {:inner {:tag       1
                   :scalar?   true
                   :type      :bool
                   :wire-type 0}}}
 :my.test/Nested
 {:fields {:inner {:tag       1
                   :type      :my.test.Nested/Inner
                   :wire-type 2}}}}

```

Wire's dependency resolution is a little messed up, so your output may include a
bunch of extraneous meta-protobuf Google messages, You can constrain the output
to only the transitive dependencies of one or more protobuf types using the
optional `:include` argument. Separately `:strip-ns` will strip a leading prefix
off of protobuf package names --- the names aren't ever sent over the wire, so this
is just a matter of deciding how you'd like to refer to your messages.

```sh
$ lein stickler :dirs proto :include my.test/Nested :strip-ns my
```
```edn
{:test.Nested/Inner
 {:fields {:inner {:tag       1
                   :scalar?   true
                   :type      :bool
                   :wire-type 0}}}
 ...}
```

## Missing Features
 - Maps are not supported.  I can add support for this if anyone's interested.

## Subprojects

### [org.datopia/stickler-translate](translate)

protobuf->edn schema generation library.

[![Clojars
Project](http://clojars.org/org.datopia/stickler-translate/latest-version.svg)](http://clojars.org/org.datopia/stickler-translate)

### [org.datopia/lein-stickler](lein-stickler)

protobuf->edn schema generation library.

[![Clojars
Project](http://clojars.org/org.datopia/lein-stickler/latest-version.svg)](http://clojars.org/org.datopia/lein-stickler)

### [org.datopia/stickler-codec](codec)

Runtime codec library.

[![Clojars
Project](http://clojars.org/org.datopia/stickler-codec/latest-version.svg)](http://clojars.org/org.datopia/stickler-codec)

## Contributors

- Moe Aboulkheir

## License

[MIT](LICENSE)
