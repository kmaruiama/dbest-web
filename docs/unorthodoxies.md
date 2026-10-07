# Kotlin style

The server uses a small subset of Kotlin, so someone new to the language can still see what each line does.

- Lambdas go inside the parentheses, with named parameters: `require(ok, { "message" })`, `edges.filter({ edge -> edge.to == id })`. Never `it`.
- Name the steps: a big expression becomes a few `val`s.
- Free functions transform data: `tryCompile(query, tables)`, not `query.compile()`. Methods are only for stateful owners: `engine.run`, `engineState.page`, `cursor.next`.
- Maps use `.get` and `.put`; `[]` is for lists.
- Files read top to bottom: each helper sits directly under its first caller.

Not used: scope functions (`let`, `run`, `apply`, `also`, `with`), extension, infix or operator functions, `lateinit`, delegated properties, companion objects, `!!`, reified helpers, and destructuring other than `for ((key, value) in map)`.

One exception: `Compiler.kt` builds engine operators that share a constructor shape through reflection (`make`), so that one table serves both compiling and captions.
