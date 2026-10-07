# Development

Run these from the repository root. `make help` lists the targets.

| Command | Does |
| --- | --- |
| `make run` | builds the client and serves the app on :8000 |
| `make test-unit` | Gradle `check` (JVM tests, ktfmt check) and Vitest |
| `make verify` | `test-unit`, ESLint, the Prettier check and the client build |
| `make test-e2e` | the Playwright browser suite |
| `make fmt` | Prettier and ktfmt |
| `make build` | `build/libs/dbest-0.1.0-SNAPSHOT.jar` |
| `make clean` | build output only; `node_modules` stays |

To work on the client alone, run `./gradlew :app:client:npmInstall` once, then `./gradlew :app:server:run` and `cd app/client && npm run dev`. Vite serves on :5274 and proxies to `DBEST_API_URL`, or :8000 by default.

## Browser tests

```bash
cd app/client && npx playwright install chromium        # once
cd app/client && npm run test:e2e -- tests/health/selection.spec.ts
cd app/client && npm run test:e2e -- -g "escape clears the selection"
```

The suite starts the backend on 8931 and Vite on 5931 with a temporary home. `HEALTH_BACKEND_PORT` and `HEALTH_VITE_PORT` override the ports.

## Golden files

`app/server/test/golden` holds output guaranteed to be correct. A test will fail on any difference. When a change is intended, regenerate the file and review the difference.

- `operators.json`: the palette catalog (`CatalogTest`)
- `view-v1.json`, `view-v2.json`: views of the old fixture files (`GraphTest`)
- `rows-v2.json`: every v2 root's rows and errors (`CompilerTest`)
- `session-v3.dbest`: a saved file, byte for byte (`GraphTest`)
