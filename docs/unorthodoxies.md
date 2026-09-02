# Kotlin unorthodoxies

This server intentionally does not follow every common Kotlin idiom. The goal is
not to make the code look more “Java-like”; it is to make the mechanics visible
to people who are learning Kotlin or joining the project without much Kotlin
experience.

These conventions are local to the first-party Kotlin server. They do not apply
to the embedded Java engine.

## Keep call parentheses around lambdas

Kotlin allows the final lambda argument to sit outside a function call:

```kotlin
require(port in 0..65535) { "invalid port" }
```

DBest normally keeps the lambda inside the call:

```kotlin
require(port in 0..65535, { "invalid port" })
```

Both forms mean the same thing. The second form makes the call’s opening and
closing delimiters explicit and shows that the message is an argument evaluated
only when required. This is especially useful while reading nested calls, route
definitions, or callback-heavy code.

Do not move a lambda outside the parentheses merely because Kotlin permits it.
Use the outside form only when it makes a short, familiar API substantially
clearer than the explicit form.

## Prefer named free functions for transformations

Much of the server uses top-level functions such as:

```kotlin
val executable = compile(plan)
val columns = schema(plan)
val tree = plan(session, root, tables)
```

rather than extension-style calls such as:

```kotlin
val executable = plan.compile()
val columns = plan.schema()
val tree = session.plan(root, tables)
```

The free-function form puts the operation first and makes its inputs explicit.
It avoids implying that the receiver owns the work, mutates itself, or has a
method in its domain model. It also keeps the adapter boundary readable: a
`Plan` is data, while `compile(plan)` is a separate transformation that creates
engine objects.

This is not a ban on methods. Use a method when the operation is intrinsic to
the value or its resource lifetime—for example, `operation.close()`—or when a
method name is clearer than a free function.

## Prefer small, purpose-named files over utility receivers

Top-level functions live near the concept they operate on: for example,
`compile`, `schema`, and `execute` live in the adapter package rather than in a
catch-all extension or utility object. This keeps a reader from needing to know
which imports add methods to a type before they can understand a call.

The convention is deliberate: discoverability comes from a descriptive function
name and a small file, not from hiding behavior behind extension-method syntax.

## The rule of thumb

When choosing between an idiomatic shorthand and a more explicit form, prefer
the form that lets a new Kotlin reader answer these questions immediately:

1. What function is being called?
2. Which values are its inputs?
3. Is a lambda an argument or surrounding control flow?
4. Does the value own the operation, or is it being transformed by another
   component?

This document explains readability conventions, not correctness requirements.
Existing code may use idiomatic Kotlin where it remains clearer in context.
