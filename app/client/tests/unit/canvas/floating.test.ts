import { Position } from "@xyflow/react";
import { describe, expect, it } from "vitest";
import { border, edgeGeometry, type Rect } from "../../../src/canvas/floating";

const box = (x: number, y: number): Rect => ({ x, y, width: 100, height: 40 });

describe("border", () => {
  it("exits the top edge for a point straight above", () => {
    const { point, side } = border(box(0, 0), { x: 50, y: -1000 });
    expect(side).toBe(Position.Top);
    expect(point).toEqual({ x: 50, y: 0 });
  });

  it("exits the right edge for a point far to the right", () => {
    const { point, side } = border(box(0, 0), { x: 1000, y: 20 });
    expect(side).toBe(Position.Right);
    expect(point).toEqual({ x: 100, y: 20 });
  });
});

describe("edgeGeometry", () => {
  it("puts each end on the side facing the other node", () => {
    const g = edgeGeometry(box(0, 0), box(0, 300));
    expect(g.sourcePos).toBe(Position.Bottom);
    expect(g.targetPos).toBe(Position.Top);

    const side = edgeGeometry(box(0, 0), box(400, 0));
    expect(side.sourcePos).toBe(Position.Right);
    expect(side.targetPos).toBe(Position.Left);
  });

  it("returns finite coordinates for overlapping nodes", () => {
    const g = edgeGeometry(box(0, 0), box(0, 0));
    for (const n of [g.sx, g.sy, g.tx, g.ty])
      expect(Number.isFinite(n)).toBe(true);
  });
});
