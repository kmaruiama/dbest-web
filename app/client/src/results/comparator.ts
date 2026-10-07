import type { Measure } from "../server/types";
import type { Async } from "../ui/useLoad";
import type { Stepped } from "../ui/useStep";

export const DEFAULT_LIMIT = 100;

export type Measures = { kind: "notMeasured" } | Async<Measure[]>;

export type State = {
  asked: number;
  measures: Measures;
};

export type Event =
  | { kind: "measureClicked"; limit: number | null }
  | { kind: "measured"; asked: number; measures: Measure[] }
  | { kind: "measureFailed"; asked: number; message: string };

export type Effect = { kind: "measure"; asked: number; limit: number | null };

export const initial: Stepped<State, Effect> = {
  state: { asked: 0, measures: { kind: "notMeasured" } },
  effects: [],
};

export function step(state: State, event: Event): Stepped<State, Effect> {
  switch (event.kind) {
    case "measureClicked": {
      const asked = state.asked + 1;
      return {
        state: { asked, measures: { kind: "loading" } },
        effects: [{ kind: "measure", asked, limit: event.limit }],
      };
    }
    case "measured":
      return only(
        event.asked === state.asked
          ? { ...state, measures: { kind: "ready", value: event.measures } }
          : state,
      );
    case "measureFailed":
      return only(
        event.asked === state.asked
          ? { ...state, measures: { kind: "failed", message: event.message } }
          : state,
      );
  }
}

function only(state: State): Stepped<State, Effect> {
  return { state, effects: [] };
}

export type MetricUnit = "count" | "ms" | "bytes";

export type MetricRow = {
  key: string;
  unit: MetricUnit;
  values: number[];
  partial: boolean[];
  ranked: boolean;
};

export function metricRows(measures: Measure[]): MetricRow[] {
  const complete = measures.map(() => false);
  const tuples: MetricRow = {
    key: "metric.tuples",
    unit: "count",
    values: measures.map((side) => side.tuples),
    partial: measures.map((side) => !side.exhausted),
    ranked: false,
  };
  const elapsed: MetricRow = {
    key: "metric.elapsed",
    unit: "ms",
    values: measures.map((side) => side.elapsedMs),
    partial: complete,
    ranked: true,
  };
  const counters = measures[0].counters.map((counter, index): MetricRow => ({
    key: `metric.${counter.name}`,
    unit: counter.name === "memoryBytes" ? "bytes" : "count",
    values: measures.map((side) => side.counters[index].value),
    partial: complete,
    ranked: true,
  }));
  return [tuples, elapsed, ...counters];
}
