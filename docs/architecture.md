# Architecture

## Layers

The server is one package, `dbest`, in four folders. A file should use the layers above it and its own layer, but never the layer below itself.

| Folder | Files | Knows                |
| --- | --- |----------------------|
| `document/` | `Operators.kt` nodes and conditions · `Graph.kt` session, commands, undo, graph walks · `Json.kt` wire format and file migration | plain values only    |
| `compile/` | `Compiler.kt` node → `Query` → engine `Operation` · `Tables.kt` opening tables · `Correlation.kt` what flows down a tree | the engine's classes |
| `runtime/` | `Engine.kt` engine thread, `Cursor`, `EngineState`, schema cache | threads and closing  |
| `app/` | routes: `Execution`, `Workspaces`, `Renaming`, `Catalog`, `Comparator`, `Export`, `Ingest`, `Dialogs`, `Server` | everything else      |

## The document

A `Session` is plain data (tables, nodes, edges, layout): it opens no file and touches no engine object, so it can be saved, compared and undone as is.

- Nodes: one `@Serializable` class per operator, in `Operators.kt`. That class is, at once, the JSON, the file format, what `compile` switches on and the source of its form (`Catalog.kt` reads its serial descriptor; `@Input(Widget.COLUMN)` marks a string that names a column). A node validates itself in `init`: a malformed node cannot exist, not even one decoded from a file.
- Edges: an edge goes from a producer to one port of a consumer (`ONLY` for unary nodes, `LEFT`/`RIGHT` for binary ones). Inputs are edges, not node fields, which is why a node can sit unwired; `/problems` reports the gap instead of refusing the edit.
- Columns: always `"source.column"` strings, split only where the engine needs the parts.
- Commands: `apply` returns the next session, or refuses the command with a 400; `invert` builds the command that undoes it. `EditorState` keeps at most 200 undo steps.
- Source names: a node that starts a source names it with `alias` (`aliasOf` lists which ones). Two rules hold: no dot in a name (`a.b.dept` would be ambiguous), and no two nodes of one tree with the same name (`sourceClashes`). A rename (`Renaming.kt`) rewrites every reader in one batch, so one undo reverts all of it.

Adding an operator takes a `Node` class, a branch in `compile` and in `engineClass`, a caption in `expression`, an entry in `aliasOf` and a palette chip. Nothing else describes an operator.

## From a node to rows

```text
planForNodeById -> tryCompile -> Cursor
```

`planForNodeById` follows edges upstream from the node into a `Query`. Downstream nodes are ignored, which is why any node (even one in the middle of a chain) can run. A `Query` is plain data: two equal trees are equal values, so caches and cursors are keyed by it. A missing input is a `PlanError` (422).

`tryCompile` builds the engine operations bottom-up and opens each table on first use. Engine failures become the server's own: `PlanError` (422), `StorageError` (502), `EngineFailure` (500).

`Cursor` reads forward only (`hasNext`, `next`, `skipTo`, `take`, `close`). Pages, streams, export, the comparator and CSV previews read through it; XML previews do not, since the engine's `XMLRecognizer` reads the file itself.

`GET …/rows` serves rows in one of two modes:

- With `offset` and `limit`: one page. `EngineState` keeps up to 8 cursors per workspace, so the next page continues the same cursor; going back re-runs the query (a cursor cannot rewind).
- With neither: every row, as NDJSON, in chunks of 64 KB or 100 ms of work. A failure mid-stream ends the body with an `{"error": …}` line.

With only one of the two parameters, the answer is 400.

## The engine thread

The engine keeps global state (its work counters among it), so the server has one engine thread and one permit (`Engine`).

| Method | When the engine is busy | Used for |
| --- | --- | --- |
| `run` | waits 1 s, then answers 409 | pages, export, compare |
| `runWaitingIndefinitely` | waits | schema, form columns, problems, the old name before a rename, closing a tab |
| `stream` | like `run` | "show all"; holds the permit until the stream ends |

`schema` caches up to 256 trees. `/problems` reports missing inputs -> source clashes -> each complete root's engine error, in that order.

## Correlation

Rows flow up the tree; one thing flows down. A nested-loop join or a cross join runs its right side once per left row, and that side may read the left row's columns (the nested-loop right join does the reverse, since the engine builds it with its sides swapped). That is how a correlated subquery is built.

`Correlation.kt` is the one place that knows this:

- `lookupSides`: a join's provider and receiver ports, read from the engine's class hierarchy (`LookupJoin` and `CrossJoin` correlate);
- `receivingJoins`: the joins whose receiving side a node feeds;
- `readers`: every node that reads a source, which renaming needs.

`outerColumns` offers those columns only in condition fields and in operators without inputs: the engine resolves them nowhere else. A correlated node run alone answers 422 and names the join to run instead (`withJoinHint`).

One gap remains. Sort, materialize, memoize, hash index and a hash join's build side run their input once, so a correlated subtree below them reuses the first row without any error. Nothing guards against it yet.

## Comparing

`GET …/compare/{other}?limit=` measures two nodes of different trees in one engine job. Each side opens its own tables before the counters are zeroed (opening is not counted), and the counters are read before `exhausted` is asked (asking computes one more row).

## Persistence

A session is a `.dbest` file, written atomically before the edit is published: an edit that cannot be saved is not applied, and there is no save button. The format is `{"version": 3, "history": …}` (`@SerialName` keeps the on-disk name `history` for `EditorState`). `migrate` loads versions 1 and 2. `~/.dbest/config.json` keeps the sessions folder and the open files, which are reopened at start.

## HTTP

Unsafe methods need the `X-DBest-Token` header, served by `GET /bootstrap`. Every error is JSON: `{"error": message}`.

```text
/bootstrap  /shutdown  /operators  /config  /config/sessions-dir  /files
/sessions  /sessions/open  /pick-file  /csv-preview  /xml-preview
/sessions/{sid}/ session close rename commands undo redo problems
/sessions/{sid}/nodes/{id}/ alias schema columns rows compare/{other} export
```

Status codes: 400 bad input · 401 token · 404 not found · 409 busy or file exists · 422 bad plan · 500 engine or I/O · 502 storage · 503 no file chooser.
