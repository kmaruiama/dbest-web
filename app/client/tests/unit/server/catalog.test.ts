import { describe, expect, it } from "vitest";
import {
  categories,
  chipByKey,
  chipOf,
  chipsIn,
  fieldsOf,
  inputPorts,
  isEditable,
  symbolOf,
  variantOptions,
} from "../../../src/server/catalog";
import type {
  Catalog,
  Chip,
  FieldSpec,
  FormValue,
  Node,
  Port,
} from "../../../src/server/types";

const CONDITION_FIELD: FieldSpec = {
  at: "condition",
  widget: "condition",
  options: [],
  item: null,
  of: [],
  nullable: false,
};

const joinChip = (algorithm: string, key: string): Chip => ({
  key,
  type: "join",
  symbol: key,
  category: "Joins",
  template: { "@type": "join", type: "INNER", algorithm },
});

const CATALOG: Catalog = {
  categories: ["Algebra", "Joins"],
  types: {
    filter: {
      arity: "unary",
      editable: true,
      fields: [CONDITION_FIELD],
      variants: [],
    },
    join: {
      arity: "binary",
      editable: true,
      fields: [],
      variants: ["type", "algorithm"],
    },
    distinct: {
      arity: "unary",
      editable: true,
      fields: [],
      variants: ["hashed"],
    },
    scan: { arity: "unary", editable: false, fields: [], variants: [] },
  },
  operators: [
    {
      key: "filter",
      type: "filter",
      symbol: "σ",
      category: "Algebra",
      template: { "@type": "filter" },
    },
    joinChip("NESTED_LOOP", "join"),
    joinChip("HASH", "hashJoin"),
    joinChip("MERGE", "mergeJoin"),
    {
      key: "distinct",
      type: "distinct",
      symbol: "Δ",
      category: "Algebra",
      template: { "@type": "distinct", hashed: false },
    },
    {
      key: "hashDistinct",
      type: "distinct",
      symbol: "#Δ",
      category: "Algebra",
      template: { "@type": "distinct", hashed: true },
    },
    {
      key: "scan",
      type: "scan",
      symbol: "→",
      category: "Algebra",
      template: { "@type": "scan" },
    },
  ],
  placeholder: "?",
};

const node = (type: string, fields: Record<string, FormValue>): Node => ({
  "@type": type,
  ...fields,
});

describe("categories", () => {
  it("returns the installed category list verbatim", () => {
    expect(categories(CATALOG)).toEqual(["Algebra", "Joins"]);
  });
});

describe("chipsIn", () => {
  const scenarios: { name: string; category: string; keys: string[] }[] = [
    {
      name: "lists every chip in the category, in catalog order",
      category: "Joins",
      keys: ["join", "hashJoin", "mergeJoin"],
    },
    { name: "an unknown category has none", category: "Nope", keys: [] },
  ];
  it.each(scenarios)("$name", ({ category, keys }) => {
    expect(chipsIn(CATALOG, category).map((chip) => chip.key)).toEqual(keys);
  });
});

describe("chipByKey", () => {
  const scenarios: { name: string; key: string; symbol: string | undefined }[] =
    [
      {
        name: "finds the chip by its key",
        key: "hashJoin",
        symbol: "hashJoin",
      },
      { name: "an unknown key finds nothing", key: "nope", symbol: undefined },
    ];
  it.each(scenarios)("$name", ({ key, symbol }) => {
    expect(chipByKey(CATALOG, key)?.symbol).toBe(symbol);
  });
});

describe("inputPorts", () => {
  const scenarios: { name: string; type: string; ports: Port[] }[] = [
    { name: "a unary type has one inlet", type: "scan", ports: ["ONLY"] },
    { name: "a binary type has two", type: "join", ports: ["LEFT", "RIGHT"] },
    { name: "the table source has none", type: "table", ports: [] },
    {
      name: "an unknown type falls back to unary",
      type: "nonesuch",
      ports: ["ONLY"],
    },
  ];
  it.each(scenarios)("$name", ({ type, ports }) => {
    expect(inputPorts(CATALOG, type)).toEqual(ports);
  });
});

describe("isEditable", () => {
  const scenarios: { name: string; type: string; editable: boolean }[] = [
    {
      name: "a type that declares fields is editable",
      type: "filter",
      editable: true,
    },
    {
      name: "a known type without fields is not",
      type: "scan",
      editable: false,
    },
    { name: "an unknown type is not", type: "nonesuch", editable: false },
  ];
  it.each(scenarios)("$name", ({ type, editable }) => {
    expect(isEditable(CATALOG, type)).toBe(editable);
  });
});

describe("fieldsOf", () => {
  const scenarios: { name: string; type: string; specs: FieldSpec[] }[] = [
    {
      name: "returns the type's declared field specs",
      type: "filter",
      specs: [CONDITION_FIELD],
    },
    { name: "a known type without fields has none", type: "scan", specs: [] },
    { name: "an unknown type has none", type: "nonesuch", specs: [] },
  ];
  it.each(scenarios)("$name", ({ type, specs }) => {
    expect(fieldsOf(CATALOG, type)).toEqual(specs);
  });
});

describe("chipOf", () => {
  const scenarios: { name: string; node: Node; key: string | undefined }[] = [
    {
      name: "a type with no variants resolves to its only chip",
      node: node("filter", {}),
      key: "filter",
    },
    {
      name: "a variant type matches on every variant field",
      node: node("join", { type: "INNER", algorithm: "HASH" }),
      key: "hashJoin",
    },
    {
      name: "an impossible variant combination resolves to nothing",
      node: node("join", { type: "INNER", algorithm: "BOGUS" }),
      key: undefined,
    },
    {
      name: "a type the catalog does not carry resolves to nothing",
      node: node("nonesuch", {}),
      key: undefined,
    },
  ];
  it.each(scenarios)("$name", ({ node, key }) => {
    expect(chipOf(CATALOG, node)?.key).toBe(key);
  });
});

describe("symbolOf", () => {
  const scenarios: {
    name: string;
    node: Node;
    result: { symbol: string; known: boolean };
  }[] = [
    {
      name: "the table source has its own glyph and needs no chip",
      node: node("table", {}),
      result: { symbol: "▤", known: true },
    },
    {
      name: "a resolvable node takes its chip's symbol",
      node: node("filter", {}),
      result: { symbol: "σ", known: true },
    },
    {
      name: "an unresolvable node is marked unknown",
      node: node("join", { type: "INNER", algorithm: "BOGUS" }),
      result: { symbol: "?", known: false },
    },
  ];
  it.each(scenarios)("$name", ({ node, result }) => {
    expect(symbolOf(CATALOG, node)).toEqual(result);
  });
});

describe("variantOptions", () => {
  const innerHash = node("join", { type: "INNER", algorithm: "HASH" });
  const scenarios: {
    name: string;
    node: Node;
    at: string;
    options: FormValue[] | null;
  }[] = [
    {
      name: "lists a variant field's legal values, other variants held fixed",
      node: innerHash,
      at: "algorithm",
      options: ["NESTED_LOOP", "HASH", "MERGE"],
    },
    {
      name: "covers a single-variant type",
      node: node("distinct", { hashed: false }),
      at: "hashed",
      options: [false, true],
    },
    {
      name: "a field that is not a variant yields null",
      node: innerHash,
      at: "condition",
      options: null,
    },
    {
      name: "a type with no variants yields null",
      node: node("filter", {}),
      at: "anything",
      options: null,
    },
  ];
  it.each(scenarios)("$name", ({ node, at, options }) => {
    expect(variantOptions(CATALOG, node, at)).toEqual(options);
  });
});
