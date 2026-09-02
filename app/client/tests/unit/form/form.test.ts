import { describe, expect, it } from "vitest";
import {
  arrayField,
  type FormDraft,
  type FormValue,
  objectField,
  withField,
} from "../../../src/form/FormDraft";
import {
  itemsAt,
  readInt,
  showText,
  withAppended,
  withCell,
  withItem,
  withoutItem,
} from "../../../src/form/values";
import type { PlanNode } from "../../../src/server/types";

describe("withField", () => {
  const base: FormDraft = { a: 1, b: "x" };
  const scenarios: {
    name: string;
    key: string;
    value: FormValue | undefined;
    draft: FormDraft;
  }[] = [
    {
      name: "adds a new key",
      key: "c",
      value: true,
      draft: { a: 1, b: "x", c: true },
    },
    {
      name: "overwrites an existing key",
      key: "a",
      value: 2,
      draft: { a: 2, b: "x" },
    },
    {
      name: "undefined drops the key",
      key: "a",
      value: undefined,
      draft: { b: "x" },
    },
    {
      name: "dropping an absent key changes nothing",
      key: "z",
      value: undefined,
      draft: { a: 1, b: "x" },
    },
  ];
  it.each(scenarios)("$name", ({ key, value, draft }) => {
    expect(withField(base, key, value)).toEqual(draft);
  });

  it("leaves the source draft untouched", () => {
    withField(base, "a", 99);
    expect(base).toEqual({ a: 1, b: "x" });
  });
});

describe("objectField", () => {
  const scenarios: {
    name: string;
    input: FormValue | undefined;
    object: object;
  }[] = [
    {
      name: "a plain object passes through",
      input: { k: 1 },
      object: { k: 1 },
    },
    { name: "an array is not an object", input: [1, 2], object: {} },
    { name: "null is not an object", input: null, object: {} },
    { name: "a scalar is not an object", input: "x", object: {} },
    { name: "undefined is not an object", input: undefined, object: {} },
  ];
  it.each(scenarios)("$name", ({ input, object }) => {
    expect(objectField(input)).toEqual(object);
  });
});

describe("arrayField", () => {
  const scenarios: {
    name: string;
    input: FormValue | undefined;
    array: FormValue[];
  }[] = [
    { name: "an array passes through", input: [1, 2], array: [1, 2] },
    { name: "a plain object is not an array", input: { k: 1 }, array: [] },
    { name: "null is not an array", input: null, array: [] },
    { name: "a scalar is not an array", input: "x", array: [] },
    { name: "undefined is not an array", input: undefined, array: [] },
  ];
  it.each(scenarios)("$name", ({ input, array }) => {
    expect(arrayField(input)).toEqual(array);
  });
});

describe("row editing", () => {
  const node: PlanNode = {
    kind: "sort",
    fields: {
      keys: [
        { column: "a", ascending: true },
        { column: "b", ascending: false },
      ],
    },
  };
  const scenarios: {
    name: string;
    edit: (n: PlanNode) => PlanNode;
    keys: FormValue[];
  }[] = [
    {
      name: "withAppended adds a row at the end",
      edit: (n) => withAppended(n, "keys", { column: "c", ascending: true }),
      keys: [
        { column: "a", ascending: true },
        { column: "b", ascending: false },
        { column: "c", ascending: true },
      ],
    },
    {
      name: "withoutItem drops the row at the index",
      edit: (n) => withoutItem(n, "keys", 0),
      keys: [{ column: "b", ascending: false }],
    },
    {
      name: "withItem replaces a whole row",
      edit: (n) => withItem(n, "keys", 1, { column: "z", ascending: true }),
      keys: [
        { column: "a", ascending: true },
        { column: "z", ascending: true },
      ],
    },
    {
      name: "withCell replaces one field of one row",
      edit: (n) => withCell(n, "keys", 0, "ascending", false),
      keys: [
        { column: "a", ascending: false },
        { column: "b", ascending: false },
      ],
    },
  ];
  it.each(scenarios)("$name", ({ edit, keys }) => {
    expect(itemsAt(edit(node), "keys")).toEqual(keys);
  });

  it("never mutates the source node", () => {
    withCell(node, "keys", 0, "ascending", false);
    expect(itemsAt(node, "keys")).toEqual([
      { column: "a", ascending: true },
      { column: "b", ascending: false },
    ]);
  });
});

describe("showText", () => {
  const scenarios: {
    name: string;
    input: FormValue | undefined;
    text: string;
  }[] = [
    { name: "a string is itself", input: "hi", text: "hi" },
    { name: "a number becomes text", input: 42, text: "42" },
    { name: "a boolean becomes text", input: false, text: "false" },
    { name: "null is blank", input: null, text: "" },
    { name: "undefined is blank", input: undefined, text: "" },
    { name: "an object is blank", input: { source: "", column: "" }, text: "" },
  ];
  it.each(scenarios)("$name", ({ input, text }) => {
    expect(showText(input)).toBe(text);
  });
});

describe("readInt", () => {
  const scenarios: {
    name: string;
    input: string;
    parsed: FormValue | undefined;
  }[] = [
    { name: "an integer parses", input: "10", parsed: 10 },
    { name: "a float parses", input: "3.5", parsed: 3.5 },
    { name: "blank is undefined", input: "", parsed: undefined },
    { name: "whitespace is undefined", input: "  ", parsed: undefined },
  ];
  it.each(scenarios)("$name", ({ input, parsed }) => {
    expect(readInt(input)).toBe(parsed);
  });
});
