import { useCallback, useEffect, useState } from "react";
import { messageOf } from "../server/api";

export type Async<T> =
  | { kind: "loading" }
  | { kind: "failed"; message: string }
  | { kind: "ready"; value: T };

type Loaded<T> = {
  load: (signal: AbortSignal) => Promise<T>;
  attempt: number;
  state: Async<T>;
};

type Load<T> = {
  state: Async<T>;
  reload: () => void;
};

export function useLoad<T>(load: (signal: AbortSignal) => Promise<T>): Load<T> {
  const [attempt, setAttempt] = useState(0);
  const [loaded, setLoaded] = useState<Loaded<T> | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    const settle = (state: Async<T>) => {
      if (!controller.signal.aborted) setLoaded({ load, attempt, state });
    };
    load(controller.signal).then(
      (value) => settle({ kind: "ready", value }),
      (caught: unknown) =>
        settle({ kind: "failed", message: messageOf(caught) }),
    );
    return () => controller.abort();
  }, [load, attempt]);
  const current =
    loaded !== null && loaded.load === load && loaded.attempt === attempt;
  const reload = useCallback(() => setAttempt((count) => count + 1), []);
  return {
    state: current ? loaded.state : { kind: "loading" },
    reload,
  };
}
