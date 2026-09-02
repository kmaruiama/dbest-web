# Runtime ownership contract

Most application state is immutable data, but table handles, DBest operations, cursor caches, and the engine worker are mutable runtime resources. They have distinct owners and lifetimes.

| Resource | Owner | Lifetime | Release path |
| --- | --- | --- | --- |
| `Session`, history, layout | Workspace canvas state | Until replaced or session closes | Garbage collected with old immutable values |
| Open `TableHandle`s | Workspace | Lazily opened; reused until session/process close | `closeSession` / `closeAllSessions` via [Sessions.kt](../../app/server/src/features/sessions/Sessions.kt) |
| Compiled DBest `Operation` | Caller of compilation | One validation/schema/execution request, except a retained page cursor | Caller invokes `close()` |
| Paged result cursor | Process-global LRU cache, maximum 8 plans | Until exhausted, restarted, or evicted | [Execution.kt](../../app/server/src/kernel/adapter/Execution.kt) closes it |
| Schema entry | Process-global map keyed by `Plan` | Current process; no eviction/invalidation | None currently |
| Engine worker/admission permit | `Sessions` singleton | Server process | [Engine.kt](../../app/server/src/features/sessions/Engine.kt) shutdown |

## Engine scheduling

There is one DBest worker thread and one fair admission permit for the whole server, not one per workspace. Regular paged row and export requests attempt admission for one second and return `409` if another query remains active. Schema, existence, and problem checks wait instead. A streamed row request also takes the permit for its full stream lifetime.

This is intentionally a simple engine-safety boundary, but it means a long query in one workspace can delay or reject engine work in another. `Engine.kt` is the source of this behavior; HTTP endpoints choose `run`, `runWaiting`, or `stream` in [NodeRoutes.kt](../../app/server/src/features/canvas/NodeRoutes.kt) and [ExportRoutes.kt](../../app/server/src/features/export/ExportRoutes.kt).

## Rows, cursors, and cancellation

With `offset` and `limit`, the rows endpoint returns one JSON page. A forward page can reuse an in-process cursor for the same `Plan`; requesting an earlier offset restarts that operation. With neither parameter, the endpoint streams NDJSON and a closed HTTP body signals cancellation to the producer through a bounded queue. The stream format is implemented in [UnpagedRowsStream.kt](../../app/server/src/features/canvas/UnpagedRowsStream.kt).

The caches are process-global while table handles are workspace-owned. The implementation currently has no workspace-close purge for page cursors and no schema-cache eviction/invalidation. These are known lifecycle pressure points, not guarantees that callers should build upon.

## Consistency boundary

Each route reads the canvas state available when it begins deriving a plan. Results are not a transaction over subsequent graph edits, and cached schemas/cursors can outlive an edit or a workspace. Consumers must treat a new session revision as a reason to refresh their view; the server does not attach revision numbers to row or schema responses. Any change to cache scope or result consistency should update this contract and likely receive an ADR.
