import { describe, expect, it } from "vitest";
import { mint } from "../../../src/workspace/ids";

describe("mint", () => {
  const scenarios: {
    name: string;
    start: number;
    calls: number[][];
    results: number[];
  }[] = [
    {
      name: "returns one past the highest id taken",
      start: 0,
      calls: [[1, 2]],
      results: [3],
    },
    {
      name: "an empty session starts at 1",
      start: 0,
      calls: [[]],
      results: [1],
    },
    {
      name: "keeps advancing when the taken set has not grown",
      start: 0,
      calls: [
        [1, 2],
        [1, 2],
      ],
      results: [3, 4],
    },
    {
      name: "never rewinds when a later refresh reports fewer ids",
      start: 0,
      calls: [
        [1, 2],
        [1, 2],
        [1, 2, 3],
      ],
      results: [3, 4, 5],
    },
  ];
  it.each(scenarios)("$name", ({ start, calls, results }) => {
    const watermark = { current: start };
    expect(calls.map((taken) => mint(taken.values(), watermark))).toEqual(
      results,
    );
  });
});
