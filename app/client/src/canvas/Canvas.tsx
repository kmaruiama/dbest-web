import {
  Background,
  MarkerType,
  ReactFlow,
  SelectionMode,
  useReactFlow,
  type EdgeTypes,
  type NodeMouseHandler,
  type OnNodeDrag,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useEffect, useState } from "react";
import type { NodeId, Position, Session } from "../server/types";
import { useCatalog } from "../ui/connection";
import {
  CHIP_MIME,
  edgeEvents,
  nodeEvents,
  projectEdges,
  projectNodes,
  rounded,
  TABLE_MIME,
  type BoxNode,
  type CanvasEvent,
  type Look,
} from "./canvas";
import { FloatingEdge } from "./FloatingEdge";
import { NodeBox, NodeEvents } from "./NodeBox";

const NODE_TYPES = { box: NodeBox };
const EDGE_TYPES: EdgeTypes = { floating: FloatingEdge };
const DEFAULT_EDGE_OPTIONS = {
  type: "floating",
  markerEnd: { type: MarkerType.ArrowClosed },
};
const FIT_VIEW = { padding: 0.2 };

type Props = {
  session: Session;
  look: Look;
  onEvent: (event: CanvasEvent) => void;
};

export function Canvas({ session, look, onEvent }: Props) {
  const catalog = useCatalog();
  const flow = useReactFlow();
  const [pointer, setPointer] = useState<{
    from: NodeId;
    at: Position;
  } | null>(null);
  const nodes = projectNodes(catalog, session, look);
  const edges = projectEdges(session, look.edgeSelection);
  const linkingFrom = look.linkingFrom;
  const start = linkingFrom === null ? undefined : session.layout[linkingFrom];
  const cable =
    start === undefined || pointer === null || pointer.from !== linkingFrom
      ? null
      : { from: flow.flowToScreenPosition(start), to: pointer.at };
  const handleDrop = (event: React.DragEvent) => {
    event.preventDefault();
    const at = rounded(
      flow.screenToFlowPosition({ x: event.clientX, y: event.clientY }),
    );
    const key = event.dataTransfer.getData(CHIP_MIME);
    if (key.length > 0) return onEvent({ kind: "chipDropped", key, at });
    const table = event.dataTransfer.getData(TABLE_MIME);
    if (table.length > 0)
      onEvent({ kind: "tableDropped", table: Number(table), at });
  };
  const handleNodeContextMenu: NodeMouseHandler<BoxNode> = (event, node) => {
    event.preventDefault();
    onEvent({
      kind: "nodeRightClicked",
      id: Number(node.id),
      additive: event.ctrlKey || event.metaKey || event.shiftKey,
    });
  };
  const handleDragStop: OnNodeDrag<BoxNode> = (_event, _node, dragged) => {
    const positions: Record<NodeId, Position> = {};
    for (const node of dragged)
      positions[Number(node.id)] = rounded(node.position);
    onEvent({ kind: "dragEnded", positions });
  };
  useEffect(() => {
    if (linkingFrom === null) return;
    const handlePointerMove = (event: MouseEvent) =>
      setPointer({
        from: linkingFrom,
        at: { x: event.clientX, y: event.clientY },
      });
    window.addEventListener("mousemove", handlePointerMove);
    return () => window.removeEventListener("mousemove", handlePointerMove);
  }, [linkingFrom]);
  return (
    <NodeEvents.Provider value={onEvent}>
      <div
        className="flow"
        onDrop={handleDrop}
        onDragOver={(event) => event.preventDefault()}
      >
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={NODE_TYPES}
          edgeTypes={EDGE_TYPES}
          defaultEdgeOptions={DEFAULT_EDGE_OPTIONS}
          fitView
          fitViewOptions={FIT_VIEW}
          nodesConnectable={false}
          elementsSelectable
          selectionOnDrag
          selectionMode={SelectionMode.Partial}
          panOnDrag={[1]}
          deleteKeyCode={null}
          onNodesChange={(changes) => nodeEvents(changes).forEach(onEvent)}
          onEdgesChange={(changes) => edgeEvents(changes).forEach(onEvent)}
          onNodeClick={(_event, node) =>
            onEvent({ kind: "nodeClicked", id: Number(node.id) })
          }
          onNodeContextMenu={handleNodeContextMenu}
          onPaneContextMenu={(event) => {
            event.preventDefault();
            onEvent({ kind: "paneRightClicked" });
          }}
          onNodeDragStop={handleDragStop}
          onPaneClick={() => onEvent({ kind: "paneClicked" })}
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
    </NodeEvents.Provider>
  );
}
