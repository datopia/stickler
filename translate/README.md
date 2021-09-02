# org.datopia/stickler-translate

 [![Clojars
Project](http://clojars.org/io.datopia/stickler-translate/latest-version.svg)](http://clojars.org/io.datopia/stickler-translate)

Conversion of on-disk protobuf3 files into EDN schemas, suitable as inputs for [org.datopia/stickler-codec](../codec).

## Documentation
 - [API Docs](https://datopia.github.io/stickler/stickler-translate/)

## Testing

Prior to launching a JVM in which to run tests (e.g. REPL or via `lein test`),
run `lein test-prep` to perform the necessary code generation from the test
protobuf schema. Unless the schema has changed, you oughtn'd need to do this
more than once.

## Contributors

- Moe Aboulkheir

## License

[MIT](LICENSE)
