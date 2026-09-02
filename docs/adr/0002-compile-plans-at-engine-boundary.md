# Compile adapter plans at the engine boundary

## Context

The application needs a representation that validation, schema inference, captions, and execution can consume without exposing DBest's Java `Operation` classes to the browser or editor model. The engine, however, executes a behavior-rich object graph with concrete operation subclasses, lookup filters, opened tables, iterators, and `close()` lifecycles.

## Decision

Represent the executable description as structural Kotlin `Plan` data classes and keep schema, validation, execution, and compilation as adapter functions. Immediately before engine work, recursively compile a `Plan` into concrete DBest `Operation` objects. Conditions and algorithm choices are translated at the same boundary.

The plan hierarchy is structurally immutable. Its table leaf contains a runtime `TableHandle`, so it is intentionally an in-process adapter representation, not a persisted or network protocol.

## Consequences

The application has one explicit mapping from its operator vocabulary to DBest classes and can run schema inference or validation without changing the editor document. Engine exceptions can be translated into application-level plan, storage, or engine failures at the boundary.

Compilation creates a new engine object graph for each operation request, and the adapter must mirror engine capabilities and unsupported combinations. Resource ownership is split: workspaces own opened table handles, while each compiled operation must be closed by its caller. This makes cache and temporary-operation lifecycle management a continuing concern.
