import { describe, expect, it } from "vitest";
import { offeredColumns } from "../../../src/form/offer";
import type { Columns, Port, Widget } from "../../../src/server/types";

const joined: Columns = {
  inputs: { LEFT: ["e.id", "e.dept"], RIGHT: ["d.id"] },
  outer: ["m.id"],
};

const filtered: Columns = {
  inputs: { ONLY: ["d.id", "d.city"] },
  outer: ["e.dept", "d.id"],
};

const alone: Columns = { inputs: {}, outer: ["e.dept"] };

describe("offeredColumns", () => {
  const scenarios: {
    name: string;
    columns: Columns;
    widget: Widget;
    hasInputs: boolean;
    port?: Port;
    offered: string[];
  }[] = [
    {
      name: "a column field offers the inputs only",
      columns: filtered,
      widget: "column",
      hasInputs: true,
      offered: ["d.id", "d.city"],
    },
    {
      name: "a condition offers the outer columns after the inputs, once each",
      columns: filtered,
      widget: "condition",
      hasInputs: true,
      offered: ["d.id", "d.city", "e.dept"],
    },
    {
      name: "a cell named after a side offers that side only",
      columns: joined,
      widget: "column",
      hasInputs: true,
      port: "RIGHT",
      offered: ["d.id"],
    },
    {
      name: "a binary node's other fields offer both sides",
      columns: joined,
      widget: "list",
      hasInputs: true,
      offered: ["e.id", "e.dept", "d.id"],
    },
    {
      name: "an operator without inputs offers the outer columns everywhere",
      columns: alone,
      widget: "list",
      hasInputs: false,
      offered: ["e.dept"],
    },
  ];
  it.each(scenarios)(
    "$name",
    ({ columns, widget, hasInputs, port, offered }) => {
      expect(offeredColumns(columns, widget, hasInputs, port)).toEqual(offered);
    },
  );
});
