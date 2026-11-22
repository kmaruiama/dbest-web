import { useEffect, useRef, useState } from "react";

export type Stepped<State, Effect> = { state: State; effects: Effect[] };
export type Dispatch<Event> = (event: Event) => void;

export function useStep<State, Event, Effect>(
  step: (state: State, event: Event) => Stepped<State, Effect>,
  initial: Stepped<State, Effect>,
  run: (effect: Effect, dispatch: Dispatch<Event>, signal: AbortSignal) => void,
): [State, Dispatch<Event>] {
  const [state, setState] = useState(initial.state);
  const [controller] = useState(() => new AbortController());
  const current = useRef(initial.state);
  const firstEffects = useRef(initial.effects);
  const abortTimer = useRef(0);
  const latest = useRef({ step, run });
  useEffect(() => {
    latest.current = { step, run };
  });

  const [dispatch] = useState(
    () =>
      function dispatch(event: Event) {
        if (controller.signal.aborted) return;
        const next = latest.current.step(current.current, event);
        current.current = next.state;
        setState(next.state);
        for (const effect of next.effects)
          latest.current.run(effect, dispatch, controller.signal);
      },
  );

  useEffect(() => {
    window.clearTimeout(abortTimer.current);
    for (const effect of firstEffects.current)
      latest.current.run(effect, dispatch, controller.signal);
    firstEffects.current = [];
    return () => {
      abortTimer.current = window.setTimeout(() => controller.abort(), 0);
    };
  }, [controller, dispatch]);

  return [state, dispatch];
}
