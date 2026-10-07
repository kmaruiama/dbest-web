import { describe, expect, it } from "vitest";
import type {
  Catalog,
  Command,
  Session,
  SessionView,
} from "../../../src/server/types";
import {
  initial,
  step,
  type Effect,
  type Event,
  type State,
} from "../../../src/workspace/workspace";

const CATALOG: Catalog = {
  categories: ["Algebra"],
  types: {
    filter: { arity: "unary", editable: true, fields: [], variants: [] },
    limit: { arity: "unary", editable: false, fields: [], variants: [] },
    join: { arity: "binary", editable: true, fields: [], variants: [] },
  },
  operators: [
    {
      key: "filter",
      type: "filter",
      symbol: "σ",
      category: "Algebra",
      template: { "@type": "filter" },
    },
    {
      key: "aggregation",
      type: "agg",
      symbol: "∑",
      category: "Algebra",
      template: { "@type": "agg", alias: "agg" },
    },
  ],
  placeholder: "?",
};

const session = (
  nodes: Session["nodes"],
  edges: Session["edges"] = [],
): Session => ({
  tables: { 1: { "@type": "memory", name: "emp", columns: [], rows: [] } },
  nodes,
  edges,
  layout: Object.fromEntries(
    Object.keys(nodes).map((id) => [id, { x: 10, y: 10 }]),
  ),
});

const view = (s: Session): SessionView => ({
  session: s,
  captions: {},
  undoDepth: 0,
  redoDepth: 0,
});

const ready = (s: Session): State =>
  step(initial("s1", CATALOG).state, {
    kind: "loaded",
    view: view(s),
    problems: [],
  }).state;

const run = (state: State, events: Event[]) => {
  let current = state;
  const effects: Effect[] = [];
  for (const event of events) {
    const next = step(current, event);
    current = next.state;
    effects.push(...next.effects);
  }
  return { state: current, effects };
};

const sentCommand = (effects: Effect[]): Command | undefined => {
  const sent = effects.find((effect) => effect.kind === "send");
  return sent?.kind === "send" && sent.write.kind === "command"
    ? sent.write.command
    : undefined;
};

const table = { "@type": "table", table: 1, alias: "emp" };

describe("workspace step", () => {
  it("starts by loading the session", () => {
    expect(initial("s1", CATALOG).effects).toEqual([{ kind: "load" }]);
  });

  it("a click starts a link, and a click on a free target sends the edge", () => {
    const start = ready(session({ 1: table, 2: { "@type": "filter" } }));
    const { state, effects } = run(start, [
      { kind: "nodeClicked", id: 1 },
      { kind: "nodeClicked", id: 2 },
    ]);
    expect(state.interaction).toEqual({ kind: "idle" });
    expect(sentCommand(effects)).toEqual({
      "@type": "connect",
      edge: { from: 1, to: 2, port: "ONLY" },
    });
  });

  it("linking a node to itself says why, and sends nothing", () => {
    const start = ready(session({ 1: { "@type": "filter" } }));
    const { state, effects } = run(start, [
      { kind: "nodeClicked", id: 1 },
      { kind: "nodeClicked", id: 1 },
    ]);
    expect(state.notice).toEqual({ key: "link.sameNode" });
    expect(effects).toEqual([]);
  });

  it("a binary target asks which input to use", () => {
    const start = ready(session({ 1: table, 2: { "@type": "join" } }));
    const { state } = run(start, [
      { kind: "nodeClicked", id: 1 },
      { kind: "nodeClicked", id: 2 },
    ]);
    expect(state.interaction.kind).toBe("choosing");
  });

  it("right-click selects one node, and with a modifier toggles it", () => {
    const start = ready(session({ 1: table, 2: { "@type": "filter" } }));
    const { state } = run(start, [
      { kind: "nodeRightClicked", id: 1, additive: false },
      { kind: "nodeRightClicked", id: 2, additive: true },
      { kind: "nodeRightClicked", id: 1, additive: true },
    ]);
    expect(state.selection).toEqual([2]);
  });

  it("paste adds offset copies with new ids and selects them once they land", () => {
    const start = ready(
      session({ 1: table, 2: { "@type": "filter" } }, [
        { from: 1, to: 2, port: "ONLY" },
      ]),
    );
    const { state, effects } = run(start, [
      { kind: "nodeRightClicked", id: 1, additive: false },
      { kind: "nodeRightClicked", id: 2, additive: true },
      { kind: "copyPressed" },
      { kind: "pastePressed" },
    ]);
    const batch = sentCommand(effects);
    expect(batch).toEqual({
      "@type": "batch",
      commands: [
        {
          "@type": "addNode",
          id: 3,
          node: table,
          at: { x: 40, y: 40 },
        },
        {
          "@type": "addNode",
          id: 4,
          node: { "@type": "filter" },
          at: { x: 40, y: 40 },
        },
        { "@type": "connect", edge: { from: 3, to: 4, port: "ONLY" } },
      ],
    });
    const send = effects.find((effect) => effect.kind === "send");
    if (send?.kind !== "send") throw new Error("nothing sent");
    const landed = step(state, {
      kind: "committed",
      seq: send.seq,
      loaded: {
        view: view(
          session({
            1: table,
            2: { "@type": "filter" },
            3: table,
            4: { "@type": "filter" },
          }),
        ),
        problems: [],
      },
      done: send.done,
    });
    expect(landed.state.selection).toEqual([3, 4]);
    expect(landed.state.pending).toBe(0);
  });

  it("an older answer never replaces a newer view", () => {
    const start = ready(session({}));
    const { state, effects } = run(start, [
      { kind: "undoClicked" },
      { kind: "redoClicked" },
    ]);
    const [first, second] = effects;
    if (first.kind !== "send" || second.kind !== "send")
      throw new Error("expected two sends");
    const newer = session({ 9: { "@type": "filter" } });
    const answered = run(state, [
      {
        kind: "committed",
        seq: second.seq,
        loaded: { view: view(newer), problems: [] },
        done: second.done,
      },
      {
        kind: "committed",
        seq: first.seq,
        loaded: { view: view(session({})), problems: [] },
        done: first.done,
      },
    ]).state;
    expect(
      answered.load.kind === "ready" && answered.load.value.view.session,
    ).toEqual(newer);
    expect(answered.pending).toBe(0);
  });

  it("a drop where the node already is sends nothing", () => {
    const start = ready(session({ 1: table }));
    const { effects } = run(start, [
      { kind: "dragEnded", positions: { 1: { x: 10, y: 10 } } },
    ]);
    expect(effects).toEqual([]);
  });

  it("a moved node is drawn where it was dropped until the layout says so", () => {
    const start = ready(session({ 1: table }));
    const moved = run(start, [
      { kind: "dragEnded", positions: { 1: { x: 50, y: 60 } } },
    ]);
    expect(sentCommand(moved.effects)).toEqual({
      "@type": "batch",
      commands: [{ "@type": "move", id: 1, to: { x: 50, y: 60 } }],
    });
    expect(moved.state.drag).toEqual({ 1: { x: 50, y: 60 } });
    const send = moved.effects[0];
    if (send.kind !== "send") throw new Error("nothing sent");
    const saved = session({ 1: table });
    saved.layout[1] = { x: 50, y: 60 };
    const landed = step(moved.state, {
      kind: "committed",
      seq: send.seq,
      loaded: { view: view(saved), problems: [] },
      done: send.done,
    });
    expect(landed.state.drag).toEqual({});
  });

  it("a third mark and a mark in a marked tree are refused", () => {
    const start = ready(
      session(
        {
          1: table,
          2: { "@type": "filter" },
          3: { "@type": "filter" },
          4: { "@type": "filter" },
        },
        [{ from: 1, to: 2, port: "ONLY" }],
      ),
    );
    const sameTree = run(start, [
      { kind: "toolClicked", tool: "mark", id: 1 },
      { kind: "toolClicked", tool: "mark", id: 2 },
    ]).state;
    expect(sameTree.notice).toEqual({ key: "mark.sameTree" });
    const full = run(start, [
      { kind: "toolClicked", tool: "mark", id: 1 },
      { kind: "toolClicked", tool: "mark", id: 3 },
      { kind: "toolClicked", tool: "mark", id: 4 },
    ]).state;
    expect(full.marks).toEqual([1, 3]);
    expect(full.notice).toEqual({ key: "mark.full" });
  });

  it("a refused edit stays in the form; anything else is a notice", () => {
    const start = ready(session({ 1: { "@type": "filter" } }));
    const editing = step(start, {
      kind: "columnsLoaded",
      id: 1,
      node: { "@type": "filter" },
      columns: { inputs: {}, outer: [] },
    }).state;
    const refused = run(editing, [
      { kind: "formSubmitted", id: 1, node: { "@type": "filter" } },
      { kind: "commandFailed", message: "bad column" },
    ]).state;
    expect(refused.interaction).toMatchObject({
      kind: "editing",
      error: "bad column",
    });
    expect(refused.notice).toBeNull();
    const idle = step(start, { kind: "commandFailed", message: "busy" }).state;
    expect(idle.notice).toEqual({ message: "busy" });
  });

  it("a dropped chip gets a new id, never one a deleted node had", () => {
    const start = { ...ready(session({ 1: table })), lastNodeId: 5 };
    const { effects } = run(start, [
      { kind: "chipDropped", key: "aggregation", at: { x: 1, y: 2 } },
    ]);
    expect(sentCommand(effects)).toEqual({
      "@type": "addNode",
      id: 6,
      node: { "@type": "agg", alias: "agg" },
      at: { x: 1, y: 2 },
    });
  });

  it("delete with nothing selected sends nothing", () => {
    const start = ready(session({ 1: table }));
    expect(step(start, { kind: "deletePressed" }).effects).toEqual([]);
  });
});
