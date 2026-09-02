package dbest.features.canvas.query

import dbest.features.canvas.graph.NodeId
import dbest.features.canvas.graph.Session
import dbest.kernel.util.isEmpty
import dbest.kernel.util.mapCollection

data class Tree(val root: NodeId, val nodes: List<NodeId>)

fun trees(session: Session): List<Tree> =
    mapCollection(roots(session), { root -> Tree(root, upstream(session, root)) })

fun upstream(session: Session, start: NodeId): List<NodeId> {
    val seen = LinkedHashSet<NodeId>()
    val stack = ArrayDeque<NodeId>()
    stack.addLast(start)
    while (!isEmpty(stack)) {
        val node = stack.removeLast()
        if (!seen.add(node)) continue
        for (edge in session.edges) {
            if (edge.to == node) stack.addLast(edge.from)
        }
    }
    return seen.toList()
}
