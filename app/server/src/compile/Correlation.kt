package dbest

import ibd.query.binaryop.join.CrossJoin
import ibd.query.binaryop.join.LookupJoin
import ibd.query.binaryop.join.outer.NestedLoopRightJoin

data class LookupSides(val provider: Port, val receiver: Port)

fun lookupSides(node: Node): LookupSides? {
    val type = engineClass(node)
    if (type == null ||
        !(LookupJoin::class.java.isAssignableFrom(type) ||
            CrossJoin::class.java.isAssignableFrom(type))) {
        return null
    }
    return if (NestedLoopRightJoin::class.java.isAssignableFrom(type))
        LookupSides(provider = Port.RIGHT, receiver = Port.LEFT)
    else LookupSides(provider = Port.LEFT, receiver = Port.RIGHT)
}

fun receivingJoins(session: Session, id: NodeId): List<NodeId> {
    val joins = LinkedHashSet<NodeId>()
    for (node in walk(session, id, Direction.DOWNSTREAM)) {
        for (edge in session.edges) {
            val sides = lookupSides(session.nodes.getValue(edge.to))
            if (edge.from == node && edge.port == sides?.receiver) {
                joins.add(edge.to)
            }
        }
    }
    return joins.toList()
}

fun readers(session: Session, id: NodeId): Set<NodeId> {
    val downstream = walk(session, id, Direction.DOWNSTREAM)
    val readers = LinkedHashSet<NodeId>(downstream - id)
    for (join in downstream) {
        val sides = lookupSides(session.nodes.getValue(join)) ?: continue
        val provider = inputOf(session, join, sides.provider) ?: continue
        val receiver = inputOf(session, join, sides.receiver) ?: continue
        if (provider in downstream) {
            readers.addAll(walk(session, receiver, Direction.UPSTREAM))
        }
    }
    return readers
}
