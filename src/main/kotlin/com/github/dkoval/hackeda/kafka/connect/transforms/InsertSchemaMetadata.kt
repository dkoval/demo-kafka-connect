package com.github.dkoval.hackeda.kafka.connect.transforms

import org.apache.kafka.common.cache.Cache
import org.apache.kafka.common.cache.LRUCache
import org.apache.kafka.common.cache.SynchronizedCache
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.connect.connector.ConnectRecord
import org.apache.kafka.connect.data.Schema
import org.apache.kafka.connect.data.SchemaBuilder
import org.apache.kafka.connect.data.Struct
import org.apache.kafka.connect.transforms.Transformation
import org.apache.kafka.connect.transforms.util.Requirements.requireStruct
import org.apache.kafka.connect.transforms.util.SchemaUtil
import org.apache.kafka.connect.transforms.util.SimpleConfig

/**
 * Inserts schema information, including `name` and `version` fields, into a `ConnectRecord`.
 *
 * The implementation is inspired by `org.apache.kafka.connect.transforms.InsertField` Kafka Connect SMT.
 */
abstract class InsertSchemaMetadata<R : ConnectRecord<R>> : Transformation<R> {

    companion object {
        const val SCHEMA_FIELD_PROP = "schema.field"
        const val SCHEMA_FIELD_DEFAULT = "__schema__"

        val SCHEMA_INFO_SCHEMA: Schema = SchemaBuilder.struct()
            .field("name", Schema.STRING_SCHEMA)
            .field("version", Schema.OPTIONAL_INT32_SCHEMA)
            .build()

        val CONFIG_DEF: ConfigDef = ConfigDef()
            .define(
                SCHEMA_FIELD_PROP,
                ConfigDef.Type.STRING,
                SCHEMA_FIELD_DEFAULT,
                ConfigDef.Importance.MEDIUM,
                "Field name for schema information."
            )
    }

    private var schemaInfoField: String? = null
    private var schemaUpdateCache: Cache<Schema, Schema>? = null

    override fun configure(props: Map<String, *>) {
        val config = SimpleConfig(CONFIG_DEF, props)
        schemaInfoField = config.getString(SCHEMA_FIELD_PROP)
        schemaUpdateCache = SynchronizedCache(LRUCache(16))
    }

    override fun apply(record: R): R =
        if (shouldInsertSchemaInfo(record)) applyWithSchema(record) else record

    private fun shouldInsertSchemaInfo(record: R): Boolean {
        operatingValue(record) ?: return false
        operatingSchema(record)?.name() ?: return false
        return true
    }

    private fun applyWithSchema(record: R): R {
        val originalValue = requireStruct(operatingValue(record), "schema fields insertion")
        val originalSchema = originalValue.schema()

        val updatedSchema = schemaUpdateCache!!.get(originalSchema)
            ?: makeUpdatedSchema(originalSchema).also { schemaUpdateCache!!.put(originalSchema, it) }

        val updatedValue = originalSchema.fields()
            .fold(Struct(updatedSchema)) { acc, field ->
                acc.put(field.name(), originalValue.get(field))
            }

        // include schema name and schema version fields
        schemaInfoField?.also {
            val schemaInfo = Struct(SCHEMA_INFO_SCHEMA)
                .put("name", originalSchema.name())
                .put("version", originalSchema.version())

            updatedValue.put(it, schemaInfo)
        }

        return newRecord(record, updatedSchema, updatedValue)
    }

    private fun makeUpdatedSchema(schema: Schema): Schema {
        val builder = schema.fields()
            .fold(SchemaUtil.copySchemaBasics(schema, SchemaBuilder.struct())) { acc, field ->
                acc.field(field.name(), field.schema())
            }

        // append schema information to the original schema
        schemaInfoField?.also {
            builder.field(it, SCHEMA_INFO_SCHEMA)
        }

        return builder.build()
    }

    override fun config(): ConfigDef = CONFIG_DEF

    override fun close() {
        schemaInfoField = null
        schemaUpdateCache = null
    }

    protected abstract fun operatingValue(record: R): Any?
    protected abstract fun operatingSchema(record: R): Schema?
    protected abstract fun newRecord(record: R, updatedSchema: Schema, updatedValue: Any): R

    class Value<R : ConnectRecord<R>> : InsertSchemaMetadata<R>() {

        override fun operatingValue(record: R): Any? = record.value()

        override fun operatingSchema(record: R): Schema? = record.valueSchema()

        override fun newRecord(record: R, updatedSchema: Schema, updatedValue: Any): R =
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
