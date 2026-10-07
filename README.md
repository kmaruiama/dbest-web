# DBest

Build relational queries on a canvas. Drop tables on it (in memory, CSV, XML or B-tree), wire them into operators, and inspect the rows of any node: paged, streamed, compared with another node, or exported as CSV or SQL.

## Run it

Needs JDK 17 and Node.js 22.

```bash
make run         # http://localhost:8000, loopback only; PORT changes the port
make verify      # unit suites, lint, format check, client build
make test-e2e    # browser suite
make build       # build/libs/dbest-0.1.0-SNAPSHOT.jar
```

## Layout

```text
app/client        React + Vite
app/server/src    Kotlin, in four layers: document/ compile/ runtime/ app/
modules/engine    the Java DBest engine
docs/             architecture, client, development, Kotlin style, decisions
```

The browser sends every edit to the server as a command. The server saves the session file, then answers. To show a node's rows, the server compiles the node and everything feeding it into engine operations.

DBest is in `0.x`. Session files carry a version and older ones are migrated on load.
