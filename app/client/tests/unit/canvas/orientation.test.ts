import { describe, expect, it } from "vitest";
import {
  AXES,
  type Axis,
  flip,
  isAxis,
  portOffset,
} from "../../../src/canvas/orientation";
import type { Position } from "../../../src/server/types";

const point: Position = { x: 10, y: 40 };

describe("flip", () => {
  it.each(AXES)("%s is its own inverse", (axis) => {
    expect(flip(flip(point, axis), axis)).toEqual(point);
  });

  const parent: Position = { x: 0, y: 0 };
  const child: Position = { x: 0, y: 100 };

  const directions: {
    axis: Axis;
    along: (p: Position) => number;
    parentComesFirst: boolean;
  }[] = [
    { axis: "TB", along: (p) => p.y, parentComesFirst: true },
    { axis: "BT", along: (p) => p.y, parentComesFirst: false },
    { axis: "LR", along: (p) => p.x, parentComesFirst: true },
    { axis: "RL", along: (p) => p.x, parentComesFirst: false },
  ];

  it.each(directions)(
    "$axis draws the parent on the leading side",
    ({ axis, along, parentComesFirst }) => {
      const drawnParent = along(flip(parent, axis));
      const drawnChild = along(flip(child, axis));
      expect(drawnParent < drawnChild).toBe(parentComesFirst);
    },
  );
});

describe("isAxis", () => {
  const scenarios: { name: string; input: string; valid: boolean }[] = [
    { name: "a known axis", input: "TB", valid: true },
    { name: "another known axis", input: "RL", valid: true },
    { name: "the wrong case", input: "tb", valid: false },
    { name: "an unrelated word", input: "diagonal", valid: false },
    { name: "the empty string", input: "", valid: false },
  ];

  it.each(scenarios)("$name", ({ input, valid }) => {
    expect(isAxis(input)).toBe(valid);
  });
});

describe("portOffset", () => {
  const scenarios: {
    name: string;
    axis: Axis;
    index: number;
    count: number;
    style: Record<string, string>;
  }[] = [
    {
      name: "a single vertical port sits at the middle",
      axis: "TB",
      index: 0,
      count: 1,
      style: { left: "50%" },
    },
    {
      name: "vertical ports spread evenly",
      axis: "BT",
      index: 0,
      count: 3,
      style: { left: "25%" },
    },
    {
      name: "horizontal ports offset from the top",
      axis: "LR",
      index: 1,
      count: 3,
      style: { top: "50%" },
    },
    {
      name: "the last horizontal port sits near the end",
      axis: "RL",
      index: 2,
      count: 3,
      style: { top: "75%" },
    },
  ];

  it.each(scenarios)("$name", ({ axis, index, count, style }) => {
    expect(portOffset(axis, index, count)).toEqual(style);
  });
});
