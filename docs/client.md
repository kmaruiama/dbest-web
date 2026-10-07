# Client

## Folders

```text
app/        the page: boot, tabs, launcher, settings
workspace/  one open tab
canvas/ form/ results/ tables/   features
server/     types, api, catalog, graph, ids, literals, nodes
ui/         Dialog, Status, settings, preferences, theme, useStep, useLoad…
i18n/       strings
```

A folder imports only from the folders below it in this list. One feature never imports another. ESLint enforces both rules.

## Four file shapes

- State `thing.ts`: `State`, an `Event` union, an `Effect` union, `initial`, a pure `step`, then helpers and selectors. No React and no fetch. These are `app/app.ts`, `workspace/workspace.ts`, `canvas/canvas.ts`, `results/results.ts`, `results/comparator.ts` and `tables/tables.ts`.
- View `Thing.tsx`: `type Props` first, then hooks, derived values, `handleX` handlers and JSX. Private components go below. A view that owns a state module ends with `perform`, which turns effects into API calls.
- `server/api.ts`: `connect()` returns an `Api`. Writes to one session go out in order.
- Pure helpers: data in, data out.

Rules: top-level `function` declarations; every load is an `Async<T>`; every modal is a `<Dialog>`; no `useRef` for state.

## State

Shared state, and anything that talks to the server, goes in a state module run by `useStep(step, initial, run)`. After each render, the hook hands new effects to the latest `run`. Each `step` drops stale answers by their offset, count or sequence number. Local details, like a draft or an open list, stay in `useState`. Preferences live in `ui/settings.tsx` and are stored through `ui/preferences.ts`.

The canvas is controlled. `projectNodes` builds React Flow's nodes from the state on every render, and React Flow reports changes back as events.

## Tests

Every `step` and pure helper is unit-tested in `tests/unit/`. The browser suite (`tests/health`) depends on about sixty `data-testid`s, React Flow's `rf__node-{id}` and `rf__edge-{from}-{to}-{port}` IDs, and the classes `.node-box`, `.node-symbol` and `.node-alias`. Keep all of these.
