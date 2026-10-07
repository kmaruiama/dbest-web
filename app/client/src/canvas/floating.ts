import { Position } from "@xyflow/react";

export type Rect = { x: number; y: number; width: number; height: number };
type Pt = { x: number; y: number };

function centre(r: Rect): Pt {
  return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
}

export function border(r: Rect, toward: Pt): { point: Pt; side: Position } {
  const c = centre(r);
  const dx = toward.x - c.x;
  const dy = toward.y - c.y;
  const spanX = Math.abs(dx) / (r.width / 2 || 1);
  const spanY = Math.abs(dy) / (r.height / 2 || 1);
  const scale = 1 / Math.max(spanX, spanY, Number.EPSILON);
  const point = { x: c.x + dx * scale, y: c.y + dy * scale };
  const side =
    spanX > spanY
      ? dx > 0
        ? Position.Right
        : Position.Left
      : dy > 0
        ? Position.Bottom
        : Position.Top;
  return { point, side };
}

export function edgeGeometry(
  source: Rect,
  target: Rect,
): {
  sx: number;
  sy: number;
  tx: number;
  ty: number;
  sourcePos: Position;
  targetPos: Position;
} {
  const sc = centre(source);
  const tc = centre(target);
  if (Math.hypot(tc.x - sc.x, tc.y - sc.y) < 1) {
    return {
      sx: sc.x,
      sy: sc.y,
      tx: tc.x,
      ty: tc.y,
      sourcePos: Position.Bottom,
      targetPos: Position.Top,
    };
  }
  const t = border(target, sc);
  const s = border(source, t.point);
  return {
    sx: s.point.x,
    sy: s.point.y,
    tx: t.point.x,
    ty: t.point.y,
    sourcePos: s.side,
    targetPos: t.side,
  };
}
