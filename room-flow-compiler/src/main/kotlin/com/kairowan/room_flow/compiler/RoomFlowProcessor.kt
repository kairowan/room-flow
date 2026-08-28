package com.kairowan.room_flow.compiler

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.validate

/** Generates only explicit flat scalar mappings; Room remains responsible for database/DAO generation. */
class RoomFlowProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val generated = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("com.kairowan.room_flow.typed.RoomFlowEntity").toList()
        for (symbol in symbols.filter { it.validate() }) {
            val entity = symbol as? KSClassDeclaration
            if (entity == null) {
                error("RF001: annotation requires an entity class", symbol)
                continue
            }
            val qualified = entity.qualifiedName?.asString() ?: continue
            if (entity.containingFile == null) continue
            val incompatibleDatabase = resolver.getSymbolsWithAnnotation("androidx.room.Database").any { database ->
                database.annotation("androidx.room.TypeConverters") != null &&
                    (database.annotation("androidx.room.Database")?.value("entities") as? List<*>)?.any {
                        (it as? KSType)?.declaration?.qualifiedName?.asString() == qualified
                    } == true
            }
            if (incompatibleDatabase) {
                error("RF002: database-level TypeConverters are not supported by typed entities; keep this entity on Room DAO", entity)
                continue
            }
            if (qualified !in generated && generate(entity, Dependencies(true, *resolver.getAllFiles().toList().toTypedArray()))) generated += qualified
        }
        return symbols.filterNot { it.validate() }
    }

    private fun generate(entity: KSClassDeclaration, dependencies: Dependencies): Boolean {
        val file = requireNotNull(entity.containingFile)
        val roomEntity = entity.annotation("androidx.room.Entity")
        val constructor = entity.primaryConstructor
        if (roomEntity == null || Modifier.DATA !in entity.modifiers || entity.parentDeclaration != null ||
            entity.typeParameters.isNotEmpty() || constructor == null ||
            entity.modifiers.any { it in setOf(Modifier.PRIVATE, Modifier.PROTECTED, Modifier.INTERNAL) } ||
            constructor.modifiers.any { it in setOf(Modifier.PRIVATE, Modifier.PROTECTED) } ||
            constructor.annotation("androidx.room.Ignore") != null ||
            entity.getAllSuperTypes().any { it.declaration.qualifiedName?.asString() != "kotlin.Any" }) {
            error("RF001: use a public top-level non-generic @Entity data class with a public primary constructor and no inheritance", entity)
            return false
        }
        if (entity.annotation("androidx.room.TypeConverters") != null || file.annotation("androidx.room.TypeConverters") != null ||
            entity.annotation("androidx.room.Fts3") != null || entity.annotation("androidx.room.Fts4") != null ||
            (roomEntity.value("ignoredColumns") as? List<*>)?.isNotEmpty() == true) {
            error("RF002: TypeConverters, FTS and Entity.ignoredColumns are unsupported; use @Ignore on a body property or a Room DAO", entity)
            return false
        }
        val properties = entity.getAllProperties().toList()
        val names = constructor.parameters.mapNotNull { it.name?.asString() }
        if (properties.any { it.simpleName.asString() !in names && it.annotation("androidx.room.Ignore") == null }) {
            error("RF002: persisted properties must be declared in the primary constructor", entity)
            return false
        }
        val primaryKeys = (roomEntity.value("primaryKeys") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val columnNames = mutableListOf<String>()
        val keyNames = mutableListOf<String>()
        val declarations = mutableListOf<String>()
        val constructorArgs = mutableListOf<String>()
        val reserved = setOf("sqlName", "columns", "keys", "quoted", "column", "readEntity", "owns", "query", "validate")
        for (parameter in constructor.parameters) {
            val name = parameter.name?.asString() ?: return false
            val property = properties.singleOrNull { it.simpleName.asString() == name }
            if (property == null || property.modifiers.any { it in setOf(Modifier.PRIVATE, Modifier.PROTECTED, Modifier.INTERNAL) }) {
                error("RF002: constructor parameters must be readable public properties", parameter)
                return false
            }
            val annotations = property.annotations.toList() + parameter.annotations.toList() + property.getter?.annotations.orEmpty().toList()
            fun annotation(qualified: String): KSAnnotation? = annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == qualified }
            if (annotation("androidx.room.Ignore") != null) {
                error("RF002: ignored properties must be in the class body, not the primary constructor", parameter)
                return false
            }
            if (name in reserved || annotation("kotlin.jvm.Transient") != null ||
                listOf("Embedded", "Relation", "TypeConverters").any { annotation("androidx.room.$it") != null }) {
                error("RF002: Embedded, Relation, TypeConverters and reserved metadata property names are unsupported", property)
                return false
            }
            val type = property.type.resolve()
            val typeName = type.declaration.qualifiedName?.asString()
            val nullable = type.nullability == Nullability.NULLABLE
            val affinity = when (typeName) {
                "kotlin.Int", "kotlin.Long", "kotlin.Short", "kotlin.Byte", "kotlin.Boolean" -> "INTEGER"
                "kotlin.Double", "kotlin.Float" -> "REAL"
                "kotlin.String" -> "TEXT"
                "kotlin.ByteArray" -> "BLOB"
                else -> {
                    error("RF002: unsupported field type $typeName; use Room DAO for custom conversions", property)
                    return false
                }
            }
            val columnInfo = annotation("androidx.room.ColumnInfo")
            val explicitAffinity = columnInfo?.value("typeAffinity") as? Int ?: 1
            if (explicitAffinity != 1 && explicitAffinity != mapOf("TEXT" to 2, "INTEGER" to 3, "REAL" to 4, "BLOB" to 5)[affinity]) {
                error("RF002: overridden storage affinity does not match the Kotlin scalar type", property)
                return false
            }
            val columnName = (columnInfo?.value("name") as? String)?.takeUnless { it == "[field-name]" } ?: name
            if (columnName.isBlank() || columnName.any { it == '\u0000' || it == '"' || it == '`' } || columnNames.any { it.equals(columnName, true) }) {
                error("RF003: invalid or duplicate SQL column name", property)
                return false
            }
            val primary = annotation("androidx.room.PrimaryKey")
            if (primary != null && primaryKeys.isNotEmpty()) {
                error("RF003: choose either @PrimaryKey or Entity.primaryKeys", property)
                return false
            }
            val keyPosition = if (primary != null) 1 else primaryKeys.indexOf(columnName) + 1
            val autoGenerate = primary?.value("autoGenerate") == true
            if ((keyPosition > 0 && nullable) || (autoGenerate && typeName !in setOf("kotlin.Int", "kotlin.Long"))) {
                error("RF003: primary keys must be non-null; autoGenerate requires Int or Long", property)
                return false
            }
            if (keyPosition > 0) keyNames += columnName
            val kotlinType = typeName + if (nullable) "?" else ""
            val read: (String) -> String = { index ->
                val value = when (typeName) {
                    "kotlin.Int" -> "cursor.getInt($index)"
                    "kotlin.Long" -> "cursor.getLong($index)"
                    "kotlin.Short" -> "cursor.getShort($index)"
                    "kotlin.Byte" -> "cursor.getInt($index).toByte()"
                    "kotlin.Boolean" -> "(cursor.getInt($index) != 0)"
                    "kotlin.Double" -> "cursor.getDouble($index)"
                    "kotlin.Float" -> "cursor.getFloat($index)"
                    "kotlin.ByteArray" -> "cursor.getBlob($index)"
                    else -> "cursor.getString($index)"
                }
                "if (cursor.isNull($index)) ${if (nullable) "null" else "error(\"NULL in non-null entity column\")"} else $value"
            }
            declarations += "    val `${name}`: com.kairowan.room_flow.typed.EntityColumn<${entity.qualifiedName!!.asString()}, $kotlinType> = column(\n" +
                "        ${literal(columnName)}, ${literal(affinity)}, $nullable, $keyPosition, $autoGenerate,\n" +
                "        { it.`$name` }, { cursor, index -> ${read("index")} }\n    )"
            constructorArgs += "        `$name` = ${read(columnNames.size.toString())}"
            columnNames += columnName
        }
        if (keyNames.isEmpty() || (primaryKeys.isEmpty() && keyNames.size != 1) ||
            (primaryKeys.isNotEmpty() && (primaryKeys.toSet() != keyNames.toSet() || primaryKeys.distinct().size != primaryKeys.size))) {
            error("RF003: entity needs one primary key or an exact composite primaryKeys definition", entity)
            return false
        }
        val tableName = (roomEntity.value("tableName") as? String).orEmpty().ifEmpty { entity.simpleName.asString() }
        if (tableName.isBlank() || tableName.any { it == '\u0000' || it == '"' || it == '`' } || tableName.startsWith("sqlite_", true) || tableName.equals("room_master_table", true)) {
            error("RF003: invalid or reserved table name", entity)
            return false
        }
        val packageName = entity.packageName.asString()
        val generatedName = entity.simpleName.asString() + "Table"
        // ponytail: aggregate source dependencies so adding database-level converters invalidates mappings.
        // Per-entity dependency graphs can replace this if processor cost becomes material.
        environment.codeGenerator.createNewFile(dependencies, packageName, generatedName).bufferedWriter().use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("/** Generated by room-flow-compiler. Do not edit. */\n")
            writer.write("object $generatedName : com.kairowan.room_flow.typed.EntityTable<${entity.qualifiedName!!.asString()}>(${literal(tableName)}) {\n")
            writer.write(declarations.joinToString("\n\n"))
            writer.write("\n\n    override fun readEntity(cursor: android.database.Cursor): ${entity.qualifiedName!!.asString()} = ${entity.qualifiedName!!.asString()}(\n")
            writer.write(constructorArgs.joinToString(",\n"))
            writer.write("\n    )\n}\n")
        }
        return true
    }

    private fun error(message: String, node: KSNode) = environment.logger.error(message, node)
}

private fun KSAnnotated.annotation(name: String): KSAnnotation? =
    annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == name }

private fun KSAnnotation.value(name: String): Any? = arguments.firstOrNull { it.name?.asString() == name }?.value

private fun literal(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("$", "\\$").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
