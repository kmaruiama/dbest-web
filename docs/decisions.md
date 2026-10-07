# Decisions

## 1. Keep the editor graph separate from execution (replaced by 3)

The canvas edits incomplete graphs, so the saved `Session` holds no engine objects. A plan is derived only when a node runs.

## 2. Compile plans at the engine boundary (replaced by 3)

A Kotlin `Plan` sat between nodes and engine `Operation`s. It held opened tables, so it made a poor cache key, and it described every operator a second time.

## 3. One operator vocabulary

A `@Serializable` `Node` is the only description of an operator: JSON, file format, form fields and compile input. `planForNodeById` builds a plain-data `Query`. Every edit is written to the file before it is published. Files are version 3, and versions 1 and 2 migrate on load.

## 4. Correlation scope

Nested-loop and cross joins pass one side's current row to the other, and `lookupSides` derives which side from the engine's class hierarchy. Scope is derived, never stored. Only a nested-loop join may have no terms. A comparison's right side is a value or a column, picked by a switch in the form. Condition and Reference were ported into the engine. The open problem: operators that run their input once (sort, materialize, memoize, hash index, a hash join's build side) silently reuse the first row.

## 5. Layers

The server has four layers (document, compile, runtime, app), one folder each, all in one package. Calls go one way only. `EngineState` owns a workspace's tables and cursors. A set operation reads its inputs' columns from the operations it just built, so nothing is compiled twice.

## 6. Client state as data

Each screen has one state module with a pure `step`, run by `useStep`. The canvas is controlled. The API and catalog come from context. To add an action: add an `Event`, a `step` branch, an `Effect` and `perform` branch if it reaches the server, and the button that dispatches it. React Flow re-adopts every node on each render, which is fine at canvas sizes.
