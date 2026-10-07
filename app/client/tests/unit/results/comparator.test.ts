import { describe, expect, it } from "vitest";
import { initial, metricRows, step } from "../../../src/results/comparator";

describe("comparator step", () => {
  it("measures nothing until asked", () => {
    expect(initial.state.measures).toEqual({ kind: "notMeasured" });
    expect(initial.effects).toEqual([]);
  });

  it("each click measures again, even with the same limit", () => {
    const once = step(initial.state, { kind: "measureClicked", limit: 5 });
    const twice = step(once.state, { kind: "measureClicked", limit: 5 });
    expect(once.state.measures).toEqual({ kind: "loading" });
    expect(once.effects).toEqual([{ kind: "measure", asked: 1, limit: 5 }]);
    expect(twice.effects).toEqual([{ kind: "measure", asked: 2, limit: 5 }]);
  });

  it("only the answer to the latest click is shown", () => {
    const first = step(initial.state, { kind: "measureClicked", limit: 5 });
    const second = step(first.state, { kind: "measureClicked", limit: null });
    const stale = step(second.state, {
      kind: "measured",
      asked: 1,
      measures: [],
    }).state;
    expect(stale.measures).toEqual({ kind: "loading" });
    const fresh = step(stale, { kind: "measured", asked: 2, measures: [] });
    expect(fresh.state.measures).toEqual({ kind: "ready", value: [] });
  });
});

describe("metricRows", () => {
  const measures = [
    {
      tuples: 10,
      exhausted: false,
      elapsedMs: 1.25,
      counters: [
        { name: "memoryBytes", value: 2048 },
        { name: "nextCalls", value: 12 },
      ],
    },
    {
      tuples: 8,
      exhausted: true,
      elapsedMs: 2.5,
      counters: [
        { name: "memoryBytes", value: 1024 },
        { name: "nextCalls", value: 9 },
      ],
    },
  ];

  it("keeps side order and marks partial tuple counts without ranking them", () => {
    expect(metricRows(measures)[0]).toEqual({
      key: "metric.tuples",
      unit: "count",
      values: [10, 8],
      partial: [true, false],
      ranked: false,
    });
  });

  it("keeps raw measurements and server counter order for the view to format", () => {
    const complete = [false, false];
    expect(metricRows(measures).slice(1)).toEqual([
      {
        key: "metric.elapsed",
        unit: "ms",
        values: [1.25, 2.5],
        partial: complete,
        ranked: true,
      },
      {
        key: "metric.memoryBytes",
        unit: "bytes",
        values: [2048, 1024],
        partial: complete,
        ranked: true,
      },
      {
        key: "metric.nextCalls",
        unit: "count",
        values: [12, 9],
        partial: complete,
        ranked: true,
      },
    ]);
  });
});
