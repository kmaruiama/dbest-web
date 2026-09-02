import { beforeAll, describe, expect, it } from "vitest";
import {
  categories,
  chipByKey,
  chipOf,
  chipsIn,
  fieldsOf,
  inputPorts,
  installCatalog,
  isEditable,
  symbolOf,
  variantOptions,
} from "../../../src/server/catalog";
import type { FormValue } from "../../../src/form/FormDraft";
import type {
  Catalog,
  Chip,
  FieldSpec,
  PlanNode,
  Port,
} from "../../../src/server/types";

const joinChip = (algorithm: string, key: string): Chip => ({
  key,
  type: "join",
  symbol: key,
  category: "Joins",
  template: { kind: "join", fields: { type: "INNER", algorithm } },
});

const CATALOG: Catalog = {
  categories: ["Algebra", "Joins"],
  types: {
    filter: {
      arity: "unary",
      editable: true,
      fields: [{ at: "condition", widget: "condition" }],
    },
    join: { arity: "binary", editable: true, variants: ["type", "algorithm"] },
    distinct: { arity: "unary", editable: true, variants: ["hashed"] },
    scan: { arity: "unary", editable: false },
  },
  operators: [
    {
      key: "filter",
      type: "filter",
      symbol: "σ",
      category: "Algebra",
      template: { kind: "filter", fields: {} },
    },
    joinChip("NESTED_LOOP", "join"),
    joinChip("HASH", "hashJoin"),
    joinChip("MERGE", "mergeJoin"),
    {
      key: "distinct",
      type: "distinct",
      symbol: "Δ",
      category: "Algebra",
      template: { kind: "distinct", fields: { hashed: false } },
    },
    {
      key: "hashDistinct",
      type: "distinct",
      symbol: "#Δ",
      category: "Algebra",
      template: { kind: "distinct", fields: { hashed: true } },
    },
    {
      key: "scan",
      type: "scan",
      symbol: "→",
      category: "Algebra",
      template: { kind: "scan", fields: {} },
    },
  ],
};

const node = (kind: string, fields: PlanNode["fields"]): PlanNode => ({
  kind,
  fields,
});

beforeAll(() => installCatalog(CATALOG));

describe("categories", () => {
  it("returns the installed category list verbatim", () => {
    expect(categories()).toEqual(["Algebra", "Joins"]);
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
    expect(chipsIn(category).map((chip) => chip.key)).toEqual(keys);
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
    expect(chipByKey(key)?.symbol).toBe(symbol);
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
    expect(inputPorts(type)).toEqual(ports);
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
    expect(isEditable(type)).toBe(editable);
  });
});

describe("fieldsOf", () => {
  const scenarios: { name: string; type: string; specs: FieldSpec[] }[] = [
    {
      name: "returns the type's declared field specs",
      type: "filter",
      specs: [{ at: "condition", widget: "condition" }],
    },
    { name: "a known type without fields has none", type: "scan", specs: [] },
    { name: "an unknown type has none", type: "nonesuch", specs: [] },
  ];
  it.each(scenarios)("$name", ({ type, specs }) => {
    expect(fieldsOf(type)).toEqual(specs);
  });
});

describe("chipOf", () => {
  const scenarios: { name: string; node: PlanNode; key: string | undefined }[] =
    [
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
    expect(chipOf(node)?.key).toBe(key);
  });
});

describe("symbolOf", () => {
  const scenarios: {
    name: string;
    node: PlanNode;
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
    expect(symbolOf(node)).toEqual(result);
  });
});

describe("variantOptions", () => {
  const innerHash = node("join", { type: "INNER", algorithm: "HASH" });
  const scenarios: {
    name: string;
    node: PlanNode;
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
    expect(variantOptions(node, at)).toEqual(options);
  });
});
