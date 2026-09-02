# Development

## Prerequisites

Use JDK 17 and Node.js 22. Those are the versions configured in CI. `npm` is required for the client; Gradle is provided through `./gradlew`.

The E2E suite also requires Playwright Chromium and its operating-system dependencies. CI prepares it with:

```bash
cd app/client
npx playwright install --with-deps chromium
```

On systems where `--with-deps` is unsuitable, install the browser and dependencies through the local Playwright-supported method before running the suite.

## Clean setup and run

From the repository root:

```bash
make setup
make run
```

`make setup` runs `npm ci` in `app/client`. `make run` invokes `./gradlew :app:server:run`; Gradle's resource processing first runs the client's `npm run build`, then serves the built client from the Kotlin server. The server binds only to the loopback interface and defaults to port 8000. Override it with, for example, `PORT=8080 make run`.

For browser-only UI work, `cd app/client && npm run dev` starts Vite on port 5274 and proxies API requests to `http://localhost:8000` unless `DBEST_API_URL` is set. Start the server separately for that mode.

## Tests and verification

The root Makefile is the supported command surface.

For the complete setup, test, Playwright, client, Gradle, and recovery command
reference, see [Command reference](commands.md).

| Command              | What it runs                                                                                                                                        |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `make test-unit`     | `./gradlew check`, then client Vitest unit tests                                                                                                    |
| `make test-contract` | Client Vitest contract tests; they build or use `DBEST_JAR`, boot the actual server JAR, and exercise client-facing routes and operator definitions |
| `make test-e2e`      | Playwright health suite; it builds an application distribution, starts backend plus Vite in isolated temporary state, and drives the browser        |
| `make verify`        | Unit tests, contract tests, ESLint, TypeScript/Vite build, and Prettier check                                                                       |
| `make test`          | Alias for `make test-unit`                                                                                                                          |

`make verify` is the normal non-browser verification command. It does not run E2E tests, so run `make test-e2e` before changes that need browser coverage.

The testing layers have different purposes:

- Server tests exercise routes, history, graph rules, adapter operations, ingestion, persistence, and export behavior.
- Client unit tests focus on response decoding and local presentation/graph/form code.
- Contract tests ensure the TypeScript decoder/client assumptions remain compatible with a self-booted Kotlin server and its catalog.
- E2E tests assemble representative operator graphs through the browser and verify results and selected UI behavior.

## Build and formatting

```bash
make build   # self-contained application JAR
make fmt     # write client Prettier formatting
make clean   # remove Gradle outputs and app/client/dist
```

`make build` runs `:app:server:bundledJar` and produces `app/server/build/libs/dbest-0.1.0-SNAPSHOT.jar` at the current project version. The JAR includes the backend, DBest engine classes, dependencies, and built client assets. It can be started with:

```bash
java -jar app/server/build/libs/dbest-0.1.0-SNAPSHOT.jar
```

Client linting and format checking are included in `make verify`; their direct commands are `npm run lint` and `npm run format:check` from `app/client`.

## Test artifacts and cleanup

Contract tests create an isolated temporary home/configuration and boot a server on an ephemeral port. E2E tests create an isolated temporary home, start the backend on port 8931 and Vite on 5931, then tear down their process trees and temporary files. If an interrupted E2E run leaves a process behind, stop it before retrying so those fixed ports are free.

`make clean` does not remove `node_modules`, Playwright browsers, or temporary files from an interrupted browser run.
