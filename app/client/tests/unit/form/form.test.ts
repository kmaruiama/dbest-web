import { describe, expect, it } from "vitest";
import {
  itemsAt,
  readInt,
  showText,
  withAppended,
  withCell,
  withField,
  withItem,
  withoutItem,
  withoutPlaceholders,
} from "../../../src/form/values";
import type { FieldSpec, FormValue, Node } from "../../../src/server/types";

describe("withoutPlaceholders", () => {
  const field = (at: string): FieldSpec => ({
    at,
    widget: "text",
    options: [],
    item: null,
    of: [],
    nullable: false,
  });
  it("empties every field a template left at the placeholder, at any depth", () => {
    const join: Node = {
      "@type": "join",
      type: "INNER",
      on: [{ left: "?.?", right: "?.?" }],
    };
    expect(withoutPlaceholders(join, [field("on")], "?")).toEqual({
      "@type": "join",
      type: "INNER",
      on: [{ left: "", right: "" }],
    });
    const filter: Node = {
      "@type": "filter",
      condition: { "@type": "cmp", left: "?", op: "EQ", right: { str: "?" } },
    };
    expect(withoutPlaceholders(filter, [field("condition")], "?")).toEqual({
      "@type": "filter",
      condition: { "@type": "cmp", left: "", op: "EQ", right: { str: "" } },
    });
  });
  it("keeps values the user typed, even ones that contain the placeholder", () => {
    const project: Node = { "@type": "project", columns: ["e.id", "e.?x"] };
    expect(withoutPlaceholders(project, [field("columns")], "?")).toEqual(
      project,
    );
    const limit: Node = { "@type": "limit", count: 10, offset: 0 };
    expect(
      withoutPlaceholders(limit, [field("count"), field("offset")], "?"),
    ).toEqual(limit);
  });
  it("leaves alone the fields the form does not show, such as an alias", () => {
    const agg: Node = { "@type": "agg", alias: "?", by: "?" };
    expect(withoutPlaceholders(agg, [field("by")], "?")).toEqual({
      "@type": "agg",
      alias: "?",
      by: "",
    });
  });
});

describe("withField", () => {
  const base: Node = { "@type": "t", a: 1, b: "x" };
  const scenarios: {
    name: string;
    key: string;
    value: FormValue | undefined;
    draft: Node;
  }[] = [
    {
      name: "adds a new key",
      key: "c",
      value: true,
      draft: { "@type": "t", a: 1, b: "x", c: true },
    },
    {
      name: "overwrites an existing key",
      key: "a",
      value: 2,
      draft: { "@type": "t", a: 2, b: "x" },
    },
    {
      name: "undefined drops the key",
      key: "a",
      value: undefined,
      draft: { "@type": "t", b: "x" },
    },
    {
      name: "dropping an absent key changes nothing",
      key: "z",
      value: undefined,
      draft: { "@type": "t", a: 1, b: "x" },
    },
  ];
  it.each(scenarios)("$name", ({ key, value, draft }) => {
    expect(withField(base, key, value)).toEqual(draft);
  });

  it("leaves the source draft untouched", () => {
    withField(base, "a", 99);
    expect(base).toEqual({ "@type": "t", a: 1, b: "x" });
  });
});

describe("row editing", () => {
  const node: Node = {
    "@type": "sort",
    keys: [
      { column: "a", ascending: true },
      { column: "b", ascending: false },
    ],
  };
  const scenarios: {
    name: string;
    edit: (n: Node) => Node;
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
    { name: "an object is blank", input: { column: "" }, text: "" },
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
