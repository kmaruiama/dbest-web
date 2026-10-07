import { describe, expect, it } from "vitest";
import {
  blockedReason,
  freePorts,
  legalLinks,
  linkTargets,
  reaches,
  resolveLink,
  sameTree,
} from "../../../src/server/graph";
import type {
  Catalog,
  Edge,
  Node,
  Port,
  Session,
} from "../../../src/server/types";

const CATALOG: Catalog = {
  categories: [],
  types: {
    filter: { arity: "unary", editable: true, fields: [], variants: [] },
    agg: { arity: "unary", editable: true, fields: [], variants: [] },
    join: { arity: "binary", editable: true, fields: [], variants: [] },
  },
  operators: [],
  placeholder: "?",
};

const edge = (from: number, to: number, port: Port): Edge => ({
  from,
  to,
  port,
});

function session(kinds: Record<number, string>, edges: Edge[]): Session {
  const nodes: Record<number, Node> = {};
  for (const [id, kind] of Object.entries(kinds))
    nodes[Number(id)] = { "@type": kind };
  return { tables: {}, nodes, edges, layout: {} };
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
    const result = resolveLink(CATALOG, graph, first, second);
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
    expect(freePorts(CATALOG, graph, node)).toEqual(free);
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

describe("sameTree", () => {
  const forked = session({ 1: "filter", 2: "agg", 3: "filter", 4: "agg" }, [
    edge(1, 2, "ONLY"),
    edge(1, 3, "ONLY"),
  ]);
  const scenarios: {
    name: string;
    graph: Session;
    first: number;
    second: number;
    same: boolean;
  }[] = [
    {
      name: "a node shares its own tree",
      graph: chain,
      first: 2,
      second: 2,
      same: true,
    },
    {
      name: "an input and its consumer share one",
      graph: chain,
      first: 3,
      second: 1,
      same: true,
    },
    {
      name: "two consumers of one node share one",
      graph: forked,
      first: 2,
      second: 3,
      same: true,
    },
    {
      name: "unconnected nodes do not",
      graph: forked,
      first: 2,
      second: 4,
      same: false,
    },
  ];
  it.each(scenarios)("$name", ({ graph, first, second, same }) => {
    expect(sameTree(graph, first, second)).toBe(same);
  });
});

describe("legalLinks", () => {
  it("keeps both directions before click order decides", () => {
    const loose = session({ 1: "filter", 2: "agg" }, []);
    expect(render(legalLinks(CATALOG, loose, 1, 2))).toBe(
      "1->2:ONLY 2->1:ONLY",
    );
  });
});

describe("linkTargets", () => {
  it("offers only nodes a fresh edge can reach", () => {
    const graph = session({ 1: "table", 2: "filter", 3: "agg" }, [
      edge(1, 2, "ONLY"),
    ]);
    expect([...linkTargets(CATALOG, graph, 1)].sort()).toEqual([3]);
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
    expect(blockedReason(CATALOG, graph, first, second)).toBe(reason);
  });
});
