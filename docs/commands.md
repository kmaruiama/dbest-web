# Command reference

Run commands from the repository root unless a command begins with `cd app/client`.
The root `Makefile` is the supported day-to-day interface; use `make help` to list
its targets.

## Setup and application

```bash
make setup                         # install locked client dependencies
make run                           # build the client and run the integrated server
PORT=8080 make run                 # run the integrated server on another loopback port
make build                         # create app/server/build/libs/dbest-0.1.0-SNAPSHOT.jar
java -jar app/server/build/libs/dbest-0.1.0-SNAPSHOT.jar
make clean                         # remove Gradle outputs and app/client/dist
```

The integrated application listens on `http://localhost:8000` by default.

## Verification and tests

```bash
make test                          # alias for make test-unit
make test-unit                     # Gradle check plus client unit tests
make test-contract                 # client/server contract tests
make test-e2e                      # complete Playwright browser health suite
make verify                        # unit + contract + client lint/build/format check
```

`make verify` intentionally does not run the browser suite. Run `make test-e2e`
when a change needs browser coverage or before a release candidate.

## Playwright browser tests

Install Chromium once after `make setup`:

```bash
cd app/client
npx playwright install chromium
```

On Linux CI-like machines where Playwright may install operating-system packages,
use:

```bash
cd app/client
npx playwright install --with-deps chromium
```

Run all browser tests from the root:

```bash
make test-e2e
```

Run a single file, a test selected by title, or a headed/slow run:

```bash
cd app/client && npm run test:e2e -- tests/health/selection.spec.ts
cd app/client && npm run test:e2e -- -g "escape clears the selection"
cd app/client && npm run test:e2e -- --verbose
```

The suite builds an application distribution, starts a backend on port `8931` and
Vite on port `5931`, creates temporary session state, then tears everything down.
If those ports are in use, choose alternatives for that run:

```bash
cd app/client
HEALTH_BACKEND_PORT=8932 HEALTH_VITE_PORT=5932 npm run test:e2e
```

## Client-only work

```bash
cd app/client && npm run dev                    # Vite development server
cd app/client && DBEST_API_URL=http://localhost:8000 npm run dev
cd app/client && npm run build                  # type-check and production bundle
cd app/client && npm run preview                # serve the built bundle locally
cd app/client && npm run lint
cd app/client && npm run format:check
cd app/client && npm run format                 # write Prettier formatting
cd app/client && npm run test:unit
cd app/client && npm run test:unit:cov
cd app/client && npm run test:watch
cd app/client && npm run test:contract
```

`npm run dev` uses `http://localhost:8000` for its API unless `DBEST_API_URL` is
set. Start `make run` separately when using client-only development mode.

## Gradle commands

```bash
./gradlew check                         # server and engine checks
./gradlew :app:server:run               # run the server; also builds client resources
./gradlew :app:server:bundledJar        # build the self-contained application JAR
./gradlew :app:server:installDist       # build the server distribution used by E2E setup
./gradlew tasks --all                   # inspect available Gradle tasks
```

## CI-equivalent sequence

```bash
make setup
make verify
make test-e2e
```

Do not use `make clean` as a substitute for fixing a failing test: it removes
generated output only.
