import type {
  EdgeChange,
  Edge as FlowEdge,
  Node as FlowNode,
  NodeChange,
} from "@xyflow/react";
import { inputPorts, isEditable, symbolOf } from "../server/catalog";
import { linkTargets } from "../server/graph";
import type {
  Caption,
  Catalog,
  NodeId,
  Port,
  Position,
  Problem,
  Session,
  TableId,
} from "../server/types";

export const CHIP_MIME = "application/dbest-chip";

export const TABLE_MIME = "application/dbest-table";

export type Size = { width: number; height: number };

export type Tool = "edit" | "alias" | "link" | "mark" | "run" | "delete";

type Selected<Id> = { id: Id; selected: boolean };

export type CanvasEvent =
  | { kind: "nodeClicked"; id: NodeId }
  | { kind: "nodeRightClicked"; id: NodeId; additive: boolean }
  | { kind: "paneClicked" }
  | { kind: "paneRightClicked" }
  | { kind: "nodesSelected"; changes: Selected<NodeId>[] }
  | { kind: "edgesSelected"; changes: Selected<string>[] }
  | { kind: "nodesDragged"; positions: Record<NodeId, Position> }
  | { kind: "dragEnded"; positions: Record<NodeId, Position> }
  | { kind: "nodesMeasured"; sizes: Record<NodeId, Size> }
  | { kind: "chipDropped"; key: string; at: Position }
  | { kind: "tableDropped"; table: TableId; at: Position }
  | { kind: "toolClicked"; tool: Tool; id: NodeId };

export type PaletteEvent =
  | { kind: "paletteTableClicked"; table: TableId }
  | { kind: "paletteTableRemoved"; table: TableId }
  | { kind: "paletteMouseDown" };

export type Look = {
  captions: Record<NodeId, Caption>;
  problems: Problem[];
  selection: NodeId[];
  edgeSelection: string[];
  linkingFrom: NodeId | null;
  marks: NodeId[];
  drag: Record<NodeId, Position>;
  sizes: Record<NodeId, Size>;
  showEngineClass: boolean;
  showExpression: boolean;
  translate: (key: string) => string;
};

type BoxData = {
  symbol: string;
  engineClass: string | null;
  expression: string;
  ports: Port[];
  unknownVariant: boolean;
  problem: string | null;
  dimmed: boolean;
  editable: boolean;
  alias: string | null;
  mark: string | null;
  solo: boolean;
};

export type BoxNode = FlowNode<BoxData, "box">;

const NO_CAPTION: Caption = { engineClass: null, expression: "", alias: null };

export function projectNodes(
  catalog: Catalog,
  session: Session,
  look: Look,
): BoxNode[] {
  const targets =
    look.linkingFrom === null
      ? null
      : linkTargets(catalog, session, look.linkingFrom);
  const trouble = new Map<number, string>();
  for (const problem of look.problems)
    trouble.set(problem.node, problem.message);
  const selected = new Set(look.selection.filter((id) => id in session.nodes));
  const drawn: BoxNode[] = [];
  for (const [key, node] of Object.entries(session.nodes)) {
    const id = Number(key);
    const glyph = symbolOf(catalog, node);
    const caption = look.captions[id] ?? NO_CAPTION;
    drawn.push({
      id: String(id),
      type: "box",
      position: look.drag[id] ?? session.layout[id] ?? { x: 0, y: 0 },
      selected: selected.has(id),
      measured: look.sizes[id],
      data: {
        symbol: glyph.symbol,
        engineClass: look.showEngineClass ? caption.engineClass : null,
        expression: look.showExpression ? caption.expression : "",
        ports: inputPorts(catalog, node["@type"]),
        unknownVariant: !glyph.known,
        problem:
          trouble.get(id) ??
          (glyph.known ? null : look.translate("unknownVariant")),
        dimmed: targets !== null && id !== look.linkingFrom && !targets.has(id),
        editable: isEditable(catalog, node["@type"]),
        alias: caption.alias,
        mark: markLetter(look.marks, id),
        solo: selected.size === 1,
      },
    });
  }
  return drawn;
}

export function projectEdges(
  session: Session,
  edgeSelection: string[],
): FlowEdge[] {
  return session.edges.map((edge) => {
    const id = `${edge.from}-${edge.to}-${edge.port}`;
    return {
      id,
      source: String(edge.from),
      target: String(edge.to),
      sourceHandle: "out",
      targetHandle: "in",
      selected: edgeSelection.includes(id),
      data: { port: edge.port },
    };
  });
}

export function nodeEvents(changes: NodeChange<BoxNode>[]): CanvasEvent[] {
  const selections: Selected<NodeId>[] = [];
  const positions: Record<NodeId, Position> = {};
  const sizes: Record<NodeId, Size> = {};
  for (const change of changes) {
    if (change.type === "select")
      selections.push({ id: Number(change.id), selected: change.selected });
    if (change.type === "position" && change.position !== undefined)
      positions[Number(change.id)] = change.position;
    if (change.type === "dimensions" && change.dimensions !== undefined)
      sizes[Number(change.id)] = change.dimensions;
  }
  const events: CanvasEvent[] = [];
  if (selections.length > 0)
    events.push({ kind: "nodesSelected", changes: selections });
  if (Object.keys(positions).length > 0)
    events.push({ kind: "nodesDragged", positions });
  if (Object.keys(sizes).length > 0)
    events.push({ kind: "nodesMeasured", sizes });
  return events;
}

export function edgeEvents(changes: EdgeChange[]): CanvasEvent[] {
  const selections: Selected<string>[] = [];
  for (const change of changes) {
    if (change.type === "select")
      selections.push({ id: change.id, selected: change.selected });
  }
  return selections.length > 0
    ? [{ kind: "edgesSelected", changes: selections }]
    : [];
}

export function rounded(position: Position): Position {
  return { x: Math.round(position.x), y: Math.round(position.y) };
}

function markLetter(marks: NodeId[], id: NodeId): string | null {
  const index = marks.indexOf(id);
  return index < 0 ? null : String.fromCharCode(65 + index);
}
