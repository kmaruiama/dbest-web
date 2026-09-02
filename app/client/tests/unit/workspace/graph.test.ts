import { beforeAll, describe, expect, it } from "vitest";
import { installCatalog } from "../../../src/server/catalog";
import {
  blockedReason,
  freePorts,
  legalLinks,
  linkTargets,
  parentsOf,
  reaches,
  resolveLink,
} from "../../../src/workspace/graph";
import type {
  Catalog,
  Edge,
  PlanNode,
  Port,
  Session,
} from "../../../src/server/types";

const CATALOG: Catalog = {
  categories: [],
  types: {
    filter: { arity: "unary", editable: true },
    agg: { arity: "unary", editable: true },
    join: { arity: "binary", editable: true },
  },
  operators: [],
};

beforeAll(() => installCatalog(CATALOG));

const edge = (from: number, to: number, port: Port): Edge => ({
  from,
  to,
  port,
});

function session(kinds: Record<number, string>, edges: Edge[]): Session {
  const nodes = new Map<number, PlanNode>();
  for (const [id, kind] of Object.entries(kinds)) {
    nodes.set(Number(id), { kind, fields: {} });
  }
  return { tables: new Map(), nodes, edges, layout: new Map() };
}

const render = (links: readonly Edge[]) =>
  links.map((link) => `${link.from}->${link.to}:${link.port}`).join(" ");

const chain = session({ 1: "filter", 2: "agg", 3: "filter" }, [
  edge(1, 2, "ONLY"),
  edge(2, 3, "ONLY"),
]);

describe("resolveLink", () => {
  const scenarios: {
    name: string;
    graph: Session;
    first: number;
    second: number;
    outcome: string;
  }[] = [
    {
      name: "click order picks the direction, no prompt",
      graph: session({ 1: "filter", 2: "agg" }, []),
      first: 1,
      second: 2,
      outcome: "one 1->2:ONLY",
    },
    {
      name: "reversing the click reverses the edge",
      graph: session({ 1: "filter", 2: "agg" }, []),
      first: 2,
      second: 1,
      outcome: "one 2->1:ONLY",
    },
    {
      name: "a source can only be the parent",
      graph: session({ 1: "table", 2: "filter" }, []),
      first: 2,
      second: 1,
      outcome: "one 1->2:ONLY",
    },
    {
      name: "a free binary target asks which side",
      graph: session({ 1: "filter", 2: "join" }, []),
      first: 1,
      second: 2,
      outcome: "many 1->2:LEFT 1->2:RIGHT",
    },
    {
      name: "a half-full binary target needs no prompt",
      graph: session({ 1: "filter", 2: "join" }, [edge(1, 2, "LEFT")]),
      first: 1,
      second: 2,
      outcome: "one 1->2:RIGHT",
    },
    {
      name: "a node cannot feed itself",
      graph: session({ 1: "filter" }, []),
      first: 1,
      second: 1,
      outcome: "none link.sameNode",
    },
    {
      name: "a link that closes a cycle is refused",
      graph: chain,
      first: 3,
      second: 1,
      outcome: "none link.cycle",
    },
    {
      name: "two saturated nodes cannot link",
      graph: session({ 1: "filter", 2: "agg", 3: "filter", 4: "agg" }, [
        edge(1, 2, "ONLY"),
        edge(3, 4, "ONLY"),
      ]),
      first: 2,
      second: 4,
      outcome: "none link.bothFull",
    },
  ];
  it.each(scenarios)("$name", ({ graph, first, second, outcome }) => {
    const result = resolveLink(graph, first, second);
    const shown =
      result.kind === "one"
        ? `one ${render([result.link])}`
        : result.kind === "many"
          ? `many ${render(result.options)}`
          : `none ${result.reason}`;
    expect(shown).toBe(outcome);
  });
});

describe("freePorts", () => {
  const scenarios: {
    name: string;
    graph: Session;
    node: number;
    free: Port[];
  }[] = [
    {
      name: "a source takes nothing",
      graph: session({ 1: "table" }, []),
      node: 1,
      free: [],
    },
    {
      name: "an unconnected unary node has its one port",
      graph: session({ 1: "filter" }, []),
      node: 1,
      free: ["ONLY"],
    },
    {
      name: "a connected unary node has none",
      graph: session({ 1: "filter", 2: "agg" }, [edge(1, 2, "ONLY")]),
      node: 2,
      free: [],
    },
    {
      name: "a binary node with a left edge keeps its right",
      graph: session({ 1: "filter", 2: "join" }, [edge(1, 2, "LEFT")]),
      node: 2,
      free: ["RIGHT"],
    },
    {
      name: "an unknown node has nothing",
      graph: session({ 1: "filter" }, []),
      node: 9,
      free: [],
    },
  ];
  it.each(scenarios)("$name", ({ graph, node, free }) => {
    expect(freePorts(graph, node)).toEqual(free);
  });
});

describe("reaches", () => {
  const scenarios: {
    name: string;
    from: number;
    to: number;
    reaches: boolean;
  }[] = [
    { name: "a node reaches its descendant", from: 1, to: 3, reaches: true },
    { name: "reachability is directed", from: 3, to: 1, reaches: false },
    { name: "a node does not reach itself", from: 2, to: 2, reaches: false },
  ];
  it.each(scenarios)("$name", ({ from, to, reaches: expected }) => {
    expect(reaches(chain, from, to)).toBe(expected);
  });
});

describe("parentsOf", () => {
  const diamond = session({ 1: "filter", 2: "filter", 3: "join" }, [
    edge(1, 3, "LEFT"),
    edge(2, 3, "RIGHT"),
  ]);
  const scenarios: { name: string; node: number; parents: number[] }[] = [
    { name: "lists every direct input", node: 3, parents: [1, 2] },
    { name: "a root node has none", node: 1, parents: [] },
  ];
  it.each(scenarios)("$name", ({ node, parents }) => {
    expect(parentsOf(diamond, node)).toEqual(parents);
  });
});

describe("legalLinks", () => {
  it("keeps both directions before click order decides", () => {
    const loose = session({ 1: "filter", 2: "agg" }, []);
    expect(render(legalLinks(loose, 1, 2))).toBe("1->2:ONLY 2->1:ONLY");
  });
});

describe("linkTargets", () => {
  it("offers only nodes a fresh edge can reach", () => {
    const graph = session({ 1: "table", 2: "filter", 3: "agg" }, [
      edge(1, 2, "ONLY"),
    ]);
    expect([...linkTargets(graph, 1)].sort()).toEqual([3]);
  });
});

describe("blockedReason", () => {
  const scenarios: {
    name: string;
    graph: Session;
    first: number;
    second: number;
    reason: string;
  }[] = [
    {
      name: "a node cannot feed itself",
      graph: chain,
      first: 1,
      second: 1,
      reason: "link.sameNode",
    },
    {
      name: "both nodes saturated",
      graph: session({ 1: "filter", 2: "agg", 3: "filter", 4: "agg" }, [
        edge(1, 2, "ONLY"),
        edge(3, 4, "ONLY"),
      ]),
      first: 2,
      second: 4,
      reason: "link.bothFull",
    },
    {
      name: "anything else reads as a cycle",
      graph: chain,
      first: 3,
      second: 1,
      reason: "link.cycle",
    },
  ];
  it.each(scenarios)("$name", ({ graph, first, second, reason }) => {
    expect(blockedReason(graph, first, second)).toBe(reason);
  });
});
