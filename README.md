# demo-kafka-connect

Key deliverables:

- Moving data from a source Kafka topic to S3 with Kafka Connect
  - Message `keys` in the Kafka topic are regular strings, whereas `values` are Avro-serialized
    in [Confluent Wire Format](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html#wire-format)
  - Data in S3 is stored in JSON file format (see [config/kafka-connect.http](config/kafka-connect.http))
- Example of custom schema registration in Confluent Schema Registry (
  see [config/schema-registry.http](config/schema-registry.http))
- Custom Kafka
  Connect [InsertSchemaMetadata](demo-connect-transforms/src/main/kotlin/com/github/dkoval/hackeda/kafka/connect/transforms/InsertSchemaMetadata.kt)
  SMT responsible for adding `value` schema metadata fields to the Kafka Connect `SinkRecord`
