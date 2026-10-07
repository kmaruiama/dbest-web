import { describe, expect, it } from "vitest";
import {
  atEnd,
  hasNextPage,
  initial,
  PAGE,
  shownRows,
  step,
  streaming,
  totalShown,
  type Event,
  type State,
} from "../../../src/results/results";
import type { FormValue, RowsPage } from "../../../src/server/types";

const rows = (n: number, from = 0): FormValue[][] =>
  Array.from({ length: n }, (_, index) => [from + index]);

const page = (n: number, from = 0): RowsPage => ({
  rows: rows(n, from),
  elapsedMs: 3,
});

const run = (events: Event[], start: State = initial.state) =>
  events.reduce((state, event) => step(state, event).state, start);

describe("results step", () => {
  it("opens on the first page", () => {
    expect(initial.effects).toEqual([{ kind: "fetchPage", offset: 0 }]);
  });

  it("a full page offers a next one; a short page confirms the total", () => {
    const full = run([{ kind: "pageLoaded", offset: 0, page: page(PAGE) }]);
    expect(hasNextPage(full)).toBe(true);
    expect(totalShown(full)).toEqual({ n: PAGE, confirmed: false });
    const short = run([{ kind: "pageLoaded", offset: 0, page: page(3) }]);
    expect(hasNextPage(short)).toBe(false);
    expect(totalShown(short)).toEqual({ n: 3, confirmed: true });
  });

  it("next asks for the next page, and an answer for an old page is ignored", () => {
    const first = run([{ kind: "pageLoaded", offset: 0, page: page(PAGE) }]);
    const next = step(first, { kind: "nextClicked" });
    expect(next.effects).toEqual([{ kind: "fetchPage", offset: PAGE }]);
    const stale = step(next.state, {
      kind: "pageLoaded",
      offset: 0,
      page: page(PAGE),
    }).state;
    expect(stale).toEqual(next.state);
  });

  it("showing all streams, then pages through memory at the last page", () => {
    const all = step(initial.state, { kind: "allClicked" });
    expect(all.effects).toEqual([{ kind: "stream" }]);
    expect(streaming(all.state)).toBe(true);
    const done = run(
      [
        { kind: "streamProgressed", rows: rows(60), elapsedMs: 5 },
        { kind: "streamFinished", rows: rows(120), elapsedMs: 9 },
      ],
      all.state,
    );
    expect(streaming(done)).toBe(false);
    expect(done.offset).toBe(100);
    expect(shownRows(done)).toEqual(rows(20, 100));
    expect(atEnd(done)).toBe(true);
    const back = step(done, { kind: "firstClicked" });
    expect(back.effects).toEqual([]);
    expect(shownRows(back.state)).toEqual(rows(PAGE));
  });

  it("a stream that fails keeps the reason and stops spinning", () => {
    const failed = run([
      { kind: "allClicked" },
      { kind: "streamFailed", failure: { message: "busy", busy: true } },
    ]);
    expect(streaming(failed)).toBe(false);
    expect(failed.mode === "all" && failed.failure).toEqual({
      message: "busy",
      busy: true,
    });
  });
});
