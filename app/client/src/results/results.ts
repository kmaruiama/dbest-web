import type { FormValue, RowsPage } from "../server/types";
import type { Stepped } from "../ui/useStep";

export const PAGE = 50;

export type Failure = { message: string; busy: boolean };

export type State =
  | {
      mode: "pages";
      offset: number;
      page:
        | { kind: "running" }
        | { kind: "failed"; failure: Failure }
        | { kind: "ready"; page: RowsPage };
    }
  | {
      mode: "all";
      offset: number;
      rows: FormValue[][] | null;
      elapsedMs: number;
      complete: boolean;
      failure: Failure | null;
    };

export type Event =
  | { kind: "pageLoaded"; offset: number; page: RowsPage }
  | { kind: "pageFailed"; offset: number; failure: Failure }
  | { kind: "streamProgressed"; rows: FormValue[][]; elapsedMs: number }
  | { kind: "streamFinished"; rows: FormValue[][]; elapsedMs: number }
  | { kind: "streamFailed"; failure: Failure }
  | { kind: "firstClicked" }
  | { kind: "previousClicked" }
  | { kind: "nextClicked" }
  | { kind: "allClicked" };

export type Effect = { kind: "fetchPage"; offset: number } | { kind: "stream" };

export const initial: Stepped<State, Effect> = {
  state: { mode: "pages", offset: 0, page: { kind: "running" } },
  effects: [{ kind: "fetchPage", offset: 0 }],
};

export function step(state: State, event: Event): Stepped<State, Effect> {
  switch (event.kind) {
    case "pageLoaded":
      return only(
        state.mode === "pages" && state.offset === event.offset
          ? { ...state, page: { kind: "ready", page: event.page } }
          : state,
      );
    case "pageFailed":
      return only(
        state.mode === "pages" && state.offset === event.offset
          ? { ...state, page: { kind: "failed", failure: event.failure } }
          : state,
      );
    case "streamProgressed":
      return only(
        state.mode === "all"
          ? { ...state, rows: event.rows, elapsedMs: event.elapsedMs }
          : state,
      );
    case "streamFinished":
      return only(
        state.mode === "all"
          ? {
              ...state,
              rows: event.rows,
              elapsedMs: event.elapsedMs,
              complete: true,
              offset: lastPage(event.rows.length),
            }
          : state,
      );
    case "streamFailed":
      return only(
        state.mode === "all" ? { ...state, failure: event.failure } : state,
      );
    case "firstClicked":
      return moveTo(state, 0);
    case "previousClicked":
      return moveTo(state, Math.max(0, state.offset - PAGE));
    case "nextClicked":
      return moveTo(state, state.offset + PAGE);
    case "allClicked":
      if (state.mode === "all")
        return only(
          state.complete && state.rows !== null
            ? { ...state, offset: lastPage(state.rows.length) }
            : state,
        );
      return {
        state: {
          mode: "all",
          offset: 0,
          rows: null,
          elapsedMs: 0,
          complete: false,
          failure: null,
        },
        effects: [{ kind: "stream" }],
      };
  }
}

export function shownRows(state: State): FormValue[][] {
  if (state.mode === "pages")
    return state.page.kind === "ready" ? state.page.page.rows : [];
  return (state.rows ?? []).slice(state.offset, state.offset + PAGE);
}

export function failureOf(state: State): Failure | null {
  if (state.mode === "pages")
    return state.page.kind === "failed" ? state.page.failure : null;
  return state.failure;
}

export function running(state: State): boolean {
  if (state.mode === "pages") return state.page.kind === "running";
  return state.rows === null && state.failure === null;
}

export function streaming(state: State): boolean {
  return state.mode === "all" && !state.complete && state.failure === null;
}

export function hasNextPage(state: State): boolean {
  if (state.mode === "pages")
    return state.page.kind === "ready" && state.page.page.rows.length >= PAGE;
  return (
    state.complete &&
    state.rows !== null &&
    state.offset + PAGE < state.rows.length
  );
}

export function atEnd(state: State): boolean {
  return (
    state.mode === "all" &&
    state.complete &&
    state.rows !== null &&
    state.offset + PAGE >= state.rows.length
  );
}

export function totalShown(
  state: State,
): { n: number; confirmed: boolean } | null {
  if (state.mode === "all") {
    if (state.rows === null) return null;
    return { n: state.rows.length, confirmed: state.complete };
  }
  if (state.page.kind !== "ready") return null;
  const rows = state.page.page.rows.length;
  return { n: state.offset + rows, confirmed: rows < PAGE };
}

export function elapsedMs(state: State): number | null {
  if (state.mode === "all") return state.rows === null ? null : state.elapsedMs;
  return state.page.kind === "ready" ? state.page.page.elapsedMs : null;
}

export function pageNumber(state: State): number {
  return Math.floor(state.offset / PAGE) + 1;
}

function moveTo(state: State, offset: number): Stepped<State, Effect> {
  if (state.mode === "all") return only({ ...state, offset });
  return {
    state: { mode: "pages", offset, page: { kind: "running" } },
    effects: [{ kind: "fetchPage", offset }],
  };
}

function lastPage(total: number): number {
  return total === 0 ? 0 : Math.floor((total - 1) / PAGE) * PAGE;
}

function only(state: State): Stepped<State, Effect> {
  return { state, effects: [] };
}
