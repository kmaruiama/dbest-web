import { describe, expect, it } from "vitest";
import {
  edgeEvents,
  nodeEvents,
  projectEdges,
  projectNodes,
  type BoxNode,
  type Look,
} from "../../../src/canvas/canvas";
import type { Catalog, NodeId, Port, Session } from "../../../src/server/types";

const CATALOG: Catalog = {
  categories: [],
  types: {
    filter: { arity: "unary", editable: true, fields: [], variants: [] },
    join: {
      arity: "binary",
      editable: true,
      fields: [],
      variants: ["algorithm"],
    },
  },
  operators: [
    {
      key: "filter",
      type: "filter",
      symbol: "σ",
      category: "",
      template: { "@type": "filter" },
    },
    {
      key: "join",
      type: "join",
      symbol: "⋈",
      category: "",
      template: { "@type": "join", algorithm: "HASH" },
    },
  ],
  placeholder: "?",
};

const baseView: Look = {
  captions: { 1: { engineClass: "Filter", expression: "a > 1", alias: null } },
  problems: [],
  selection: [],
  edgeSelection: [],
  linkingFrom: null,
  marks: [],
  drag: {},
  sizes: {},
  showEngineClass: true,
  showExpression: true,
  translate: (key) => `T:${key}`,
};

const session = (
  nodes: Session["nodes"],
  layout: Session["layout"] = {},
  edges: Session["edges"] = [],
): Session => ({ tables: {}, nodes, edges, layout });

const filterAt = (id: NodeId, x: number, y: number): Session =>
  session({ [id]: { "@type": "filter" } }, { [id]: { x, y } });

const badJoin: Session = session(
  { 1: { "@type": "join", algorithm: "BOGUS" } },
  { 1: { x: 0, y: 0 } },
);

const project = (s: Session, view: Partial<Look> = {}) =>
  projectNodes(CATALOG, s, { ...baseView, ...view });

const faceOf = (box: BoxNode) => ({
  id: box.id,
  position: box.position,
  symbol: box.data.symbol,
  engineClass: box.data.engineClass,
  expression: box.data.expression,
  ports: box.data.ports,
  editable: box.data.editable,
  alias: box.data.alias,
  unknownVariant: box.data.unknownVariant,
  problem: box.data.problem,
  dimmed: box.data.dimmed,
});

const FILTER_FACE = {
  id: "1",
  position: { x: 0, y: 0 },
  symbol: "σ",
  engineClass: "Filter" as string | null,
  expression: "a > 1",
  ports: ["ONLY"] as Port[],
  editable: true,
  alias: null as string | null,
  unknownVariant: false,
  problem: null as string | null,
  dimmed: false,
};

describe("projectNodes", () => {
  it("carries the caption, glyph and position onto the box", () => {
    expect(faceOf(project(filterAt(1, 10, 20))[0])).toEqual({
      ...FILTER_FACE,
      position: { x: 10, y: 20 },
    });
  });

  const scenarios: {
    name: string;
    graph: Session;
    view: Partial<Look>;
    face: typeof FILTER_FACE;
  }[] = [
    {
      name: "hides the engine class when its toggle is off",
      graph: filterAt(1, 0, 0),
      view: { showEngineClass: false },
      face: { ...FILTER_FACE, engineClass: null },
    },
    {
      name: "blanks the expression when its toggle is off",
      graph: filterAt(1, 0, 0),
      view: { showExpression: false },
      face: { ...FILTER_FACE, expression: "" },
    },
    {
      name: "offers a table's alias to rename and keeps the table out of the form",
      graph: session(
        { 1: { "@type": "table", table: 1, alias: "e" } },
        { 1: { x: 0, y: 0 } },
      ),
      view: {
        captions: {
          1: { engineClass: "IndexScan", expression: "emp", alias: "e" },
        },
      },
      face: {
        ...FILTER_FACE,
        symbol: "▤",
        engineClass: "IndexScan",
        expression: "emp",
        ports: [],
        editable: false,
        alias: "e",
      },
    },
    {
      name: "attaches a matching problem message",
      graph: filterAt(1, 0, 0),
      view: { problems: [{ node: 1, message: "missing input ONLY" }] },
      face: { ...FILTER_FACE, problem: "missing input ONLY" },
    },
    {
      name: "flags an unresolvable variant and labels it via translate",
      graph: badJoin,
      view: {},
      face: {
        ...FILTER_FACE,
        symbol: "?",
        ports: ["LEFT", "RIGHT"],
        unknownVariant: true,
        problem: "T:unknownVariant",
      },
    },
  ];
  it.each(scenarios)("$name", ({ graph, view, face }) => {
    expect(faceOf(project(graph, view)[0])).toEqual(face);
  });

  it("dims every node that is not a link target, but never the source", () => {
    const s = session(
      {
        1: { "@type": "filter" },
        2: { "@type": "filter" },
        3: { "@type": "filter" },
      },
      { 1: { x: 0, y: 0 }, 2: { x: 0, y: 0 }, 3: { x: 0, y: 0 } },
      [{ from: 1, to: 2, port: "ONLY" }],
    );
    const dimmed = Object.fromEntries(
      project(s, { linkingFrom: 1 }).map((box) => [box.id, box.data.dimmed]),
    );
    expect(dimmed).toEqual({ "1": false, "2": true, "3": false });
  });

  it("letters the marked nodes A and B in the order they were marked", () => {
    const s = session(
      {
        1: { "@type": "filter" },
        2: { "@type": "filter" },
        3: { "@type": "filter" },
      },
      { 1: { x: 0, y: 0 }, 2: { x: 0, y: 0 }, 3: { x: 0, y: 0 } },
    );
    const marks = Object.fromEntries(
      project(s, { marks: [3, 1] }).map((box) => [box.id, box.data.mark]),
    );
    expect(marks).toEqual({ "1": "B", "2": null, "3": "A" });
  });

  it("draws a dragged node where it was dropped until the layout catches up", () => {
    const s = filterAt(1, 10, 20);
    expect(project(s)[0].position).toEqual({ x: 10, y: 20 });
    expect(project(s, { drag: { 1: { x: 99, y: 98 } } })[0].position).toEqual({
      x: 99,
      y: 98,
    });
  });

  it("takes selection and measured size from the look", () => {
    const s = session(
      { 1: { "@type": "filter" }, 2: { "@type": "filter" } },
      { 1: { x: 0, y: 0 }, 2: { x: 0, y: 0 } },
    );
    const one = project(s, {
      selection: [2, 7],
      sizes: { 2: { width: 40, height: 30 } },
    });
    expect(one.map((box) => box.selected)).toEqual([false, true]);
    expect(one[1].measured).toEqual({ width: 40, height: 30 });
    expect(one.every((box) => box.data.solo)).toBe(true);
    const both = project(s, { selection: [1, 2] });
    expect(both.some((box) => box.data.solo)).toBe(false);
  });
});

describe("projectEdges", () => {
  it("maps each edge to a react-flow edge with a stable id", () => {
    const s = session({}, {}, [{ from: 1, to: 2, port: "LEFT" }]);
    expect(projectEdges(s, ["1-2-LEFT"])).toEqual([
      {
        id: "1-2-LEFT",
        source: "1",
        target: "2",
        sourceHandle: "out",
        targetHandle: "in",
        selected: true,
        data: { port: "LEFT" },
      },
    ]);
  });
});

describe("nodeEvents", () => {
  it("turns React Flow's changes into selection, drag and size events", () => {
    const changes = [
      { type: "select" as const, id: "1", selected: true },
      { type: "select" as const, id: "2", selected: false },
      {
        type: "position" as const,
        id: "1",
        position: { x: 5, y: 6 },
        dragging: true,
      },
      {
        type: "dimensions" as const,
        id: "2",
        dimensions: { width: 10, height: 11 },
      },
    ];
    expect(nodeEvents(changes)).toEqual([
      {
        kind: "nodesSelected",
        changes: [
          { id: 1, selected: true },
          { id: 2, selected: false },
        ],
      },
      { kind: "nodesDragged", positions: { 1: { x: 5, y: 6 } } },
      { kind: "nodesMeasured", sizes: { 2: { width: 10, height: 11 } } },
    ]);
  });

  it("ignores changes it has no event for", () => {
    expect(nodeEvents([{ type: "remove", id: "1" }])).toEqual([]);
    expect(edgeEvents([{ type: "remove", id: "1-2-ONLY" }])).toEqual([]);
  });
});
