import { describe, expect, it } from "vitest";
import {
  appendChild,
  CONDITION_SHAPES,
  type ConditionShape,
  changeShape,
  conditionShape,
  removeChild,
  replaceChild,
} from "../../../src/form/conditions";
import type {
  ColumnReference,
  Condition,
  GroupCondition,
} from "../../../src/server/types";

const comparison: Condition = {
  kind: "comparison",
  left: { source: "u", name: "age" },
  operator: "GT",
  value: { int: 18 },
};

describe("changeShape", () => {
  it.each(CONDITION_SHAPES)(
    "a %s shape round-trips through conditionShape",
    (shape) => {
      expect(conditionShape(changeShape(shape, comparison))).toBe(shape);
    },
  );

  const carries: {
    name: string;
    shape: ConditionShape;
    column: ColumnReference;
  }[] = [
    {
      name: "comparison keeps the left column",
      shape: "comparison",
      column: { source: "u", name: "age" },
    },
    {
      name: "null check keeps the column",
      shape: "nullCheck",
      column: { source: "u", name: "age" },
    },
    {
      name: "not-null keeps the column",
      shape: "notNull",
      column: { source: "u", name: "age" },
    },
  ];
  it.each(carries)("$name", ({ shape, column }) => {
    const next = changeShape(shape, comparison);
    const held =
      next.kind === "comparison"
        ? next.left
        : next.kind === "nullCheck"
          ? next.column
          : { name: "" };
    expect(held).toEqual(column);
  });

  const groups: {
    name: string;
    shape: ConditionShape;
    previous: Condition;
    result: Condition;
  }[] = [
    {
      name: "wrapping a leaf in a group makes it the sole child",
      shape: "all",
      previous: comparison,
      result: { kind: "group", operator: "all", children: [comparison] },
    },
    {
      name: "regrouping a group keeps its children and swaps the operator",
      shape: "any",
      previous: { kind: "group", operator: "all", children: [comparison] },
      result: { kind: "group", operator: "any", children: [comparison] },
    },
  ];
  it.each(groups)("$name", ({ shape, previous, result }) => {
    expect(changeShape(shape, previous)).toEqual(result);
  });
});

describe("conditionShape", () => {
  const scenarios: {
    name: string;
    condition: Condition;
    shape: ConditionShape;
  }[] = [
    { name: "a comparison", condition: comparison, shape: "comparison" },
    {
      name: "a plain null check",
      condition: { kind: "nullCheck", column: { name: "a" }, negated: false },
      shape: "nullCheck",
    },
    {
      name: "a negated null check",
      condition: { kind: "nullCheck", column: { name: "a" }, negated: true },
      shape: "notNull",
    },
    {
      name: "an all-group",
      condition: { kind: "group", operator: "all", children: [] },
      shape: "all",
    },
    {
      name: "an any-group",
      condition: { kind: "group", operator: "any", children: [] },
      shape: "any",
    },
  ];
  it.each(scenarios)("$name", ({ condition, shape }) => {
    expect(conditionShape(condition)).toBe(shape);
  });
});

describe("group child editing", () => {
  const group: GroupCondition = {
    kind: "group",
    operator: "all",
    children: [
      { kind: "nullCheck", column: { name: "a" }, negated: false },
      { kind: "nullCheck", column: { name: "b" }, negated: false },
    ],
  };

  const scenarios: {
    name: string;
    edit: (g: GroupCondition) => GroupCondition;
    names: (string | undefined)[];
  }[] = [
    {
      name: "replaceChild swaps one entry",
      edit: (g) => replaceChild(g, 0, comparison),
      names: [undefined, "b"],
    },
    {
      name: "removeChild drops one entry",
      edit: (g) => removeChild(g, 0),
      names: ["b"],
    },
    {
      name: "appendChild adds a fresh comparison",
      edit: (g) => appendChild(g),
      names: ["a", "b", undefined],
    },
  ];
  it.each(scenarios)("$name", ({ edit, names }) => {
    const shown = edit(group).children.map((child) =>
      child.kind === "nullCheck" ? child.column.name : undefined,
    );
    expect(shown).toEqual(names);
  });

  it("does not mutate the source group", () => {
    replaceChild(group, 0, comparison);
    removeChild(group, 0);
    appendChild(group);
    expect(group.children).toHaveLength(2);
  });
});
