# Persistence contract

A `.dbest` file is a durable editor document, not an engine snapshot. It contains serializable graph/history state and an informational tree listing; it does not contain opened DBest tables, compiled operations, cursors, schema cache entries, or server session IDs.

The format is written and read by [Persistence.kt](../../app/server/src/features/sessions/Persistence.kt). File lifecycle routes are in [SessionRoutes.kt](../../app/server/src/features/sessions/SessionRoutes.kt).

## File contents

```json
{
  "version": 2,
  "history": { "...": "serializable History" },
  "trees": [{ "root": 7, "nodes": [7, 4, 1] }]
}
```

`history` contains the current `Session`, undo steps, redo steps, and history limit. The session contains declarative `TableSpec` values; a CSV/XML/B-tree path is persisted as a path string and is reopened lazily only when a plan needs it. `trees` is derived from the saved graph for inspection and is not used to restore the session.

On load, the server accepts versions 1 and 2 and restores only `history`; it creates a new in-process `Workspace` with a new session ID and empty open-table map. Saving writes atomically. The exact code is `SESSION_FORMAT_VERSION`, `save`, and `load` in [Persistence.kt](../../app/server/src/features/sessions/Persistence.kt), with atomic replacement in [AtomicWrite.kt](../../app/server/src/util/AtomicWrite.kt).

## Compatibility rules

- Increment the file version when a persisted representation changes incompatibly.
- Keep the loader's accepted-version list and migration behavior explicit; do not silently reinterpret old data.
- Do not add runtime table handles, engine objects, cursors, or cache keys to this format.
- Test an old fixture before removing support for a previous version.

The HTTP protocol and `.dbest` format version independently. A compatible saved file does not imply compatibility with an independently deployed browser client; see [the HTTP API contract](http-api.md).
