import { useCallback, useEffect, useReducer, useRef } from "react";

export type Stepped<State, Effect> = { state: State; effects: Effect[] };

export type Dispatch<Event> = (event: Event) => void;

type Run<Event, Effect> = (
  effect: Effect,
  dispatch: Dispatch<Event>,
  signal: AbortSignal,
) => void;

type Queued<Effect> = { seq: number; effect: Effect };

type Held<State, Effect> = {
  state: State;
  queue: Queued<Effect>[];
  seq: number;
};

type Internal<Event> =
  { kind: "event"; event: Event } | { kind: "taken"; upTo: number };

export function useStep<State, Event, Effect>(
  step: (state: State, event: Event) => Stepped<State, Effect>,
  initial: Stepped<State, Effect>,
  run: Run<Event, Effect>,
): [State, Dispatch<Event>] {
  const [held, send] = useReducer(
    (current: Held<State, Effect>, action: Internal<Event>) =>
      action.kind === "taken"
        ? {
            ...current,
            queue: current.queue.filter((queued) => queued.seq > action.upTo),
          }
        : advance(current, step(current.state, action.event)),
    initial,
    (first) => advance({ state: first.state, queue: [], seq: 0 }, first),
  );
  const dispatch = useCallback(
    (event: Event) => send({ kind: "event", event }),
    [],
  );
  const latestRun = useRef(run);
  useEffect(() => {
    latestRun.current = run;
  }, [run]);
  const lifetime = useRef<AbortController | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    lifetime.current = controller;
    return () => controller.abort();
  }, []);
  useEffect(() => {
    const signal = lifetime.current?.signal;
    if (held.queue.length === 0 || signal === undefined) return;
    send({ kind: "taken", upTo: held.queue[held.queue.length - 1].seq });
    const answer = (event: Event) => {
      if (!signal.aborted) dispatch(event);
    };
    for (const queued of held.queue)
      latestRun.current(queued.effect, answer, signal);
  }, [held.queue, dispatch]);
  return [held.state, dispatch];
}

function advance<State, Effect>(
  held: Held<State, Effect>,
  next: Stepped<State, Effect>,
): Held<State, Effect> {
  const queued = next.effects.map((effect, index) => ({
    seq: held.seq + index + 1,
    effect,
  }));
  return {
    state: next.state,
    queue: [...held.queue, ...queued],
    seq: held.seq + queued.length,
  };
}
