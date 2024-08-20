package com.github.dkoval.hackeda

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.io.Closeable

class SimpleKafkaProducer(props: Map<String, Any>) : Closeable {

    companion object {
        private val logger = LoggerFactory.getLogger(SimpleKafkaProducer::class.java)
    }

    private val producer: Producer<String, ByteArray> = KafkaProducer(props, StringSerializer(), ByteArraySerializer())

    fun send(topic: String, key: String, message: ByteArray) {
        producer.send(ProducerRecord(topic, key, message)) { metadata, error ->
            if (error != null) {
                logger.error("Could not send message to $topic topic", error)
            } else {
                logger.info(
                    "Successfully sent message to topic: {}, partition: {}, offset: {}",
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset()
                )
            }
        }
    }

    fun flush() {
        producer.flush()
    }

    override fun close() {
        producer.close()
    }
}