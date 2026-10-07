package dbest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable @JvmInline value class NodeId(val value: Int)

enum class Port {
    ONLY,
    LEFT,
    RIGHT
}

@Serializable data class Position(val x: Double, val y: Double)

@Serializable data class Edge(val from: NodeId, val to: NodeId, val port: Port)

enum class ColumnType {
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    BOOLEAN
}

@Serializable
data class Column(
    val name: String,
    val type: ColumnType,
    val primaryKey: Boolean = false,
    val nullable: Boolean = false
) {
    init {
        require(name.isNotBlank(), { "O nome da coluna nao pode ser vazio" })
    }
}

@Serializable
sealed interface TableSpec {
    val name: String
}

@Serializable
@SerialName("memory")
data class MemorySpec(
    override val name: String,
    val columns: List<Column>,
    val rows: List<Map<String, @Serializable(with = LiteralSerializer::class) Any?>> = emptyList(),
) : TableSpec

@Serializable
@SerialName("csv")
data class CsvSpec(
    override val name: String,
    val path: String,
    val columns: List<Column>,
    val separator: Char = ',',
    val delimiter: Char = '"',
    val headerLine: Int = 1,
) : TableSpec

@Serializable
@SerialName("btree")
data class BTreeSpec(override val name: String, val path: String, val cacheSize: Int = 100_000) :
    TableSpec

@Serializable
@SerialName("xml")
data class XmlSpec(
    override val name: String,
    val path: String,
    val columns: List<Column>,
    val rootElement: String? = null,
    val recordElement: String? = null,
) : TableSpec

@Serializable
data class Session(
    val tables: Map<TableId, TableSpec> = emptyMap(),
    val nodes: Map<NodeId, Node> = emptyMap(),
    val edges: Set<Edge> = emptySet(),
    val layout: Map<NodeId, Position> = emptyMap(),
)

@Serializable sealed interface Command

@Serializable
@SerialName("addTable")
data class AddTable(val id: TableId, val spec: TableSpec) : Command

@Serializable @SerialName("removeTable") data class RemoveTable(val id: TableId) : Command

@Serializable
@SerialName("addNode")
data class AddNode(val id: NodeId, val node: Node, val at: Position) : Command

@Serializable @SerialName("setNode") data class SetNode(val id: NodeId, val node: Node) : Command

@Serializable @SerialName("removeNode") data class RemoveNode(val id: NodeId) : Command

@Serializable @SerialName("connect") data class Connect(val edge: Edge) : Command

@Serializable @SerialName("disconnect") data class Disconnect(val edge: Edge) : Command

@Serializable @SerialName("move") data class Move(val id: NodeId, val to: Position) : Command

@Serializable @SerialName("batch") data class Batch(val commands: List<Command>) : Command

@Serializable data class Step(val redo: Command, val undo: Command)

@Serializable
data class EditorState(
    val session: Session = Session(),
    val undoStack: List<Step> = emptyList(),
    val redoStack: List<Step> = emptyList(),
)

const val UNDO_LIMIT = 200

@Serializable data class Problem(val node: NodeId, val message: String)

fun inputPorts(node: Node): List<Port> =
    when (node) {
        is SourceNode -> emptyList()
        is UnaryNode -> listOf(Port.ONLY)
        is BinaryNode -> listOf(Port.LEFT, Port.RIGHT)
    }

fun kindOf(node: Node): String {
    val json = wire.encodeToJsonElement(Node.serializer(), node).jsonObject
    return json.getValue("@type").jsonPrimitive.content
}

fun apply(session: Session, command: Command): Session =
    when (command) {
        is AddTable -> addTable(session, command)
        is RemoveTable -> removeTable(session, command)
        is AddNode -> addNode(session, command)
        is SetNode -> setNode(session, command)
        is RemoveNode -> removeNode(session, command)
        is Connect -> connect(session, command.edge)
        is Disconnect -> disconnect(session, command.edge)
        is Move -> move(session, command)
        is Batch -> command.commands.fold(session, { state, inner -> apply(state, inner) })
    }

private fun addTable(session: Session, command: AddTable): Session {
    require(command.id !in session.tables, { "A tabela ${command.id.value} ja existe" })
    require(
        command.spec.name.isNotBlank(),
        { "A tabela precisa de um nome, que eh o alias com que ela entra no canvas" })
    return session.copy(tables = session.tables + (command.id to command.spec))
}

private fun removeTable(session: Session, command: RemoveTable): Session {
    require(command.id in session.tables, { "A tabela ${command.id.value} nao existe" })
    val scanned =
        session.nodes.values.any({ node -> node is TableNode && node.table == command.id })
    require(!scanned, { "A tabela ${command.id.value} ainda eh escaneada por um node" })
    return session.copy(tables = session.tables - command.id)
}

private fun addNode(session: Session, command: AddNode): Session {
    require(command.id !in session.nodes, { "O node #${command.id.value} ja existe" })
    requireScannedTable(session, command.node)
    requireReadableSource(command.node)
    return session.copy(
        nodes = session.nodes + (command.id to command.node),
        layout = session.layout + (command.id to command.at))
}

private fun requireScannedTable(session: Session, node: Node) {
    if (node is TableNode) {
        require(node.table in session.tables, { "A tabela ${node.table.value} nao existe" })
    }
}

private fun requireReadableSource(node: Node) {
    val name = sourceName(node)
    require(
        name == null || '.' !in name,
        { "O nome de fonte '$name' nao pode ter ponto: as colunas sao lidas como fonte.coluna" })
}

private fun setNode(session: Session, command: SetNode): Session {
    val current = session.nodes.get(command.id)
    require(current != null, { "O node #${command.id.value} nao existe" })
    requireScannedTable(session, command.node)
    requireReadableSource(command.node)
    val currentKind = kindOf(current)
    val nextKind = kindOf(command.node)
    require(
        currentKind == nextKind,
        { "O node #${command.id.value} eh um $currentKind e nao pode virar um $nextKind" })
    val next = session.copy(nodes = session.nodes + (command.id to command.node))
    requireNoNewClash(session, next, listOf(command.id))
    return next
}

private fun requireNoNewClash(before: Session, after: Session, touched: List<NodeId>) {
    val known = touched.flatMap({ id -> sourceClashes(before, id) })
    val fresh = sourceClashes(after, touched[0]).filter({ clash -> clash !in known })
    require(fresh.isEmpty(), { "${fresh.first().message}; renomeie um dos dois antes" })
}

private fun removeNode(session: Session, command: RemoveNode): Session {
    require(command.id in session.nodes, { "O node #${command.id.value} nao existe" })
    val untouched =
        session.edges.filterTo(
            LinkedHashSet(), { edge -> edge.from != command.id && edge.to != command.id })
    return session.copy(
        nodes = session.nodes - command.id,
        edges = untouched,
        layout = session.layout - command.id,
    )
}

private fun connect(session: Session, edge: Edge): Session {
    val consumer = session.nodes.get(edge.to)
    require(edge.from in session.nodes, { "O node #${edge.from.value} nao existe" })
    require(consumer != null, { "O node #${edge.to.value} nao existe" })
    require(edge.from != edge.to, { "Um node nao pode alimentar a si mesmo" })
    require(edge !in session.edges, { "Esses nodes ja estao conectados em ${edge.port}" })
    require(
        edge.port in inputPorts(consumer),
        { "O node #${edge.to.value} nao aceita entrada ${edge.port}" })
    require(
        inputOf(session, edge.to, edge.port) == null,
        { "A entrada ${edge.port} do node #${edge.to.value} ja esta conectada" })
    require(
        edge.from !in walk(session, edge.to, Direction.DOWNSTREAM), { "Conectar criaria um ciclo" })
    val next = session.copy(edges = session.edges + edge)
    requireNoNewClash(session, next, listOf(edge.to, edge.from))
    return next
}

private fun disconnect(session: Session, edge: Edge): Session {
    require(edge in session.edges, { "Nao existe essa conexao para desconectar" })
    return session.copy(edges = session.edges - edge)
}

private fun move(session: Session, command: Move): Session {
    require(command.id in session.nodes, { "O node #${command.id.value} nao existe" })
    return session.copy(layout = session.layout + (command.id to command.to))
}

fun inputOf(session: Session, id: NodeId, port: Port): NodeId? {
    val edge = session.edges.firstOrNull({ edge -> edge.to == id && edge.port == port })
    return edge?.from
}

fun invert(session: Session, command: Command): Command =
    when (command) {
        is AddTable -> RemoveTable(command.id)
        is RemoveTable -> AddTable(command.id, session.tables.getValue(command.id))
        is AddNode -> RemoveNode(command.id)
        is SetNode -> SetNode(command.id, session.nodes.getValue(command.id))
        is RemoveNode -> {
            val restore =
                AddNode(
                    command.id,
                    session.nodes.getValue(command.id),
                    session.layout.getValue(command.id))
            val touching =
                session.edges.filter({ edge -> edge.from == command.id || edge.to == command.id })
            Batch(listOf<Command>(restore) + touching.map({ edge -> Connect(edge) }))
        }
        is Connect -> Disconnect(command.edge)
        is Disconnect -> Connect(command.edge)
        is Move -> Move(command.id, session.layout.getValue(command.id))
        is Batch -> {
            var state = session
            val inverses = ArrayList<Command>()
            for (inner in command.commands) {
                inverses.add(invert(state, inner))
                state = apply(state, inner)
            }
            Batch(inverses.reversed())
        }
    }

fun edit(editorState: EditorState, command: Command): EditorState {
    val step = Step(redo = command, undo = invert(editorState.session, command))
    val session = apply(editorState.session, command)
    val undoStack = (editorState.undoStack + step).takeLast(UNDO_LIMIT)
    return EditorState(session = session, undoStack = undoStack, redoStack = emptyList())
}

fun undo(editorState: EditorState): EditorState {
    val step = editorState.undoStack.lastOrNull()
    if (step == null) {
        return editorState
    }
    val session = apply(editorState.session, step.undo)
    val undoStack = editorState.undoStack.dropLast(1)
    val redoStack = editorState.redoStack + step
    return EditorState(session = session, undoStack = undoStack, redoStack = redoStack)
}

fun redo(editorState: EditorState): EditorState {
    val step = editorState.redoStack.lastOrNull()
    if (step == null) {
        return editorState
    }
    val session = apply(editorState.session, step.redo)
    val undoStack = editorState.undoStack + step
    val redoStack = editorState.redoStack.dropLast(1)
    return EditorState(session = session, undoStack = undoStack, redoStack = redoStack)
}

enum class Direction {
    UPSTREAM,
    DOWNSTREAM,
    EITHER
}

fun walk(session: Session, start: NodeId, direction: Direction): Set<NodeId> {
    val seen = LinkedHashSet<NodeId>()
    val pending = ArrayDeque(listOf(start))
    while (pending.isNotEmpty()) {
        val current = pending.removeFirst()
        if (!seen.add(current)) {
            continue
        }
        for (edge in session.edges) {
            if (direction != Direction.UPSTREAM && edge.from == current) {
                pending.add(edge.to)
            }
            if (direction != Direction.DOWNSTREAM && edge.to == current) {
                pending.add(edge.from)
            }
        }
    }
    return seen
}

fun roots(session: Session): List<NodeId> =
    session.nodes.keys
        .filter({ id -> session.edges.none({ edge -> edge.from == id }) })
        .sortedBy({ id -> id.value })

fun missingInputs(session: Session): List<Problem> {
    val problems = ArrayList<Problem>()
    for ((id, node) in session.nodes) {
        for (port in inputPorts(node)) {
            if (inputOf(session, id, port) == null) {
                problems.add(Problem(id, "falta a entrada ${port.name}"))
            }
        }
    }
    return problems
}

fun complete(session: Session, id: NodeId): Boolean {
    val missing = missingInputs(session).map({ problem -> problem.node }).toSet()
    return walk(session, id, Direction.UPSTREAM).none({ node -> node in missing })
}

fun sourceName(node: Node): String? {
    val name = if (node is AliasNode) node.to else aliasOf(node)
    return if (name == null || name.isBlank()) null else name
}

fun sourceClashes(session: Session, start: NodeId): List<Problem> {
    val first = HashMap<String, NodeId>()
    val clashes = ArrayList<Problem>()
    for (id in walk(session, start, Direction.EITHER).sortedBy({ id -> id.value })) {
        val name = sourceName(session.nodes.getValue(id)) ?: continue
        val earlier = first.get(name)
        if (earlier == null) {
            first.put(name, id)
        } else {
            clashes.add(
                Problem(id, "o alias '$name' ja nomeia o node #${earlier.value} desta arvore"))
        }
    }
    return clashes
}

fun sourceClashes(session: Session): List<Problem> {
    val covered = HashSet<NodeId>()
    val clashes = ArrayList<Problem>()
    for (id in session.nodes.keys.sortedBy({ id -> id.value })) {
        if (id in covered) {
            continue
        }
        covered.addAll(walk(session, id, Direction.EITHER))
        clashes.addAll(sourceClashes(session, id))
    }
    return clashes
}
