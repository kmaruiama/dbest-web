# Operator extension contract

An operator is a cross-boundary feature, not one class. Its editor form, persisted node, plan representation, DBest compilation, captions, catalog entry, and client decoder must agree on its name, fields, arity, defaults, invariants, and unsupported combinations.

## Change map

| Surface | Responsibility | Living code |
| --- | --- | --- |
| Graph node | Persisted configuration, local checks, input arity, `operatorKind` | [Nodes.kt](../../app/server/src/features/canvas/graph/Nodes.kt) |
| Wire codec | `@type` tag and JSON fields | [NodesCodec.kt](../../app/server/src/features/canvas/graph/NodesCodec.kt) |
| Plan | Complete executable form and invariant checks | [Plan.kt](../../app/server/src/kernel/adapter/Plan.kt) |
| Derivation | Node plus input ports to a plan | [Query.kt](../../app/server/src/features/canvas/query/Query.kt) |
| Compilation | Plan to concrete DBest operation/condition | [compiler](../../app/server/src/kernel/adapter/compile) |
| Catalog/form | Palette metadata and editable field choices | [Catalog.kt](../../app/server/src/features/catalog/Catalog.kt), [client form](../../app/client/src/form/Form.tsx) |
| Captions | Human-readable expression and engine-class display | [Captions.kt](../../app/server/src/features/canvas/query/Captions.kt) |
| Client protocol | Safe decoding of returned graph/catalog/results | [protocol.ts](../../app/client/src/server/protocol.ts) |

## Required questions

Before adding or changing an operator, answer these questions in the change itself:

1. Is it source, unary, or binary, and which ports are required?
2. Which fields are persisted, which defaults are part of the wire contract, and which invariants are local versus engine-validated?
3. How is each graph field translated to `Plan` and then to DBest?
4. What schema, result aliases, condition/column semantics, and failure modes should a user see?
5. Is the engine operation safe for validation, schema lookup, paging, streaming, and export?
6. Which catalog fields and client widgets expose it, and do their choices reflect the engine's real expectations?

## Verification

The strongest existing checks are graph-invariant parity tests in [NodeInvariantParityTest.kt](../../app/server/test/features/canvas/graph/NodeInvariantParityTest.kt), query/caption tests in [canvas query tests](../../app/server/test/features/canvas/query), and browser/server protocol tests in [client contract tests](../../app/client/tests/contract). Add or update coverage at every affected layer; a new compiler branch without a catalog/form/codec test is not a complete operator feature.

The operator contract is intentionally distributed today. A future metadata-driven operator model could reduce repetition, but it would need to preserve the distinct responsibilities above rather than merely move them into one large registry.
