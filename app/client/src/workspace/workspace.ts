import type { CanvasEvent, PaletteEvent, Size } from "../canvas/canvas";
import { chipByKey, isEditable, symbolOf } from "../server/catalog";
import {
  hasAllInputPortsConnected,
  resolveLink,
  sameTree,
} from "../server/graph";
import { freshAlias, mint } from "../server/ids";
import { aliasOf } from "../server/nodes";
import type {
  Catalog,
  Columns,
  Command,
  Edge,
  Node,
  NodeId,
  Position,
  Problem,
  Session,
  SessionView,
  TableId,
  TableSpec,
} from "../server/types";
import type { TableMode } from "../tables/tables";
import type { Async } from "../ui/useLoad";
import type { Stepped } from "../ui/useStep";

const PASTE_OFFSET = 30;

export type Loaded = { view: SessionView; problems: Problem[] };

type Interaction =
  | { kind: "idle" }
  | { kind: "linking"; from: NodeId }
  | { kind: "choosing"; options: Edge[] }
  | {
      kind: "editing";
      id: NodeId;
      node: Node;
      columns: Columns;
      error: string | null;
    }
  | { kind: "renamingAlias"; id: NodeId; current: string; error: string | null }
  | { kind: "running"; id: NodeId }
  | { kind: "comparing"; a: NodeId; b: NodeId }
  | { kind: "newTable"; mode: TableMode };

export type Notice = { key: string } | { message: string };

type Clipboard = {
  entries: { originalId: NodeId; node: Node; at: Position }[];
  edges: Edge[];
};

export type State = {
  sid: string;
  catalog: Catalog;
  load: Async<Loaded>;
  sent: number;
  applied: number;
  pending: number;
  interaction: Interaction;
  selection: NodeId[];
  edgeSelection: string[];
  marks: NodeId[];
  clipboard: Clipboard | null;
  lastNodeId: number;
  lastTableId: number;
  drag: Record<NodeId, Position>;
  sizes: Record<NodeId, Size>;
  notice: Notice | null;
};

export type Event =
  | CanvasEvent
  | PaletteEvent
  | { kind: "loaded"; view: SessionView; problems: Problem[] }
  | { kind: "loadFailed"; message: string }
  | { kind: "retryClicked" }
  | { kind: "committed"; seq: number; loaded: Loaded; done: Done }
  | { kind: "commandFailed"; message: string }
  | { kind: "columnsLoaded"; id: NodeId; node: Node; columns: Columns }
  | { kind: "columnsFailed"; message: string }
  | { kind: "escapePressed" }
  | { kind: "deletePressed" }
  | { kind: "copyPressed" }
  | { kind: "pastePressed" }
  | { kind: "undoClicked" }
  | { kind: "redoClicked" }
  | { kind: "compareClicked" }
  | { kind: "clearMarksClicked" }
  | { kind: "newTableClicked"; mode: TableMode }
  | { kind: "tableSubmitted"; spec: TableSpec }
  | { kind: "formSubmitted"; id: NodeId; node: Node }
  | { kind: "aliasConfirmed"; id: NodeId; alias: string }
  | { kind: "linkChosen"; edge: Edge }
  | { kind: "modalClosed" }
  | { kind: "noticeDismissed" };

export type Write =
  | { kind: "command"; command: Command }
  | { kind: "undo" }
  | { kind: "redo" }
  | { kind: "rename"; id: NodeId; alias: string };

type Done =
  | { kind: "nothing" }
  | { kind: "close" }
  | { kind: "selectPasted"; ids: NodeId[] }
  | { kind: "editIfComplete"; id: NodeId };

export type Effect =
  | { kind: "load" }
  | { kind: "send"; seq: number; write: Write; done: Done }
  | { kind: "loadColumns"; id: NodeId; node: Node };

export function initial(sid: string, catalog: Catalog): Stepped<State, Effect> {
  return {
    state: {
      sid,
      catalog,
      load: { kind: "loading" },
      sent: 0,
      applied: 0,
      pending: 0,
      interaction: { kind: "idle" },
      selection: [],
      edgeSelection: [],
      marks: [],
      clipboard: null,
      lastNodeId: 0,
      lastTableId: 0,
      drag: {},
      sizes: {},
      notice: null,
    },
    effects: [{ kind: "load" }],
  };
}

export function step(state: State, event: Event): Stepped<State, Effect> {
  switch (event.kind) {
    case "loaded":
      return only({
        ...state,
        load: {
          kind: "ready",
          value: { view: event.view, problems: event.problems },
        },
      });
    case "loadFailed":
      return only({
        ...state,
        load: { kind: "failed", message: event.message },
      });
    case "retryClicked":
      return {
        state: { ...state, load: { kind: "loading" } },
        effects: [{ kind: "load" }],
      };
    case "committed":
      return committed(state, event.seq, event.loaded, event.done);
    case "commandFailed":
      return only(
        failed(
          { ...state, pending: state.pending - 1, drag: {} },
          event.message,
        ),
      );
    case "columnsLoaded":
      return only({
        ...state,
        interaction: {
          kind: "editing",
          id: event.id,
          node: event.node,
          columns: event.columns,
          error: null,
        },
      });
    case "columnsFailed":
      return only(notify(state, { message: event.message }));
    case "nodeClicked":
      return state.interaction.kind === "linking"
        ? completeLink(state, state.interaction.from, event.id)
        : only(startLink(state, event.id));
    case "nodeRightClicked":
      return state.interaction.kind === "linking"
        ? completeLink(state, state.interaction.from, event.id)
        : only(nodeSelected(state, event.id, event.additive));
    case "paneClicked":
      return only(deselect(state));
    case "paneRightClicked":
      return only(
        state.interaction.kind === "linking" ? stop(state) : deselect(state),
      );
    case "nodesSelected":
      return only({
        ...state,
        selection: applySelection(state.selection, event.changes),
        notice: null,
      });
    case "edgesSelected":
      return only({
        ...state,
        edgeSelection: applySelection(state.edgeSelection, event.changes),
      });
    case "nodesDragged":
      return only({ ...state, drag: { ...state.drag, ...event.positions } });
    case "dragEnded":
      return moved(state, event.positions);
    case "nodesMeasured":
      return only({ ...state, sizes: { ...state.sizes, ...event.sizes } });
    case "chipDropped":
      return chipDropped(state, event.key, event.at);
    case "tableDropped":
      return scanAdded(state, event.table, event.at);
    case "toolClicked":
      return toolClicked(state, event.tool, event.id);
    case "paletteTableClicked":
      return scanAdded(state, event.table, { x: 60, y: 60 });
    case "paletteTableRemoved":
      return send(state, command({ "@type": "removeTable", id: event.table }));
    case "paletteMouseDown":
      return only(deselect(state));
    case "escapePressed":
      return only(deselect(state));
    case "deletePressed":
      return liveSelection(state).length === 0
        ? only(state)
        : selectionDeleted(state);
    case "copyPressed":
      return only(copied(state));
    case "pastePressed":
      return pasted(state);
    case "undoClicked":
      return send(state, { kind: "undo" });
    case "redoClicked":
      return send(state, { kind: "redo" });
    case "compareClicked": {
      const [a, b] = liveMarks(state);
      return marksApart(state)
        ? only({ ...state, interaction: { kind: "comparing", a, b } })
        : only(state);
    }
    case "clearMarksClicked":
      return only({ ...state, marks: [] });
    case "newTableClicked":
      return only({
        ...state,
        interaction: { kind: "newTable", mode: event.mode },
      });
    case "tableSubmitted":
      return tableSubmitted(state, event.spec);
    case "formSubmitted":
      return send(
        state,
        command({ "@type": "setNode", id: event.id, node: event.node }),
        { kind: "close" },
      );
    case "aliasConfirmed":
      return aliasConfirmed(state, event.id, event.alias);
    case "linkChosen":
      return send(
        stop(state),
        command({ "@type": "connect", edge: event.edge }),
        { kind: "editIfComplete", id: event.edge.to },
      );
    case "modalClosed":
      return only(stop(state));
    case "noticeDismissed":
      return only({ ...state, notice: null });
  }
}

export function session(state: State): Session {
  return loaded(state)?.view.session ?? EMPTY;
}

export function loaded(state: State): Loaded | null {
  return state.load.kind === "ready" ? state.load.value : null;
}

export function busy(state: State): boolean {
  return state.pending > 0;
}

export function liveMarks(state: State): NodeId[] {
  const nodes = session(state).nodes;
  return state.marks.filter((id) => id in nodes);
}

function liveSelection(state: State): NodeId[] {
  const nodes = session(state).nodes;
  return state.selection.filter((id) => id in nodes);
}

export function marksApart(state: State): boolean {
  const marks = liveMarks(state);
  return marks.length === 2 && !sameTree(session(state), marks[0], marks[1]);
}

export function titleOf(state: State, id: NodeId): string {
  const node = session(state).nodes[id];
  const alias = loaded(state)?.view.captions[id]?.alias ?? "";
  const symbol = node === undefined ? "" : symbolOf(state.catalog, node).symbol;
  return `${symbol} #${id}${alias.length > 0 ? ` as ${alias}` : ""}`;
}

const EMPTY: Session = { tables: {}, nodes: {}, edges: [], layout: {} };

function only(state: State): Stepped<State, Effect> {
  return { state, effects: [] };
}

function stop(state: State): State {
  return { ...state, interaction: { kind: "idle" }, notice: null };
}

function deselect(state: State): State {
  return stop({ ...state, selection: [] });
}

function notify(state: State, notice: Notice): State {
  return { ...state, interaction: { kind: "idle" }, notice };
}

function failed(state: State, message: string): State {
  const interaction = state.interaction;
  if (interaction.kind === "editing" || interaction.kind === "renamingAlias")
    return { ...state, interaction: { ...interaction, error: message } };
  return { ...state, notice: { message } };
}

function tableSubmitted(state: State, spec: TableSpec): Stepped<State, Effect> {
  const taken = Object.keys(session(state).tables).map(Number);
  const id = mint(taken, state.lastTableId);
  return send(
    { ...state, lastTableId: id },
    command({ "@type": "addTable", id, spec }),
    { kind: "close" },
  );
}

function aliasConfirmed(
  state: State,
  id: NodeId,
  alias: string,
): Stepped<State, Effect> {
  const current = loaded(state)?.view.captions[id]?.alias;
  if (alias === current) return only(stop(state));
  return send(state, { kind: "rename", id, alias }, { kind: "close" });
}

function command(value: Command): Write {
  return { kind: "command", command: value };
}

function send(
  state: State,
  write: Write,
  done: Done = { kind: "nothing" },
): Stepped<State, Effect> {
  const seq = state.sent + 1;
  return {
    state: { ...state, sent: seq, pending: state.pending + 1 },
    effects: [{ kind: "send", seq, write, done }],
  };
}

function committed(
  state: State,
  seq: number,
  answer: Loaded,
  done: Done,
): Stepped<State, Effect> {
  const fresh = seq > state.applied;
  const next: State = {
    ...state,
    pending: state.pending - 1,
    applied: fresh ? seq : state.applied,
    load: fresh ? { kind: "ready", value: answer } : state.load,
    drag: fresh ? settledDrag(state.drag, answer.view.session) : state.drag,
  };
  switch (done.kind) {
    case "nothing":
      return only(next);
    case "close":
      return only(stop(next));
    case "selectPasted":
      return only(
        done.ids.every((id) => id in session(next).nodes)
          ? { ...next, selection: done.ids }
          : next,
      );
    case "editIfComplete":
      return editIfComplete(next, done.id);
  }
}

function settledDrag(
  drag: Record<NodeId, Position>,
  latest: Session,
): Record<NodeId, Position> {
  const left: Record<NodeId, Position> = {};
  for (const [key, at] of Object.entries(drag)) {
    const id = Number(key);
    const saved = latest.layout[id];
    if (
      id in latest.nodes &&
      (saved === undefined || saved.x !== at.x || saved.y !== at.y)
    )
      left[id] = at;
  }
  return left;
}

function editIfComplete(state: State, id: NodeId): Stepped<State, Effect> {
  const latest = session(state);
  return hasAllInputPortsConnected(state.catalog, latest, id)
    ? editAsked(state, id)
    : only(state);
}

function editAsked(state: State, id: NodeId): Stepped<State, Effect> {
  const node = session(state).nodes[id];
  if (node === undefined || !isEditable(state.catalog, node["@type"]))
    return only(state);
  return { state, effects: [{ kind: "loadColumns", id, node }] };
}

function startLink(state: State, id: NodeId): State {
  return {
    ...state,
    selection: [],
    interaction: { kind: "linking", from: id },
  };
}

function completeLink(
  linking: State,
  from: NodeId,
  to: NodeId,
): Stepped<State, Effect> {
  const state = { ...linking, selection: [] };
  const resolution = resolveLink(state.catalog, session(state), from, to);
  if (resolution.kind === "none")
    return only(notify(state, { key: resolution.reason }));
  if (resolution.kind === "many")
    return only({
      ...state,
      interaction: { kind: "choosing", options: resolution.options },
    });
  return send(
    stop(state),
    command({ "@type": "connect", edge: resolution.link }),
    { kind: "editIfComplete", id: resolution.link.to },
  );
}

function nodeSelected(state: State, id: NodeId, additive: boolean): State {
  const selection = additive ? toggled(state.selection, id) : [id];
  return { ...state, selection, notice: null };
}

function toolClicked(
  state: State,
  tool: "edit" | "alias" | "link" | "mark" | "run" | "delete",
  id: NodeId,
): Stepped<State, Effect> {
  switch (tool) {
    case "edit":
      return editAsked(state, id);
    case "alias":
      return only({
        ...state,
        interaction: {
          kind: "renamingAlias",
          id,
          current: loaded(state)?.view.captions[id]?.alias ?? "",
          error: null,
        },
      });
    case "link":
      return only(startLink(state, id));
    case "mark":
      return only(markToggled(state, id));
    case "run":
      return only({ ...state, interaction: { kind: "running", id } });
    case "delete":
      return selectionDeleted(state);
  }
}

function markToggled(state: State, id: NodeId): State {
  const marks = liveMarks(state);
  if (marks.includes(id))
    return { ...state, marks: marks.filter((mark) => mark !== id) };
  if (marks.length === 2) return notify(state, { key: "mark.full" });
  if (marks.some((mark) => sameTree(session(state), mark, id)))
    return notify(state, { key: "mark.sameTree" });
  return { ...state, marks: [...marks, id] };
}

function selectionDeleted(state: State): Stepped<State, Effect> {
  const commands: Command[] = liveSelection(state).map((id) => ({
    "@type": "removeNode",
    id,
  }));
  if (commands.length === 0) return only(stop(state));
  return send(state, command({ "@type": "batch", commands }), {
    kind: "close",
  });
}

function moved(
  state: State,
  positions: Record<NodeId, Position>,
): Stepped<State, Effect> {
  const layout = session(state).layout;
  const commands: Command[] = [];
  for (const [key, to] of Object.entries(positions)) {
    const id = Number(key);
    const at = layout[id];
    if (at !== undefined && at.x === to.x && at.y === to.y) continue;
    commands.push({ "@type": "move", id, to });
  }
  const dragged = { ...state, drag: { ...state.drag, ...positions } };
  if (commands.length === 0)
    return only({
      ...dragged,
      drag: settledDrag(dragged.drag, session(state)),
    });
  return send(dragged, command({ "@type": "batch", commands }));
}

function chipDropped(
  state: State,
  key: string,
  at: Position,
): Stepped<State, Effect> {
  const chip = chipByKey(state.catalog, key);
  if (chip === undefined) return only(state);
  const latest = session(state);
  const id = mint(nodeIds(latest), state.lastNodeId);
  const named = aliasOf(chip.template);
  const node =
    named !== null && named.length > 0
      ? { ...chip.template, alias: freshAlias(latest, named) }
      : chip.template;
  return send(
    { ...state, lastNodeId: id },
    command({ "@type": "addNode", id, node, at }),
  );
}

function scanAdded(
  state: State,
  table: TableId,
  at: Position,
): Stepped<State, Effect> {
  const latest = session(state);
  const spec = latest.tables[table];
  if (spec === undefined) return only(state);
  const id = mint(nodeIds(latest), state.lastNodeId);
  return send(
    { ...state, lastNodeId: id },
    command({
      "@type": "addNode",
      id,
      node: { "@type": "table", table, alias: freshAlias(latest, spec.name) },
      at,
    }),
  );
}

function copied(state: State): State {
  const ids = new Set(liveSelection(state));
  if (ids.size === 0) return state;
  const latest = session(state);
  const entries: Clipboard["entries"] = [];
  for (const id of ids) {
    const node = latest.nodes[id];
    const at = latest.layout[id];
    if (node !== undefined && at !== undefined)
      entries.push({ originalId: id, node, at });
  }
  const edges = latest.edges.filter(
    (edge) => ids.has(edge.from) && ids.has(edge.to),
  );
  return { ...state, clipboard: { entries, edges } };
}

function pasted(state: State): Stepped<State, Effect> {
  const clip = state.clipboard;
  if (clip === null || clip.entries.length === 0) return only(state);
  const taken = nodeIds(session(state));
  const remap = new Map<NodeId, NodeId>();
  let last = state.lastNodeId;
  for (const entry of clip.entries) {
    last = mint(taken, last);
    remap.set(entry.originalId, last);
  }
  const commands: Command[] = [];
  for (const entry of clip.entries) {
    commands.push({
      "@type": "addNode",
      id: remap.get(entry.originalId) as NodeId,
      node: entry.node,
      at: { x: entry.at.x + PASTE_OFFSET, y: entry.at.y + PASTE_OFFSET },
    });
  }
  for (const edge of clip.edges) {
    const from = remap.get(edge.from);
    const to = remap.get(edge.to);
    if (from !== undefined && to !== undefined)
      commands.push({
        "@type": "connect",
        edge: { from, to, port: edge.port },
      });
  }
  return send(
    { ...state, lastNodeId: last },
    command({ "@type": "batch", commands }),
    { kind: "selectPasted", ids: [...remap.values()] },
  );
}

function toggled(selection: NodeId[], id: NodeId): NodeId[] {
  return selection.includes(id)
    ? selection.filter((selected) => selected !== id)
    : [...selection, id];
}

function applySelection<Id>(
  selection: Id[],
  changes: { id: Id; selected: boolean }[],
): Id[] {
  const next = new Set(selection);
  for (const change of changes) {
    if (change.selected) next.add(change.id);
    else next.delete(change.id);
  }
  return [...next];
}

function nodeIds(latest: Session): NodeId[] {
  return Object.keys(latest.nodes).map(Number);
}
