import { describe, expect, it } from "vitest";
import type { Node, Session } from "../../../src/server/types";
import { freshAlias, mint } from "../../../src/server/ids";

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
    let last = start;
    const minted = calls.map((taken) => {
      last = mint(taken.values(), last);
      return last;
    });
    expect(minted).toEqual(results);
  });
});

describe("freshAlias", () => {
  const canvas = (nodes: Node[]): Session => ({
    tables: {},
    nodes: Object.fromEntries(nodes.map((node, index) => [index + 1, node])),
    edges: [],
    layout: {},
  });
  const scenarios: {
    name: string;
    nodes: Node[];
    base: string;
    alias: string;
  }[] = [
    { name: "keeps a name nobody uses", nodes: [], base: "emp", alias: "emp" },
    {
      name: "numbers a name a table already has",
      nodes: [{ "@type": "table", table: 1, alias: "emp" }],
      base: "emp",
      alias: "emp2",
    },
    {
      name: "skips every number taken",
      nodes: [
        { "@type": "condition", alias: "condition" },
        { "@type": "logicalOp", alias: "condition2" },
      ],
      base: "condition",
      alias: "condition3",
    },
    {
      name: "counts a rename's new name as taken",
      nodes: [{ "@type": "alias", from: "e", to: "agg" }],
      base: "agg",
      alias: "agg2",
    },
  ];
  it.each(scenarios)("$name", ({ nodes, base, alias }) => {
    expect(freshAlias(canvas(nodes), base)).toBe(alias);
  });
});
