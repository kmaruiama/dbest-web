# Separate the editor graph from the executable plan

## Context

The canvas must persist and edit a document that is often not executable: a user may place an operator before wiring its inputs, keep several independent pipelines, move nodes, undo commands, or save the work for later. DBest engine operations instead require resolved tables and a complete tree of inputs.

Using engine objects as the canvas model would couple editing to opened resources and make incomplete graphs, serialization, and history awkward. Conversely, a canvas `Node` has no nested input fields; its inputs are `Edge` values so graph connectivity remains editable.

## Decision

Keep `Session` as the persisted editor representation: table specifications, node configurations, edges, and layout. Derive an adapter `Plan` only when inspecting a selected node. The derivation follows that node's incoming edges recursively and resolves table specifications to workspace-owned table handles.

`Node` constructors and command application validate local and graph/document invariants. Missing inputs remain valid editor state and are reported by the problems endpoint rather than rejected as edits.

## Consequences

The UI can save, undo, and display incomplete query graphs without opening or serializing engine resources. A selected intermediate node can be run independently of its downstream consumers.

The representations repeat operator information. A feature change can require coordinated changes to node definitions, plan conversion, plan/compiler code, palette metadata, captions, codecs, and client forms. The graph-to-plan step is also recursive work performed for validation, schema lookup, and execution rather than a cached persistent plan.
