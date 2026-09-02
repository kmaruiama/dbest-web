import { describe, expect, it } from "vitest";
import {
  INITIAL,
  type InteractionAction,
  type InteractionState,
  reduce,
} from "../../../src/workspace/interaction";
import type { FeedingColumns } from "../../../src/workspace/columns";
import type { PlanNode } from "../../../src/server/types";

const node: PlanNode = { kind: "filter", fields: {} };
const FEEDING: FeedingColumns = {
  all: [],
  byPort: { ONLY: [], LEFT: [], RIGHT: [] },
};
const noticed: InteractionState = {
  interaction: { kind: "idle" },
  notice: "hi",
};
const linking: InteractionState = {
  interaction: { kind: "linking", from: 1 },
  notice: null,
};

describe("reduce", () => {
  const scenarios: {
    name: string;
    from: InteractionState;
    action: InteractionAction;
    to: InteractionState;
  }[] = [
    {
      name: "startLink enters linking and keeps the notice",
      from: noticed,
      action: { kind: "startLink", from: 3 },
      to: { interaction: { kind: "linking", from: 3 }, notice: "hi" },
    },
    {
      name: "choose enters the port picker",
      from: INITIAL,
      action: { kind: "choose", options: [{ from: 1, to: 2, port: "LEFT" }] },
      to: {
        interaction: {
          kind: "choosing",
          options: [{ from: 1, to: 2, port: "LEFT" }],
        },
        notice: null,
      },
    },
    {
      name: "edit carries the node and feeding columns",
      from: linking,
      action: { kind: "edit", id: 7, node, columns: FEEDING },
      to: {
        interaction: { kind: "editing", id: 7, node, columns: FEEDING },
        notice: null,
      },
    },
    {
      name: "notify drops any interaction and shows the message",
      from: linking,
      action: { kind: "notify", message: "blocked" },
      to: { interaction: { kind: "idle" }, notice: "blocked" },
    },
    {
      name: "reset returns to the initial state",
      from: linking,
      action: { kind: "reset" },
      to: INITIAL,
    },
    {
      name: "clearNotice keeps the interaction and clears the message",
      from: { interaction: { kind: "linking", from: 1 }, notice: "hi" },
      action: { kind: "clearNotice" },
      to: { interaction: { kind: "linking", from: 1 }, notice: null },
    },
  ];

  it.each(scenarios)("$name", ({ from, action, to }) => {
    expect(reduce(from, action)).toEqual(to);
  });

  const noops: {
    name: string;
    from: InteractionState;
    action: InteractionAction;
  }[] = [
    {
      name: "reset on a clean state",
      from: INITIAL,
      action: { kind: "reset" },
    },
    {
      name: "clearNotice with no notice",
      from: INITIAL,
      action: { kind: "clearNotice" },
    },
  ];

  it.each(noops)("$name returns the same reference", ({ from, action }) => {
    expect(reduce(from, action)).toBe(from);
  });
});
