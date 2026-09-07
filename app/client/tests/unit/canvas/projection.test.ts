import { beforeAll, describe, expect, it } from "vitest";
import {
  projectEdges,
  projectNodes,
  type BoxNode,
  type FlowView,
  type NodeActions,
  type Since,
} from "../../../src/canvas/projection";
import { installCatalog } from "../../../src/server/catalog";
import type { Catalog, NodeId, Port, Session } from "../../../src/server/types";

const CATALOG: Catalog = {
  categories: [],
  types: {
    filter: { arity: "unary", editable: true },
    join: { arity: "binary", editable: true, variants: ["algorithm"] },
  },
  operators: [
    {
      key: "filter",
      type: "filter",
      symbol: "σ",
      category: "",
      template: { kind: "filter", fields: {} },
    },
    {
      key: "join",
      type: "join",
      symbol: "⋈",
      category: "",
      template: { kind: "join", fields: { algorithm: "HASH" } },
    },
  ],
};

beforeAll(() => installCatalog(CATALOG));

const ACTIONS: NodeActions = {
  onStartLink: () => {},
  onEdit: () => {},
  onRun: () => {},
  onDelete: () => {},
};

const baseView: FlowView = {
  showEngineClass: true,
  showExpression: true,
  linkingFrom: null,
  captions: new Map([[1, { engineClass: "Filter", expression: "a > 1" }]]),
  problems: [],
  translate: (key) => `T:${key}`,
};

const session = (
  nodes: Session["nodes"],
  layout: Session["layout"] = new Map(),
  edges: Session["edges"] = [],
): Session => ({ tables: new Map(), nodes, edges, layout });

const filterAt = (id: NodeId, x: number, y: number): Session =>
  session(
    new Map([[id, { kind: "filter", fields: {} }]]),
    new Map([[id, { x, y }]]),
  );

const badJoin: Session = session(
  new Map([[1, { kind: "join", fields: { algorithm: "BOGUS" } }]]),
  new Map([[1, { x: 0, y: 0 }]]),
);

const project = (
  s: Session,
  view: Partial<FlowView> = {},
  previous: Map<string, BoxNode> = new Map(),
  since: Since = { layout: new Map() },
) => projectNodes(s, { ...baseView, ...view }, ACTIONS, previous, since);

const faceOf = (box: BoxNode) => ({
  id: box.id,
  position: box.position,
  symbol: box.data.symbol,
  engineClass: box.data.engineClass,
  expression: box.data.expression,
  ports: box.data.ports,
  editable: box.data.editable,
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
    view: Partial<FlowView>;
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
      new Map([
        [1, { kind: "filter", fields: {} }],
        [2, { kind: "filter", fields: {} }],
        [3, { kind: "filter", fields: {} }],
      ]),
      new Map([
        [1, { x: 0, y: 0 }],
        [2, { x: 0, y: 0 }],
        [3, { x: 0, y: 0 }],
      ]),
      [{ from: 1, to: 2, port: "ONLY" }],
    );
    const dimmed = Object.fromEntries(
      project(s, { linkingFrom: 1 }).map((box) => [box.id, box.data.dimmed]),
    );
    expect(dimmed).toEqual({ "1": false, "2": true, "3": false });
  });

  it("keeps an existing position until the stored layout changes", () => {
    const s = filterAt(1, 10, 20);
    const previous = new Map<string, BoxNode>([
      [
        "1",
        {
          id: "1",
          type: "box",
          position: { x: 999, y: 999 },
          data: {},
        } as BoxNode,
      ],
    ]);

    const kept = projectNodes(s, baseView, ACTIONS, previous, {
      layout: new Map([[1, { x: 10, y: 20 }]]),
    });
    expect(kept[0].position).toEqual({ x: 999, y: 999 });

    const moved = projectNodes(s, baseView, ACTIONS, previous, {
      layout: new Map([[1, { x: 0, y: 0 }]]),
    });
    expect(moved[0].position).toEqual({ x: 10, y: 20 });
  });
});

describe("projectEdges", () => {
  it("maps each edge to a react-flow edge with a stable id", () => {
    const s = session(new Map(), new Map(), [{ from: 1, to: 2, port: "LEFT" }]);
    expect(projectEdges(s)).toEqual([
      {
        id: "1-2-LEFT",
        source: "1",
        target: "2",
        sourceHandle: "out",
        targetHandle: "in",
        data: { port: "LEFT" },
      },
    ]);
  });
});
