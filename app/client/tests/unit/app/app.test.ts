import { describe, expect, it } from "vitest";
import { initial, step, type Board, type State } from "../../../src/app/app";
import type { SessionMeta } from "../../../src/server/types";

const tab = (sid: string): SessionMeta => ({
  sid,
  name: sid,
  file: `/s/${sid}.dbest`,
});

const board = (tabs: string[], active: string | null): Board => ({
  config: { sessionsDir: "/s", open: [] },
  files: [],
  tabs: tabs.map(tab),
  active,
});

const ready = (value: Board): State => ({
  ...initial.state,
  board: { kind: "ready", value },
});

const boardOf = (state: State): Board => {
  if (state.board.kind !== "ready") throw new Error("board not ready");
  return state.board.value;
};

describe("app step", () => {
  it("starts by loading the board", () => {
    expect(initial.state.board).toEqual({ kind: "loading" });
    expect(initial.effects).toEqual([{ kind: "loadBoard" }]);
  });

  it("retrying loads the board again", () => {
    const failed = step(initial.state, { kind: "boardFailed", message: "x" });
    const retried = step(failed.state, { kind: "retryClicked" });
    expect(retried.state.board).toEqual({ kind: "loading" });
    expect(retried.effects).toEqual([{ kind: "loadBoard" }]);
  });

  it("a chosen name closes the dialog and asks the server to create", () => {
    const naming = step(ready(board([], null)), { kind: "newSessionClicked" });
    expect(naming.state.naming).toBe(true);
    const chosen = step(naming.state, { kind: "nameChosen", name: "movies" });
    expect(chosen.state.naming).toBe(false);
    expect(chosen.effects).toEqual([{ kind: "create", name: "movies" }]);
  });

  it("a created session becomes the active tab and the file list refreshes", () => {
    const created = step(ready(board(["a"], "a")), {
      kind: "sessionCreated",
      meta: tab("b"),
    });
    expect(boardOf(created.state).tabs.map((meta) => meta.sid)).toEqual([
      "a",
      "b",
    ]);
    expect(boardOf(created.state).active).toBe("b");
    expect(created.effects).toEqual([{ kind: "listFiles" }]);
  });

  it("opening a file that is already open shows its tab without a copy", () => {
    const opened = step(ready(board(["a", "b"], "b")), {
      kind: "sessionOpened",
      meta: tab("a"),
    });
    expect(boardOf(opened.state).tabs.map((meta) => meta.sid)).toEqual([
      "a",
      "b",
    ]);
    expect(boardOf(opened.state).active).toBe("a");
  });

  it("closing the active tab activates the last one left", () => {
    const closed = step(ready(board(["a", "b", "c"], "b")), {
      kind: "sessionClosed",
      sid: "b",
    });
    expect(boardOf(closed.state).active).toBe("c");
    const other = step(ready(board(["a", "b"], "a")), {
      kind: "sessionClosed",
      sid: "b",
    });
    expect(boardOf(other.state).active).toBe("a");
  });

  it("the main menu deactivates the tab, and a tab click activates it", () => {
    const menu = step(ready(board(["a"], "a")), { kind: "mainMenuClicked" });
    expect(boardOf(menu.state).active).toBeNull();
    const back = step(menu.state, { kind: "tabClicked", sid: "a" });
    expect(boardOf(back.state).active).toBe("a");
  });

  it("an answer that arrives before the board leaves it loading", () => {
    const early = step(initial.state, { kind: "filesListed", files: [] });
    expect(early.state.board).toEqual({ kind: "loading" });
  });

  it("a failure is shown until dismissed", () => {
    const failed = step(ready(board([], null)), {
      kind: "failed",
      message: "nope",
    });
    expect(failed.state.error).toBe("nope");
    expect(
      step(failed.state, { kind: "errorDismissed" }).state.error,
    ).toBeNull();
  });

  it("exiting shuts the server down", () => {
    const exited = step(ready(board([], null)), { kind: "exitConfirmed" });
    expect(exited.state.exited).toBe(true);
    expect(exited.effects).toEqual([{ kind: "shutdown" }]);
  });
});
