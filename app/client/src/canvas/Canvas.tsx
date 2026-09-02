import {
  Background,
  ReactFlow,
  SelectionMode,
  useReactFlow,
  type Edge as FlowEdge,
  type Node as FlowNode,
  type NodeMouseHandler,
  type OnNodeDrag,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "../i18n";
import type {
  Caption,
  NodeId,
  Position,
  Problem,
  Session,
} from "../server/types";
import { useSettings } from "../shell/settings";
import { CHIP_MIME, TABLE_MIME } from "./dnd";
import { NodeBox } from "./NodeBox";
import { flip } from "./orientation";
import {
  projectEdges,
  projectNodes,
  type BoxNode,
  type NodeActions,
  type Since,
} from "./projection";

const NODE_TYPES = { box: NodeBox };
const EMPTY_NODES: FlowNode[] = [];
const EMPTY_EDGES: FlowEdge[] = [];

type Props = {
  session: Session;
  captions: Map<NodeId, Caption>;
  problems: Problem[];
  linkingFrom: NodeId | null;
  onNodeClick: (id: NodeId) => void;
  onNodeContextMenu: (id: NodeId, additive: boolean) => void;
  onPaneContextMenu: () => void;
  onSelectionChange: () => void;
  onStartLink: (id: NodeId) => void;
  onEdit: (id: NodeId) => void;
  onRun: (id: NodeId) => void;
  onDelete: () => void;
  onMove: (moves: Map<NodeId, Position>) => void;
  onDropChip: (key: string, at: Position) => void;
  onDropTable: (id: number, at: Position) => void;
  onBlankClick: () => void;
};

export function Canvas(props: Props) {
  const { session, captions, problems, linkingFrom } = props;
  const translate = useTranslation();
  const { axis, showEngineClass, showExpression } = useSettings();
  const [cursor, setCursor] = useState<Position | null>(null);
  const handlers = useRef(props);
  handlers.current = props;
  const since = useRef<Since>({ layout: new Map(), axis });
  const fitted = useRef(false);
  const shownAxis = useRef(axis);
  const flow = useReactFlow();
  const actions = useRef<NodeActions>({
    onStartLink: (id) => handlers.current.onStartLink(id),
    onEdit: (id) => handlers.current.onEdit(id),
    onRun: (id) => handlers.current.onRun(id),
    onDelete: () => handlers.current.onDelete(),
  }).current;
  const cable =
    linkingFrom === null || cursor === null
      ? null
      : (() => {
          const at = session.layout.get(linkingFrom);
          if (at === undefined) return null;
          return {
            from: flow.flowToScreenPosition(flip(at, axis)),
            to: cursor,
          };
        })();
  const handleDrop = (event: React.DragEvent) => {
    event.preventDefault();
    const raw = flow.screenToFlowPosition({
      x: event.clientX,
      y: event.clientY,
    });
    const at = flip({ x: Math.round(raw.x), y: Math.round(raw.y) }, axis);
    const key = event.dataTransfer.getData(CHIP_MIME);
    if (key.length > 0) return handlers.current.onDropChip(key, at);
    const table = event.dataTransfer.getData(TABLE_MIME);
    if (table.length > 0) handlers.current.onDropTable(Number(table), at);
  };
  const handleNodeContextMenu: NodeMouseHandler = (event, node) => {
    event.preventDefault();
    handlers.current.onNodeContextMenu(
      Number(node.id),
      event.ctrlKey || event.metaKey || event.shiftKey,
    );
  };
  const handleNodeDragStop: OnNodeDrag = (_event, _node, dragged) => {
    const moves = new Map<NodeId, Position>();
    for (const node of dragged) {
      moves.set(
        Number(node.id),
        flip(
          {
            x: Math.round(node.position.x),
            y: Math.round(node.position.y),
          },
          axis,
        ),
      );
    }
    handlers.current.onMove(moves);
  };
  useEffect(() => {
    const view = {
      axis,
      showEngineClass,
      showExpression,
      linkingFrom,
      captions,
      problems,
      translate,
    };
    flow.setNodes((current) => {
      const previous = new Map(
        current.flatMap((node) =>
          node.type === "box" ? [[node.id, node as BoxNode]] : [],
        ),
      );
      return projectNodes(session, view, actions, previous, since.current);
    });
    flow.setEdges(projectEdges(session));
    since.current = { layout: new Map(session.layout), axis };
  }, [
    session,
    captions,
    problems,
    translate,
    linkingFrom,
    axis,
    showEngineClass,
    showExpression,
    flow,
    actions,
  ]);
  useEffect(() => {
    if (linkingFrom === null) {
      setCursor(null);
      return;
    }
    const handlePointerMove = (event: MouseEvent) =>
      setCursor({ x: event.clientX, y: event.clientY });
    window.addEventListener("mousemove", handlePointerMove);
    return () => window.removeEventListener("mousemove", handlePointerMove);
  }, [linkingFrom]);
  useEffect(() => {
    if (fitted.current || session.nodes.size === 0) return;
    fitted.current = true;
    window.requestAnimationFrame(() => flow.fitView({ padding: 0.2 }));
  }, [session.nodes.size, flow]);
  useEffect(() => {
    if (shownAxis.current === axis) return;
    shownAxis.current = axis;
    window.requestAnimationFrame(() => flow.fitView({ padding: 0.2 }));
  }, [axis, flow]);
  return (
    <div
      className="flow"
      onDrop={handleDrop}
      onDragOver={(event) => event.preventDefault()}
    >
      <ReactFlow
        defaultNodes={EMPTY_NODES}
        defaultEdges={EMPTY_EDGES}
        nodeTypes={NODE_TYPES}
        nodesConnectable={false}
        elementsSelectable
        selectionOnDrag
        selectionMode={SelectionMode.Partial}
        panOnDrag={[1]}
        deleteKeyCode={null}
        onNodeClick={(_event, node) =>
          handlers.current.onNodeClick(Number(node.id))
        }
        onNodeContextMenu={handleNodeContextMenu}
        onPaneContextMenu={(event) => {
          event.preventDefault();
          handlers.current.onPaneContextMenu();
        }}
        onSelectionChange={() => handlers.current.onSelectionChange()}
        onNodeDragStop={handleNodeDragStop}
        onPaneClick={() => handlers.current.onBlankClick()}
        proOptions={{ hideAttribution: true }}
      >
        <Background gap={18} size={1} />
      </ReactFlow>
      {cable !== null && (
        <svg className="link-cable">
          <line
            x1={cable.from.x}
            y1={cable.from.y}
            x2={cable.to.x}
            y2={cable.to.y}
          />
        </svg>
      )}
    </div>
  );
}
