import { describe, expect, it } from "vitest";
import {
  asNumber,
  command,
  condition,
  encodeCondition,
} from "../../../src/server/protocol";
import type { Command, Condition } from "../../../src/server/types";

describe("condition round-trips through the wire", () => {
  const scenarios: { name: string; value: Condition }[] = [
    {
      name: "a bare comparison",
      value: {
        kind: "comparison",
        left: { name: "age" },
        operator: "gt",
        value: { int: 18 },
      },
    },
    {
      name: "a qualified comparison against a string",
      value: {
        kind: "comparison",
        left: { source: "u", name: "name" },
        operator: "eq",
        value: { str: "ana" },
      },
    },
    {
      name: "a null check",
      value: { kind: "nullCheck", column: { name: "email" }, negated: false },
    },
    {
      name: "a negated null check on a qualified column",
      value: {
        kind: "nullCheck",
        column: { source: "u", name: "email" },
        negated: true,
      },
    },
    {
      name: "an all-group of two comparisons",
      value: {
        kind: "group",
        operator: "all",
        children: [
          {
            kind: "comparison",
            left: { name: "age" },
            operator: "gte",
            value: { int: 18 },
          },
          {
            kind: "comparison",
            left: { name: "age" },
            operator: "lt",
            value: { int: 65 },
          },
        ],
      },
    },
    {
      name: "a nested any-group",
      value: {
        kind: "group",
        operator: "any",
        children: [
          { kind: "nullCheck", column: { name: "left" }, negated: false },
          {
            kind: "group",
            operator: "all",
            children: [
              {
                kind: "comparison",
                left: { name: "x" },
                operator: "eq",
                value: { bool: true },
              },
            ],
          },
        ],
      },
    },
  ];
  it.each(scenarios)("$name", ({ value }) => {
    expect(condition(encodeCondition(value), "c")).toEqual(value);
  });
});

describe("condition rejects malformed input", () => {
  const scenarios: { name: string; input: unknown }[] = [
    { name: "an unknown tag", input: { "@type": "wat" } },
    { name: "a missing tag", input: { left: { name: "a" } } },
    {
      name: "a comparison with no operator",
      input: { "@type": "cmp", left: { name: "a" }, right: { int: 1 } },
    },
    {
      name: "a literal carrying two values",
      input: {
        "@type": "cmp",
        left: { name: "a" },
        op: "eq",
        right: { int: 1, str: "x" },
      },
    },
    {
      name: "a group whose children are not an array",
      input: { "@type": "and", conditions: "nope" },
    },
  ];
  it.each(scenarios)("$name", ({ input }) => {
    expect(() => condition(input, "c")).toThrow();
  });
});

describe("asNumber", () => {
  const accepted: { name: string; input: number }[] = [
    { name: "a positive number", input: 3.5 },
    { name: "zero", input: 0 },
    { name: "a negative number", input: -7 },
  ];
  it.each(accepted)("returns $name unchanged", ({ input }) => {
    expect(asNumber(input)).toBe(input);
  });

  const rejected: { name: string; input: unknown }[] = [
    { name: "NaN", input: Number.NaN },
    { name: "Infinity", input: Number.POSITIVE_INFINITY },
    { name: "a numeric string", input: "3" },
    { name: "null", input: null },
    { name: "a boolean", input: true },
  ];
  it.each(rejected)("throws on $name", ({ input }) => {
    expect(() => asNumber(input)).toThrow();
  });
});

describe("command encodes the domain shape onto the wire", () => {
  const scenarios: { name: string; input: Command; expected: object }[] = [
    {
      name: "addNode carries the templated node and position",
      input: {
        kind: "addNode",
        id: 5,
        node: { kind: "filter", fields: {} },
        at: { x: 1, y: 2 },
      },
      expected: {
        "@type": "addNode",
        id: 5,
        node: { "@type": "filter" },
        at: { x: 1, y: 2 },
      },
    },
    {
      name: "connect passes the edge straight through",
      input: {
        kind: "connect",
        edge: { from: 1, to: 2, port: "LEFT" },
      },
      expected: { "@type": "connect", edge: { from: 1, to: 2, port: "LEFT" } },
    },
    {
      name: "removeNode carries only the id",
      input: { kind: "removeNode", id: 9 },
      expected: { "@type": "removeNode", id: 9 },
    },
    {
      name: "batch recurses into each child command",
      input: {
        kind: "batch",
        commands: [
          { kind: "removeNode", id: 1 },
          { kind: "move", id: 2, to: { x: 3, y: 4 } },
        ],
      },
      expected: {
        "@type": "batch",
        commands: [
          { "@type": "removeNode", id: 1 },
          { "@type": "move", id: 2, to: { x: 3, y: 4 } },
        ],
      },
    },
  ];
  it.each(scenarios)("$name", ({ input, expected }) => {
    expect(command(input)).toEqual(expected);
  });
});
