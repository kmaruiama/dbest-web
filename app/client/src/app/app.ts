import type { Config, FileEntry, SessionMeta } from "../server/types";
import type { Async } from "../ui/useLoad";
import type { Stepped } from "../ui/useStep";

export type Board = {
  config: Config;
  files: FileEntry[];
  tabs: SessionMeta[];
  active: string | null;
};

export type State = {
  board: Async<Board>;
  naming: boolean;
  exited: boolean;
  error: string | null;
};

export type Event =
  | { kind: "boardLoaded"; board: Board }
  | { kind: "boardFailed"; message: string }
  | { kind: "retryClicked" }
  | { kind: "tabClicked"; sid: string }
  | { kind: "mainMenuClicked" }
  | { kind: "newSessionClicked" }
  | { kind: "namingCancelled" }
  | { kind: "nameChosen"; name: string }
  | { kind: "fileClicked"; path: string }
  | { kind: "tabRenamed"; sid: string; name: string }
  | { kind: "tabCloseClicked"; sid: string }
  | { kind: "folderClicked" }
  | { kind: "exitConfirmed" }
  | { kind: "sessionCreated"; meta: SessionMeta }
  | { kind: "sessionOpened"; meta: SessionMeta }
  | { kind: "sessionRenamed"; meta: SessionMeta }
  | { kind: "sessionClosed"; sid: string }
  | { kind: "folderChosen"; config: Config }
  | { kind: "filesListed"; files: FileEntry[] }
  | { kind: "failed"; message: string }
  | { kind: "errorDismissed" };

export type Effect =
  | { kind: "loadBoard" }
  | { kind: "create"; name: string }
  | { kind: "open"; path: string }
  | { kind: "rename"; sid: string; name: string }
  | { kind: "close"; sid: string }
  | { kind: "chooseFolder" }
  | { kind: "listFiles" }
  | { kind: "shutdown" };

export const initial: Stepped<State, Effect> = {
  state: {
    board: { kind: "loading" },
    naming: false,
    exited: false,
    error: null,
  },
  effects: [{ kind: "loadBoard" }],
};

export function step(state: State, event: Event): Stepped<State, Effect> {
  switch (event.kind) {
    case "boardLoaded":
      return only({ ...state, board: { kind: "ready", value: event.board } });
    case "boardFailed":
      return only({
        ...state,
        board: { kind: "failed", message: event.message },
      });
    case "retryClicked":
      return {
        state: { ...state, board: { kind: "loading" } },
        effects: [{ kind: "loadBoard" }],
      };
    case "tabClicked":
      return only(onBoard(state, (board) => ({ ...board, active: event.sid })));
    case "mainMenuClicked":
      return only(onBoard(state, (board) => ({ ...board, active: null })));
    case "newSessionClicked":
      return only({ ...state, naming: true });
    case "namingCancelled":
      return only({ ...state, naming: false });
    case "nameChosen":
      return {
        state: { ...state, naming: false },
        effects: [{ kind: "create", name: event.name }],
      };
    case "fileClicked":
      return { state, effects: [{ kind: "open", path: event.path }] };
    case "tabRenamed":
      return {
        state,
        effects: [{ kind: "rename", sid: event.sid, name: event.name }],
      };
    case "tabCloseClicked":
      return { state, effects: [{ kind: "close", sid: event.sid }] };
    case "folderClicked":
      return { state, effects: [{ kind: "chooseFolder" }] };
    case "exitConfirmed":
      return {
        state: { ...state, exited: true },
        effects: [{ kind: "shutdown" }],
      };
    case "sessionCreated":
      return {
        state: onBoard(state, (board) => showTab(board, event.meta)),
        effects: [{ kind: "listFiles" }],
      };
    case "sessionOpened":
      return only(onBoard(state, (board) => showTab(board, event.meta)));
    case "sessionRenamed":
      return {
        state: onBoard(state, (board) => ({
          ...board,
          tabs: board.tabs.map((tab) =>
            tab.sid === event.meta.sid ? event.meta : tab,
          ),
        })),
        effects: [{ kind: "listFiles" }],
      };
    case "sessionClosed":
      return only(onBoard(state, (board) => closeTab(board, event.sid)));
    case "folderChosen":
      return {
        state: onBoard(state, (board) => ({ ...board, config: event.config })),
        effects: [{ kind: "listFiles" }],
      };
    case "filesListed":
      return only(
        onBoard(state, (board) => ({ ...board, files: event.files })),
      );
    case "failed":
      return only({ ...state, error: event.message });
    case "errorDismissed":
      return only({ ...state, error: null });
  }
}

function only(state: State): Stepped<State, Effect> {
  return { state, effects: [] };
}

function onBoard(state: State, change: (board: Board) => Board): State {
  if (state.board.kind !== "ready") return state;
  return {
    ...state,
    board: { kind: "ready", value: change(state.board.value) },
  };
}

function showTab(board: Board, meta: SessionMeta): Board {
  const open = board.tabs.some((tab) => tab.sid === meta.sid);
  return {
    ...board,
    tabs: open
      ? board.tabs.map((tab) => (tab.sid === meta.sid ? meta : tab))
      : [...board.tabs, meta],
    active: meta.sid,
  };
}

function closeTab(board: Board, sid: string): Board {
  const left = board.tabs.filter((tab) => tab.sid !== sid);
  return {
    ...board,
    tabs: left,
    active: board.active === sid ? (left.at(-1)?.sid ?? null) : board.active,
  };
}
