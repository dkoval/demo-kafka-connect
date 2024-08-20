# demo-kafka-connect

Key deliverables:

- Moving data from a Kafka topic to S3 with Kafka Connect
    - Message `keys` in the Kafka topic are regular strings, whereas `values` are Avro-serialized and then encoded in [Confluent Wire Format](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html#wire-format)
    - Data in S3 is stored in [Avro OCF](https://en.wikipedia.org/wiki/Apache_Avrohttps://en.wikipedia.org/wiki/Apache_Avro) file format
- Example of custom schema registration in Confluent Schema Registry
