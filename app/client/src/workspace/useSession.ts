import { useCallback, useEffect, useRef, useState } from "react";
import { api, messageOf } from "../server/api";
import type {
  Caption,
  Command,
  NodeId,
  Problem,
  Session,
  SessionView,
} from "../server/types";

const EMPTY: Session = {
  tables: new Map(),
  nodes: new Map(),
  edges: [],
  layout: new Map(),
};

const NO_PROBLEMS: Problem[] = [];
const NO_CAPTIONS: Map<NodeId, Caption> = new Map();

export type Load =
  | {
      kind: "loading";
    }
  | {
      kind: "failed";
      message: string;
    }
  | {
      kind: "ready";
      view: SessionView;
      problems: Problem[];
    };

type SessionStore = {
  load: Load;
  session: Session;
  captions: Map<NodeId, Caption>;
  problems: Problem[];
  error: string | null;
  busy: boolean;
  sendCommand: (command: Command) => Promise<CommandResult>;
  undo: () => void;
  redo: () => void;
  refresh: () => void;
  dismissError: () => void;
};

export type CommandResult =
  | {
      kind: "success";
      view: SessionView;
    }
  | {
      kind: "failed";
    };

export function useSession(sid: string, onMutate?: () => void): SessionStore {
  const [load, setLoad] = useState<Load>({ kind: "loading" });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const queue = useRef<Promise<unknown>>(Promise.resolve());
  const inFlight = useRef(0);
  const generation = useRef(0);
  const refresh = useCallback(async () => {
    const current = ++generation.current;
    try {
      const [view, problems] = await Promise.all([
        api.session(sid),
        api.problems(sid),
      ]);
      if (current === generation.current)
        setLoad({ kind: "ready", view, problems });
    } catch (caught) {
      if (current === generation.current)
        setLoad({ kind: "failed", message: messageOf(caught) });
    }
  }, [sid]);
  const run = useCallback(
    (action: () => Promise<unknown>): Promise<CommandResult> => {
      inFlight.current = inFlight.current + 1;
      setBusy(true);
      const next = queue.current.then(async () => {
        let result: CommandResult = { kind: "failed" };
        const current = ++generation.current;
        try {
          await action();
          const [view, problems] = await Promise.all([
            api.session(sid),
            api.problems(sid),
          ]);
          if (current === generation.current)
            setLoad({ kind: "ready", view, problems });
          result = { kind: "success", view };
          if (onMutate) onMutate();
        } catch (caught) {
          setError(messageOf(caught));
        }
        inFlight.current = inFlight.current - 1;
        if (inFlight.current === 0) setBusy(false);
        return result;
      });
      queue.current = next;
      return next;
    },
    [onMutate, sid],
  );
  const sendCommand = useCallback(
    (command: Command) => run(() => api.commands(sid, command)),
    [run, sid],
  );
  const undo = () => run(() => api.undo(sid));
  const redo = () => run(() => api.redo(sid));
  useEffect(() => {
    void refresh();
  }, [refresh]);
  return {
    load,
    session: load.kind === "ready" ? load.view.session : EMPTY,
    captions: load.kind === "ready" ? load.view.captions : NO_CAPTIONS,
    problems: load.kind === "ready" ? load.problems : NO_PROBLEMS,
    error,
    busy,
    sendCommand,
    undo,
    redo,
    refresh: () => void refresh(),
    dismissError: () => setError(null),
  };
}
