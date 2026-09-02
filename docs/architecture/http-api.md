# HTTP API contract

The HTTP API is a loopback, browser-facing protocol. The server routes are the primary implementation source; the client decoders are the consumer-side compatibility check. This document records stable semantics, not a generated OpenAPI description.

Server routes live in [CanvasRoutes.kt](../../app/server/src/features/canvas/CanvasRoutes.kt), [NodeRoutes.kt](../../app/server/src/features/canvas/NodeRoutes.kt), [SessionRoutes.kt](../../app/server/src/features/sessions/SessionRoutes.kt), and [ExportRoutes.kt](../../app/server/src/features/export/ExportRoutes.kt). The client uses [api.ts](../../app/client/src/server/api.ts) and decodes every response in [protocol.ts](../../app/client/src/server/protocol.ts). Contract tests are in [app/client/tests/contract](../../app/client/tests/contract).

## Wire data and mutation

JSON representations use `@type` as a discriminator for commands, nodes, table specifications, and conditions. Identifiers are numeric; maps keyed by identifiers are JSON objects keyed by their decimal string form. Commands are posted to `POST /sessions/{sid}/commands`; a successful command, undo, or redo responds with an acknowledgement containing `revision`, undo `depth`, `canUndo`, and `canRedo`.

The canonical complete canvas read is `GET /sessions/{sid}/session`. It returns the document plus captions and the same history/revision state. A command acknowledgement does not include the new document, so clients should reload the view rather than infer all server-side presentation data locally.

Commands and tagged graph data are decoded by [CommandsCodec.kt](../../app/server/src/features/canvas/history/CommandsCodec.kt), [NodesCodec.kt](../../app/server/src/features/canvas/graph/NodesCodec.kt), and [TableSpecsCodec.kt](../../app/server/src/features/canvas/graph/TableSpecsCodec.kt).

## Query endpoints

| Endpoint | Contract |
| --- | --- |
| `GET /sessions/{sid}/roots` | Node IDs with no outgoing edges. |
| `GET /sessions/{sid}/problems` | Missing-input and engine-backed validation problems. Problems are data, not necessarily HTTP failures. |
| `GET /sessions/{sid}/nodes/{id}/schema` | On-demand schema columns for the selected node. |
| `GET /sessions/{sid}/nodes/{id}/exists` | Whether the selected plan yields at least one row. |
| `GET /sessions/{sid}/nodes/{id}/rows?offset=&limit=` | One JSON page: `{ rows, elapsedMs }`. Both parameters are required to select paged mode. |
| `GET /sessions/{sid}/nodes/{id}/rows` | Full result as `application/x-ndjson`; one JSON row per line. |
| `GET /sessions/{sid}/nodes/{id}/export?format=csv|sql&table=` | Attachment generated from selected-node schema and rows. |

Rows are compact arrays whose order follows the schema. The client decodes this result shape in [api.ts](../../app/client/src/server/api.ts). A caller that needs stable correspondence between a result and canvas state must retain the session revision it read; rows and schema responses do not carry one.

## Errors and security

The common error response is JSON with an `error` string. [Errors.kt](../../app/server/src/kernel/http/Errors.kt) maps errors as follows:

| Status | Meaning |
| --- | --- |
| `400` | malformed JSON or invalid request/command parameter |
| `401` | missing or invalid local API token on an unsafe request |
| `404` | missing session, node, or other resource |
| `409` | engine is busy |
| `422` | plan cannot be derived or is invalid |
| `502` | storage failure surfaced by the engine |
| `500` | other engine failure |

`GET /bootstrap` returns the in-memory token required in `X-DBest-Token` for `POST`, `PUT`, `PATCH`, and `DELETE`. This is a same-process/browser protection for the loopback application, not an account-authentication system. The filter is implemented in [Security.kt](../../app/server/src/kernel/http/Security.kt).

## Compatibility policy

The session file format has its own version number, but the HTTP JSON protocol currently does not. A server and bundled browser are released together; changing a tag, required field, result shape, or error behavior should update the client decoder and contract tests in the same change. If independently deployed clients become a supported use case, introduce an explicit protocol version before making incompatible wire changes.
