package com.github.dkoval.hackeda.kafka.connect.transforms

import com.github.dkoval.hackeda.kafka.connect.transforms.InsertSchemaMetadata.Companion.SCHEMA_FIELD_DEFAULT
import com.github.dkoval.hackeda.kafka.connect.transforms.InsertSchemaMetadata.Companion.SCHEMA_FIELD_PROP
import com.github.dkoval.hackeda.kafka.connect.transforms.InsertSchemaMetadata.Companion.SCHEMA_METADATA_SCHEMA
import org.apache.kafka.connect.data.Schema
import org.apache.kafka.connect.data.SchemaBuilder
import org.apache.kafka.connect.data.Struct
import org.apache.kafka.connect.sink.SinkRecord
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.stream.Stream

internal class InsertSchemaMetadataTest {

    class TransformProps : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> = Stream.of(
            Arguments.of(
                mapOf<String, Any>(
                    SCHEMA_FIELD_PROP to "__schema_info__",
                )
            ),
            Arguments.of(
                emptyMap<String, Any>()
            )
        )
    }

    private val transform = InsertSchemaMetadata.Value()

    @AfterEach
    fun tearDown() {
        transform.close()
    }

    @ParameterizedTest
    @ArgumentsSource(TransformProps::class)
    fun `should copy schema and and insert schema metadata fields`(props: Map<String, *>) {
        // schema field to be inserted
        val schemaField = (props[SCHEMA_FIELD_PROP] ?: SCHEMA_FIELD_DEFAULT) as String
        transform.configure(props)

        // original schema
        val originalSchema = SchemaBuilder.struct()
            .name("user.UserAssigned")
            .version(1)
            .doc("README")
            .field("id", Schema.STRING_SCHEMA)
            .field("name", Schema.STRING_SCHEMA)
            .build()

        // original value
        val originalValue = Struct(originalSchema)
            .put("id", "ID1")
            .put("name", "John Doe")

        // apply SMT
        val transformedRecord = transform.apply(
            SinkRecord(
                "test-topic", 0,
                null, "key1",
                originalSchema, originalValue,
                0
            )
        )

        val transformedSchema = transformedRecord.valueSchema()
        val transformedValue = transformedRecord.value() as Struct

        // ensure original and transformed records share the same schema
        assertEquals(originalSchema.name(), transformedSchema.name())
        assertEquals(originalSchema.version(), transformedSchema.version())
        assertEquals(originalSchema.doc(), transformedSchema.doc())

        // assert that the transformed record contains original fields
        assertTransformedField(
            "id",
            originalSchema,
            originalValue,
            transformedSchema,
            transformedValue,
            Struct::getString
        )

        assertTransformedField(
            "name",
            originalSchema,
            originalValue,
            transformedSchema,
            transformedValue,
            Struct::getString
        )

        // assert that the transformed record contains additional schema metadata
        assertEquals(SCHEMA_METADATA_SCHEMA, transformedSchema.field(schemaField).schema())
        assertEquals(
            Struct(SCHEMA_METADATA_SCHEMA)
                .put("subject", originalSchema.name())
                .put("version", originalSchema.version()),
            transformedValue.getStruct(schemaField)
        )
    }

    @ParameterizedTest
    @ArgumentsSource(TransformProps::class)
    fun `should cache original schema`(props: Map<String, *>) {
        transform.configure(props)

        // original schema
        val originalSchema = SchemaBuilder.struct()
            .name("user.UserAssigned")
            .version(1)
            .doc("README")
            .field("id", Schema.STRING_SCHEMA)
            .field("name", Schema.STRING_SCHEMA)
            .field("address", Schema.OPTIONAL_STRING_SCHEMA)
            .build()

        // apply SMT
        val transformedRecord1 = transform.apply(
            SinkRecord(
                "test-topic", 0, null, "key1",
                originalSchema, Struct(originalSchema)
                    .put("id", "ID1")
                    .put("name", "John Doe"),
                0
            )
        )

        val transformedRecord2 = transform.apply(
            SinkRecord(
                "test-topic", 0,
                null, "key2",
                originalSchema, Struct(originalSchema)
                    .put("id", "ID2")
                    .put("name", "Jane Doe"),
                1
            )
        )

        assertSame(transformedRecord1.valueSchema(), transformedRecord2.valueSchema())
    }

    @ParameterizedTest
    @ArgumentsSource(TransformProps::class)
    fun `should not transform schemaless records`(props: Map<String, *>) {
        transform.configure(props)

        // sink record is schemaless
        val originalRecord = SinkRecord(
            "test-topic", 0,
            null, "key1",
            null, mapOf(
                "id" to "ID1",
                "name" to "John Doe"
            ),
            0
        )

        // apply SMT
        val transformedRecord = transform.apply(originalRecord)

        assertSame(originalRecord, transformedRecord)
        assertNull(transformedRecord.valueSchema())
    }

    @ParameterizedTest
    @ArgumentsSource(TransformProps::class)
    fun `should not transform records with unnamed schema`(props: Map<String, *>) {
        transform.configure(props)

        // original unnamed schema
        val originalSchema = SchemaBuilder.struct()
            .version(42)
            .doc("README")
            .field("id", Schema.STRING_SCHEMA)
            .field("name", Schema.OPTIONAL_STRING_SCHEMA)
            .build()

        // apply SMT
        val originalRecord = SinkRecord(
            "test-topic", 0, null, "key1",
            originalSchema, Struct(originalSchema)
                .put("id", "ID1")
                .put("name", "John Doe"),
            0
        )

        val transformedRecord = transform.apply(originalRecord)

        assertSame(originalRecord, transformedRecord)
    }
}

private fun assertTransformedField(
    fieldName: String,
    originalSchema: Schema,
    originalValue: Struct,
    transformedSchema: Schema,
    transformedValue: Struct,
    fieldExtractor: Struct.(fieldName: String) -> Any
) {
    assertEquals(originalSchema.field(fieldName).schema(), transformedSchema.field(fieldName).schema())
    assertEquals(originalValue.fieldExtractor(fieldName), transformedValue.fieldExtractor(fieldName))
}
