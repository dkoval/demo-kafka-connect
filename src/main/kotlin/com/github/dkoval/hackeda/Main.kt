package com.github.dkoval.hackeda

import org.apache.kafka.clients.producer.ProducerConfig

private const val BOOTSTRAP_SERVERS = "localhost:9092"
private const val INPUT_TOPIC = "user.assigned"

private val producerProps = mapOf(
    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to BOOTSTRAP_SERVERS
)

fun main() {
    val key = "8c4e627d-61ef-4a21-8631-331a0d5b1422"

    // send a binary message in Confluent Wire format
    // https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html#wire-format
    val message = byteArrayOf(
        0, 118, 74, 10, 121, 72, 56, 99, 52, 101, 54, 50, 55, 100, 45, 54, 49,
        101, 102, 45, 52, 97, 50, 49, 45, 56, 54, 51, 49, 45, 51, 51, 49, 97,
        48, 100, 53, 98, 49, 52, 50, 50, 16, 74, 111, 104, 110, 32, 68, 111, 101
    )

    SimpleKafkaProducer(producerProps).use { producer ->
        repeat(10) {
            // produce messages asynchronously
            producer.send(INPUT_TOPIC, key, message)
        }
        // makes all buffered records immediately available to send
        producer.flush()
    }
}