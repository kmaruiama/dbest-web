package dbest

import ibd.query.Operation
import ibd.table.Table
import ibd.table.btree.BTreeTable
import ibd.table.csv.CSVTable
import ibd.table.memory.MemoryTable
import ibd.table.prototype.BasicDataRow
import ibd.table.prototype.Header
import ibd.table.prototype.Prototype
import ibd.table.prototype.column.BooleanColumn
import ibd.table.prototype.column.Column as EngineColumn
import ibd.table.prototype.column.DoubleColumn
import ibd.table.prototype.column.FloatColumn
import ibd.table.prototype.column.IntegerColumn
import ibd.table.prototype.column.LongColumn
import ibd.table.prototype.column.StringColumn
import ibd.table.prototype.metadata.Metadata
import ibd.table.xml.XMLTable
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import sources.xml.XMLRecognizer

typealias OpenTables = HashMap<TableSpec, Table>

fun openTable(spec: TableSpec): Table =
    gate({
        when (spec) {
            is MemorySpec -> {
                val table = MemoryTable(Header(prototype(spec.columns), spec.name))
                for (row in spec.rows) {
                    table.addRecord(dataRow(spec, row), true)
                }
                table
            }
            is CsvSpec -> {
                val table =
                    CSVTable(
                        fileHeader(spec.name, spec.path, spec.columns),
                        spec.separator,
                        spec.delimiter,
                        spec.headerLine)
                table.open()
                table
            }
            is BTreeSpec -> {
                val table = BTreeTable(spec.path, spec.cacheSize)
                table.open()
                table
            }
            is XmlSpec -> {
                requireSafeXml(spec.path)
                val header = fileHeader(spec.name, spec.path, spec.columns)
                val table =
                    XMLTable(
                        header,
                        spec.rootElement,
                        spec.recordElement,
                        XMLRecognizer.FlatteningStrategy.NESTED_COLUMNS)
                table.open()
                table
            }
        }
    })

private fun prototype(columns: List<Column>): Prototype {
    require(columns.isNotEmpty(), { "A tabela precisa de no minimo uma coluna" })
    val prototype = Prototype()
    for (column in columns) {
        prototype.addColumn(engineColumn(column))
    }
    return prototype
}

private fun engineColumn(column: Column): EngineColumn {
    val base =
        when {
            column.primaryKey -> Metadata.PRIMARY_KEY
            column.nullable -> Metadata.CAN_NULL_COLUMN
            else -> Metadata.NONE
        }
    return when (column.type) {
        ColumnType.INT -> IntegerColumn(column.name, 4, base)
        ColumnType.LONG -> LongColumn(column.name, 8, base)
        ColumnType.FLOAT -> FloatColumn(column.name, 4, flags(base, Metadata.FLOATING_POINT))
        ColumnType.DOUBLE -> DoubleColumn(column.name, 8, flags(base, Metadata.FLOATING_POINT))
        ColumnType.BOOLEAN -> BooleanColumn(column.name, 1, flags(base, Metadata.BOOLEAN))
        ColumnType.STRING ->
            StringColumn(
                column.name, 255, flags(base, Metadata.STRING, Metadata.DINAMIC_COLUMN_SIZE))
    }
}

private fun flags(vararg parts: Short): Short {
    var combined = 0
    for (part in parts) {
        combined = combined or part.toInt()
    }
    return combined.toShort()
}

private fun dataRow(spec: MemorySpec, values: Map<String, Any?>): BasicDataRow {
    val row = BasicDataRow()
    for ((column, value) in values) {
        if (spec.columns.none({ known -> known.name == column })) {
            throw PlanError("A tabela '${spec.name}' nao tem a coluna '$column'")
        }
        when (value) {
            null -> {}
            is Int -> row.setInt(column, value)
            is Long -> row.setLong(column, value)
            is Float -> row.setFloat(column, value)
            is Double -> row.setDouble(column, value)
            is Boolean -> row.setBoolean(column, value)
            is String -> row.setString(column, value)
            else ->
                throw PlanError(
                    "Tipo de valor nao suportado ${value.javaClass.simpleName} para a coluna $column")
        }
    }
    return row
}

private fun fileHeader(name: String, path: String, columns: List<Column>): Header {
    val header = Header(prototype(columns), name)
    header.set(Header.FILE_PATH, path)
    return header
}

fun requireSafeXml(path: String) {
    val factory = DocumentBuilderFactory.newInstance()
    try {
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        factory.newDocumentBuilder().parse(File(path))
    } catch (failure: Exception) {
        throw IllegalArgumentException("arquivo XML invalido ou inseguro", failure)
    }
}

fun columnsOf(operation: Operation): List<SchemaColumn> {
    val columns = ArrayList<SchemaColumn>()
    for (source in operation.exposedDataSources) {
        for (column in source.prototype.columns) {
            columns.add(
                SchemaColumn(
                    source.alias, column.name, columnType(column.type), column.isPrimaryKey))
        }
    }
    return columns
}

private fun columnType(engineType: String): ColumnType =
    when (engineType) {
        "INTEGER" -> ColumnType.INT
        "LONG" -> ColumnType.LONG
        "FLOAT" -> ColumnType.FLOAT
        "DOUBLE" -> ColumnType.DOUBLE
        "BOOLEAN" -> ColumnType.BOOLEAN
        else -> ColumnType.STRING
    }
