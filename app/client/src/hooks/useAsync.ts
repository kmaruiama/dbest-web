import { useCallback, useEffect, useState, type DependencyList } from "react";
import { messageOf } from "../server/api";

export type Async<T> =
  | {
      kind: "loading";
    }
  | {
      kind: "failed";
      message: string;
    }
  | {
      kind: "ready";
      value: T;
    };

type UseAsync<T> = {
  state: Async<T>;
  reload: () => void;
  patch: (next: (value: T) => T) => void;
};

export function useAsync<T>(
  load: (signal: AbortSignal) => Promise<T>,
  deps: DependencyList,
): UseAsync<T> {
  const [state, setState] = useState<Async<T>>({ kind: "loading" });
  const [nonce, setNonce] = useState(0);
  const reload = useCallback(() => setNonce((n) => n + 1), []);
  const patch = useCallback((next: (value: T) => T) => {
    setState((current) =>
      current.kind === "ready"
        ? { kind: "ready", value: next(current.value) }
        : current,
    );
  }, []);
  useEffect(() => {
    let live = true;
    const controller = new AbortController();
    setState({ kind: "loading" });
    load(controller.signal).then(
      (value) => {
        if (live) setState({ kind: "ready", value });
      },
      (caught: unknown) => {
        if (live && !controller.signal.aborted)
          setState({ kind: "failed", message: messageOf(caught) });
      },
    );
    return () => {
      live = false;
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load, nonce, ...deps]);
  return { state, reload, patch };
}
