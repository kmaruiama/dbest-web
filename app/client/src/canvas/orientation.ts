import { Position as HandleSide } from "@xyflow/react";
import { type CSSProperties } from "react";
import type { Position } from "../server/types";

export const AXES = ["TB", "BT", "LR", "RL"] as const;

export type Axis = (typeof AXES)[number];

const STORAGE_KEY = "dbest.axis";

export function isAxis(value: unknown): value is Axis {
  return (
    typeof value === "string" && (AXES as readonly string[]).includes(value)
  );
}

export function initialAxis(): Axis {
  const stored = localStorage.getItem(STORAGE_KEY);
  return isAxis(stored) ? stored : "TB";
}

export function rememberAxis(axis: Axis): void {
  localStorage.setItem(STORAGE_KEY, axis);
}

export const SIDES: Record<
  Axis,
  {
    in: HandleSide;
    out: HandleSide;
  }
> = {
  TB: { in: HandleSide.Top, out: HandleSide.Bottom },
  BT: { in: HandleSide.Bottom, out: HandleSide.Top },
  LR: { in: HandleSide.Left, out: HandleSide.Right },
  RL: { in: HandleSide.Right, out: HandleSide.Left },
};

export function flip(at: Position, axis: Axis): Position {
  switch (axis) {
    case "TB":
      return at;
    case "BT":
      return { x: at.x, y: -at.y };
    case "LR":
      return { x: at.y, y: at.x };
    case "RL":
      return { x: -at.y, y: -at.x };
  }
}

export function portOffset(
  axis: Axis,
  index: number,
  count: number,
): CSSProperties {
  const offset = `${((index + 1) * 100) / (count + 1)}%`;
  return axis === "TB" || axis === "BT" ? { left: offset } : { top: offset };
}
