import {
  BaseEdge,
  getBezierPath,
  Position,
  useInternalNode,
  type EdgeProps,
  type InternalNode,
} from "@xyflow/react";
import { useTranslation } from "../i18n";
import { edgeGeometry, type Rect } from "./floating";

function absRect(node: InternalNode): Rect {
  return {
    x: node.internals.positionAbsolute.x,
    y: node.internals.positionAbsolute.y,
    width: node.measured.width ?? 0,
    height: node.measured.height ?? 0,
  };
}

const LABEL_GAP: Record<Position, { x: number; y: number }> = {
  [Position.Top]: { x: 0, y: -9 },
  [Position.Bottom]: { x: 0, y: 9 },
  [Position.Left]: { x: -9, y: 0 },
  [Position.Right]: { x: 9, y: 0 },
};

export function FloatingEdge({
  id,
  source,
  target,
  data,
  markerEnd,
  style,
}: EdgeProps) {
  const translate = useTranslation();
  const sourceNode = useInternalNode(source);
  const targetNode = useInternalNode(target);
  if (!sourceNode?.measured.width || !targetNode?.measured.width) return null;

  const g = edgeGeometry(absRect(sourceNode), absRect(targetNode));
  const [path] = getBezierPath({
    sourceX: g.sx,
    sourceY: g.sy,
    sourcePosition: g.sourcePos,
    targetX: g.tx,
    targetY: g.ty,
    targetPosition: g.targetPos,
  });
  const port = (data as { port?: string } | undefined)?.port;
  const gap = LABEL_GAP[g.targetPos];
  return (
    <>
      <BaseEdge id={id} path={path} markerEnd={markerEnd} style={style} />
      {port === "LEFT" || port === "RIGHT" ? (
        <text className="port-mark" x={g.tx + gap.x} y={g.ty + gap.y}>
          {translate(`port.${port}`)}
        </text>
      ) : null}
    </>
  );
}
