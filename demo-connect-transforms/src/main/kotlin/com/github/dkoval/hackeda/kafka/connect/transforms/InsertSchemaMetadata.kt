package com.github.dkoval.hackeda.kafka.connect.transforms

import org.apache.kafka.common.cache.Cache
import org.apache.kafka.common.cache.LRUCache
import org.apache.kafka.common.cache.SynchronizedCache
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.connect.data.Schema
import org.apache.kafka.connect.data.SchemaBuilder
import org.apache.kafka.connect.data.Struct
import org.apache.kafka.connect.sink.SinkRecord
import org.apache.kafka.connect.transforms.Transformation
import org.apache.kafka.connect.transforms.util.Requirements.requireStruct
import org.apache.kafka.connect.transforms.util.SchemaUtil
import org.apache.kafka.connect.transforms.util.SimpleConfig
import org.slf4j.LoggerFactory

/**
 * Inserts schema metadata, including `subject` and `version` fields, into a `ConnectRecord`.
 *
 * The implementation is inspired by `org.apache.kafka.connect.transforms.InsertField` Kafka Connect SMT.
 */
abstract class InsertSchemaMetadata : Transformation<SinkRecord> {

    companion object {
        const val SCHEMA_FIELD_PROP = "schema.field"
        const val SCHEMA_FIELD_DEFAULT = "__schema__"

        const val SCHEMA_MAX_CACHE_SIZE_PROP = "schema.maxCacheSize"
        const val SCHEMA_MAX_CACHE_SIZE_DEFAULT = 16

        val SCHEMA_METADATA_SCHEMA: Schema = SchemaBuilder.struct()
            .field("subject", Schema.STRING_SCHEMA)
            .field("version", Schema.OPTIONAL_INT32_SCHEMA)
            .build()

        val CONFIG_DEF: ConfigDef = ConfigDef()
            .define(
                SCHEMA_FIELD_PROP,
                ConfigDef.Type.STRING,
                SCHEMA_FIELD_DEFAULT,
                ConfigDef.Importance.MEDIUM,
                "Field name for schema metadata."
            )
            .define(
                SCHEMA_MAX_CACHE_SIZE_PROP,
                ConfigDef.Type.INT,
                SCHEMA_MAX_CACHE_SIZE_DEFAULT,
                ConfigDef.Importance.LOW,
                "Max cache size for schema metadata. Defaults to $SCHEMA_MAX_CACHE_SIZE_DEFAULT."
            )
    }

    private val logger = LoggerFactory.getLogger(javaClass)
    private var schemaField: String? = null
    private var schemaUpdateCache: Cache<Schema, Schema>? = null

    override fun configure(props: Map<String, *>) {
        val config = SimpleConfig(CONFIG_DEF, props)
        schemaField = config.getString(SCHEMA_FIELD_PROP)
        schemaUpdateCache = SynchronizedCache(LRUCache(config.getInt(SCHEMA_MAX_CACHE_SIZE_PROP)))
    }

    override fun apply(record: SinkRecord): SinkRecord =
        if (shouldInsertSchemaMetadata(record)) applyWithSchema(record) else record

    private fun shouldInsertSchemaMetadata(record: SinkRecord): Boolean {
        operatingValue(record) ?: return false
        val schema = operatingSchema(record) ?: return false
        if (schema.name().isNullOrBlank()) {
            logger.warn(
                "Record with key = {}, partition = {}, offset = {} has unnamed schema. Schema metadata won't be inserted.",
                record.key(),
                record.kafkaPartition(),
                record.kafkaOffset()
            )
            return false
        }
        return true
    }

    private fun applyWithSchema(record: SinkRecord): SinkRecord {
        val originalValue = requireStruct(operatingValue(record), "schema fields insertion")
        val originalSchema = originalValue.schema()

        val updatedSchema = schemaUpdateCache!!.get(originalSchema)
            ?: makeUpdatedSchema(originalSchema).also { schemaUpdateCache!!.put(originalSchema, it) }

        val updatedValue = originalSchema.fields()
            .fold(Struct(updatedSchema)) { acc, field ->
                acc.put(field.name(), originalValue.get(field))
            }

        // include schema metadata fields
        schemaField?.also {
            val schemaMetadata = Struct(SCHEMA_METADATA_SCHEMA)
                .put("subject", originalSchema.name())
                .put("version", originalSchema.version())

            updatedValue.put(it, schemaMetadata)
        }

        return newRecord(record, updatedSchema, updatedValue)
    }

    private fun makeUpdatedSchema(schema: Schema): Schema {
        val builder = schema.fields()
            .fold(SchemaUtil.copySchemaBasics(schema, SchemaBuilder.struct())) { acc, field ->
                acc.field(field.name(), field.schema())
            }

        // append schema metadata to the original schema
        schemaField?.also {
            builder.field(it, SCHEMA_METADATA_SCHEMA)
        }

        return builder.build()
    }

    override fun config(): ConfigDef = CONFIG_DEF

    override fun close() {
        schemaField = null
        schemaUpdateCache = null
    }

    protected abstract fun operatingValue(record: SinkRecord): Any?
    protected abstract fun operatingSchema(record: SinkRecord): Schema?
    protected abstract fun newRecord(record: SinkRecord, updatedSchema: Schema, updatedValue: Any): SinkRecord

    class Value : InsertSchemaMetadata() {

        override fun operatingValue(record: SinkRecord): Any? = record.value()

        override fun operatingSchema(record: SinkRecord): Schema? = record.valueSchema()

        override fun newRecord(record: SinkRecord, updatedSchema: Schema, updatedValue: Any): SinkRecord =
            record.newRecord(
                record.topic(),
                record.kafkaPartition(),
                record.keySchema(),
                record.key(),
                updatedSchema,
                updatedValue,
                record.timestamp()
            )
    }
}
