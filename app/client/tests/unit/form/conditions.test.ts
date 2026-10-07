import { describe, expect, it } from "vitest";
import {
  CONDITION_SHAPES,
  changeShape,
  isCondition,
  operandOf,
  withOperand,
} from "../../../src/form/conditions";
import type { Condition, Literal } from "../../../src/server/types";

const comparison: Condition = {
  "@type": "cmp",
  left: "u.age",
  op: "GT",
  right: { int: 18 },
};

describe("changeShape", () => {
  it.each(CONDITION_SHAPES)("turns a comparison into %s", (shape) => {
    expect(changeShape(shape, comparison)["@type"]).toBe(shape);
  });

  it("keeps the column when switching between a comparison and null checks", () => {
    const isNull = changeShape("isNull", comparison);
    expect(isNull).toEqual({ "@type": "isNull", column: "u.age" });
    expect(changeShape("cmp", isNull)).toMatchObject({ left: "u.age" });
  });

  it("wraps a leaf in a group as its sole child", () => {
    expect(changeShape("and", comparison)).toEqual({
      "@type": "and",
      conditions: [comparison],
    });
  });

  it("regroups a group, keeping its conditions", () => {
    const group: Condition = { "@type": "and", conditions: [comparison] };
    expect(changeShape("or", group)).toEqual({
      "@type": "or",
      conditions: [comparison],
    });
  });
});

describe("isCondition", () => {
  it("recognises the condition tags and nothing else", () => {
    expect(isCondition(comparison)).toBe(true);
    expect(isCondition({ "@type": "filter" })).toBe(false);
    expect(isCondition("u.age")).toBe(false);
    expect(isCondition(null)).toBe(false);
  });
});

describe("withOperand", () => {
  const scenarios: {
    name: string;
    right: Literal;
    operand: "value" | "column";
    switched: Literal;
  }[] = [
    {
      name: "a value becomes a column named by its text",
      right: { str: " d.id " },
      operand: "column",
      switched: { ref: "d.id" },
    },
    {
      name: "a column becomes a value read from its name",
      right: { ref: "d.id" },
      operand: "value",
      switched: { str: "d.id" },
    },
    {
      name: "a number stays typed on the way back",
      right: { ref: "18" },
      operand: "value",
      switched: { int: 18 },
    },
    {
      name: "choosing the kind it already is changes nothing",
      right: { int: 18 },
      operand: "value",
      switched: { int: 18 },
    },
  ];
  it.each(scenarios)("$name", ({ right, operand, switched }) => {
    const next = withOperand(right, operand);
    expect(next).toEqual(switched);
    expect(operandOf(next)).toBe(operand);
  });
});
