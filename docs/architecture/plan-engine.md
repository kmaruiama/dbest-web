# Plan and engine boundary

The application uses three different query representations because editing, serializing, and executing have incompatible needs.

| Representation | Purpose | May be incomplete? | Contains engine objects? | Defined by |
| --- | --- | ---: | ---: | --- |
| Editor graph | Persistable canvas document | Yes | No | [graph model](../../app/server/src/features/canvas/graph) |
| Adapter `Plan` | Complete executable description for one inspected node | No | A resolved table handle at leaves | [Plan.kt](../../app/server/src/kernel/adapter/Plan.kt) |
| DBest `Operation` graph | Behavior-rich engine runtime objects | No | Yes | [compiler](../../app/server/src/kernel/adapter/compile/Compiler.kt) and `modules/engine` |

## Derivation contract

`plan(session, root, tables)` recursively follows incoming edges of `root` in [Query.kt](../../app/server/src/features/canvas/query/Query.kt). A table node resolves its persisted `TableId` to an opened `TableHandle`; unary and binary nodes recursively attach the plans from their required ports.

The result covers the selected node and its upstream inputs only. It is a tree even when the source graph is a DAG: if a source graph node is reached by two paths, it becomes two plan subtrees. The current implementation does not perform common-subexpression sharing or memoization merely because two graph paths share a node.

`Plan` data classes repeat local checks so code outside the canvas feature cannot construct an obviously invalid plan. The hierarchy is structurally immutable, but it is not a network or persistence DTO: `Table` holds a runtime `TableHandle`. The durable source description is `TableSpec`, not `Plan.Table`.

## Compilation contract

Immediately before engine work, `compile(plan)` recursively materializes concrete DBest `Operation` classes. Conditions are translated to engine lookup filters and adapter enums select engine constants or operation subclasses. The compiler is split by arity in [Compiler.kt](../../app/server/src/kernel/adapter/compile/Compiler.kt), [Unary.kt](../../app/server/src/kernel/adapter/compile/Unary.kt), and [Binary.kt](../../app/server/src/kernel/adapter/compile/Binary.kt).

Compilation is not an interpreter that keeps consulting a plan during execution. It creates a new operation graph, after which DBest owns operation behavior. Consequently, schema lookup, validation, rows, and export each compile their own graph.

The adapter is the explicit compatibility boundary. Supporting an operator means supporting it on both sides: its editor configuration must derive to `Plan`, and its plan must compile to a DBest operation with defined behavior for valid and unsupported combinations. See [operator extension](operator-extension.md).

## Validation versus execution

`GET /problems` first reports missing ports. For a complete root, it derives and validates the plan through DBest. Validation initializes an operation with `run()` but does not consume every tuple, so it establishes initialization/engine viability rather than a complete result scan. Schema and row requests compile again and may still fail after a prior clean problem check. The route behavior is in [NodeRoutes.kt](../../app/server/src/features/canvas/NodeRoutes.kt); adapter functions are in [Execution.kt](../../app/server/src/kernel/adapter/Execution.kt).

This boundary is the subject of [ADR 0001](../adr/0001-editor-graph-and-executable-plan.md) and [ADR 0002](../adr/0002-compile-plans-at-engine-boundary.md).
