import type { Edge as FlowEdge, Node as FlowNode } from "@xyflow/react";
import { inputPorts, isEditable, symbolOf } from "../server/catalog";
import type {
  Caption,
  NodeId,
  Position,
  Problem,
  Session,
} from "../server/types";
import { linkTargets } from "../workspace/graph";
import type { BoxData } from "./NodeBox";

const NO_CAPTION: Caption = { expression: "" };

export type BoxNode = FlowNode<BoxData, "box">;

function samePosition(left: Position | undefined, right: Position): boolean {
  return left !== undefined && left.x === right.x && left.y === right.y;
}

export type FlowView = {
  showEngineClass: boolean;
  showExpression: boolean;
  linkingFrom: NodeId | null;
  captions: Map<NodeId, Caption>;
  problems: Problem[];
  translate: (key: string) => string;
};

export type NodeActions = {
  onStartLink: (id: NodeId) => void;
  onEdit: (id: NodeId) => void;
  onRun: (id: NodeId) => void;
  onDelete: () => void;
};

export type Since = {
  layout: Map<NodeId, Position>;
};

export function projectNodes(
  session: Session,
  view: FlowView,
  actions: NodeActions,
  previous: Map<string, BoxNode>,
  since: Since,
): BoxNode[] {
  const targets =
    view.linkingFrom === null ? null : linkTargets(session, view.linkingFrom);
  const trouble = new Map<number, string>();
  for (const problem of view.problems) {
    trouble.set(problem.node, problem.message);
  }
  const drawn: BoxNode[] = [];
  for (const [id, node] of session.nodes) {
    const glyph = symbolOf(node);
    const caption = view.captions.get(id) ?? NO_CAPTION;
    const existing = previous.get(String(id));
    const at = session.layout.get(id) ?? { x: 0, y: 0 };
    const data: BoxData = {
      symbol: glyph.symbol,
      engineClass: view.showEngineClass ? (caption.engineClass ?? null) : null,
      expression: view.showExpression ? caption.expression : "",
      ports: inputPorts(node.kind),
      unknownVariant: !glyph.known,
      problem:
        trouble.get(id) ??
        (glyph.known ? null : view.translate("unknownVariant")),
      dimmed: targets !== null && id !== view.linkingFrom && !targets.has(id),
      editable: isEditable(node.kind) || node.kind === "table",
      onStartLink: () => actions.onStartLink(id),
      onEdit: () => actions.onEdit(id),
      onRun: () => actions.onRun(id),
      onDelete: () => actions.onDelete(),
    };
    drawn.push({
      ...existing,
      id: String(id),
      type: "box",
      position:
        existing === undefined || !samePosition(since.layout.get(id), at)
          ? at
          : existing.position,
      selected: existing?.selected ?? false,
      data,
    });
  }
  return drawn;
}

export function projectEdges(session: Session): FlowEdge[] {
  return session.edges.map((edge) => ({
    id: `${edge.from}-${edge.to}-${edge.port}`,
    source: String(edge.from),
    target: String(edge.to),
    sourceHandle: "out",
    targetHandle: "in",
    data: { port: edge.port },
  }));
}
