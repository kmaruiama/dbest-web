# Editor graph contract

`Session` is the persisted canvas document. It is deliberately allowed to be incomplete: a user can place an operator before wiring its inputs, keep unrelated pipelines on the same canvas, and save or undo that state. DBest engine objects are not part of this representation.

The model is defined in [Session.kt](../../app/server/src/features/canvas/graph/Session.kt), [Nodes.kt](../../app/server/src/features/canvas/graph/Nodes.kt), and [TableSpecs.kt](../../app/server/src/features/canvas/graph/TableSpecs.kt).

## Document shape

| Field | Meaning | Persisted? |
| --- | --- | --- |
| `tables` | `TableId` to declarative source description | Yes |
| `nodes` | `NodeId` to operator configuration | Yes |
| `edges` | producer-to-consumer connections, including the consumer port | Yes |
| `layout` | canvas position for each node | Yes |

A node's inputs are edges, not nested fields. A source has no input ports, a unary operator has `ONLY`, and a binary operator has `LEFT` and `RIGHT`. Those port rules come from `inputPorts` in [Nodes.kt](../../app/server/src/features/canvas/graph/Nodes.kt).

## Validity rules

Node constructors reject invalid local settings, such as an empty projection or invalid limit. Command application enforces document-wide rules in [Commands.kt](../../app/server/src/features/canvas/history/Commands.kt):

- IDs and referenced tables must exist when required.
- A table cannot be removed while a table node scans it.
- Each consumer port has at most one incoming edge.
- A producer cannot feed itself, and an edge cannot create a cycle.
- Removing a node removes all of its connected edges.
- `setNode` may change settings but cannot change the node's operator kind.

Missing required inputs are intentionally **not** command failures. They are valid editing state and are surfaced by `GET /problems`; only a complete upstream subgraph can be derived into a plan. The problem calculation is in [Query.kt](../../app/server/src/features/canvas/query/Query.kt).

Fan-out is allowed: one producer can supply more than one consumer. The graph is therefore a DAG, not necessarily a forest. A derived `Plan`, however, is a tree; sharing is unfolded during derivation. See [plan and engine boundary](plan-engine.md).

## Commands, history, and revision

The command vocabulary is `addTable`, `removeTable`, `addNode`, `setNode`, `removeNode`, `connect`, `disconnect`, `move`, and `batch`. Its tagged JSON codec is [CommandsCodec.kt](../../app/server/src/features/canvas/history/CommandsCodec.kt). A batch is applied in order; if any contained command fails, no new canvas state is published.

`History` stores inverse commands, caps undo history at 200 steps, clears redo after a new edit, and coalesces consecutive moves of the same node. Its implementation is [History.kt](../../app/server/src/features/canvas/history/History.kt).

Each workspace publishes an immutable `CanvasState` through an `AtomicReference`. Command, undo, and redo routes retry compare-and-set until they publish their transition. A successful state-changing command increments `revision`; no-op undo/redo does not. This prevents torn server state, but the command API has no client-supplied expected revision. Therefore the revision is an observation/version marker, not optimistic-concurrency protection for multiple browser clients. See [Canvas.kt](../../app/server/src/features/canvas/Canvas.kt) and [CanvasRoutes.kt](../../app/server/src/features/canvas/CanvasRoutes.kt).
